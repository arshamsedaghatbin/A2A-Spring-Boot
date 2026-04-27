package memoryagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import memoryagent.entity.SessionSummary;
import memoryagent.entity.UserMemory;
import memoryagent.repository.SessionSummaryRepository;
import memoryagent.repository.UserMemoryRepository;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Core service for all memory operations.
 * Tool methods in Agent.java delegate to this service via a static holder.
 *
 * Graceful degradation: every method wraps DB/API calls in try-catch and
 * returns a safe fallback so agent operation continues even if PG is down.
 */
@Service
public class BankMemoryService {

    private static final Logger log = LoggerFactory.getLogger(BankMemoryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EMBEDDING_MODEL = "text-embedding-004";

    private final UserMemoryRepository    userMemoryRepo;
    private final SessionSummaryRepository sessionSummaryRepo;
    private final JdbcTemplate             jdbc;
    private final HttpClient               http;
    private final String                   geminiApiKey;

    public BankMemoryService(
            UserMemoryRepository userMemoryRepo,
            SessionSummaryRepository sessionSummaryRepo,
            JdbcTemplate jdbc,
            @Value("${GOOGLE_API_KEY:}") String apiKeyProp) {

        this.userMemoryRepo     = userMemoryRepo;
        this.sessionSummaryRepo = sessionSummaryRepo;
        this.jdbc               = jdbc;
        this.http               = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        // Resolve API key from Spring property or system env
        String key = (apiKeyProp != null && !apiKeyProp.isBlank())
                ? apiKeyProp : System.getenv("GOOGLE_API_KEY");
        this.geminiApiKey = (key != null) ? key : "";

        log.info("[Memory] MemoryService ready. geminiApiKey={}",
                geminiApiKey.isBlank() ? "NOT SET (embeddings disabled)" : "configured");
    }

    // ── Tool 1: loadMemory ────────────────────────────────────────────────────

    /**
     * Loads user profile and recent conversation patterns.
     * Returns a summary string and populates the provided state map.
     */
    public Map<String, Object> loadMemory(String userId, Map<String, Object> state) {
        try {
            Optional<UserMemory> opt = userMemoryRepo.findById(userId);
            if (opt.isEmpty()) {
                log.info("[Memory] No memory found for userId={}", userId);
                return Map.of("status", "no-memory", "message", "No prior memory for " + userId);
            }

            UserMemory mem = opt.get();

            // Populate session state so the orchestrator can suggest defaults
            if (mem.getDefaultFromAccount() != null)
                state.put("bank_fromAccount", mem.getDefaultFromAccount());
            if (mem.getLastTransferTo() != null)
                state.put("bank_toAccount", mem.getLastTransferTo());
            if (mem.getPreferredLanguage() != null)
                state.put("bank_language", mem.getPreferredLanguage());

            String summary = String.format(
                "User %s: usually transfers from %s to %s. Last TXN: %s (%d total transactions).",
                userId,
                nvl(mem.getDefaultFromAccount(), "unknown"),
                nvl(mem.getLastTransferTo(),      "unknown"),
                nvl(mem.getLastTransactionId(),   "none"),
                mem.getTotalTransactions() != null ? mem.getTotalTransactions() : 0
            );

            log.info("[Memory] Loaded memory for userId={}: {}", userId, summary);
            return Map.of("status", "ok", "message", summary,
                    "fromAccount", nvl(mem.getDefaultFromAccount(), ""),
                    "toAccount",   nvl(mem.getLastTransferTo(),     ""),
                    "language",    nvl(mem.getPreferredLanguage(),  "en"),
                    "lastTxn",     nvl(mem.getLastTransactionId(),  ""));

        } catch (Exception e) {
            log.warn("[Memory] loadMemory failed for userId={}: {}", userId, e.getMessage());
            return Map.of("status", "error", "message", "Memory unavailable: " + e.getMessage());
        }
    }

    // ── Tool 2: saveMemory ────────────────────────────────────────────────────

    /**
     * Persists user profile (upsert), conversation embedding, and session summary.
     * All sub-operations are independent — partial failures are logged but don't abort.
     */
    public Map<String, Object> saveMemory(String userId, String fromAccount, String toAccount,
                                           String transactionId, String sessionId) {
        List<String> saved = new ArrayList<>();

        // 1. Upsert user_memory
        try {
            userMemoryRepo.upsert(userId, fromAccount, toAccount, transactionId);
            saved.add("user_memory");
            log.info("[Memory] Upserted user_memory userId={} txn={}", userId, transactionId);
        } catch (Exception e) {
            log.warn("[Memory] user_memory upsert failed: {}", e.getMessage());
        }

        // 2. Save conversation_memory with embedding — ASYNC so tool returns immediately.
        // generateEmbedding() calls Gemini API (~5-15s); we never block the agent on it.
        final String content = String.format(
            "User %s transferred from %s to %s TXN:%s",
            userId, fromAccount, toAccount, transactionId);
        CompletableFuture.runAsync(() -> {
            try {
                float[] embedding = generateEmbedding(content);
                saveConversationMemory(userId, content, embedding, "transfer");
                log.info("[Memory] conversation_memory saved async userId={}", userId);
            } catch (Exception e) {
                log.warn("[Memory] conversation_memory async save failed: {}", e.getMessage());
            }
        });
        saved.add("conversation_memory");

        // 3. Save session_summary
        try {
            SessionSummary ss = new SessionSummary();
            ss.setSessionId(sessionId);
            ss.setUserId(userId);
            ss.setTransactionId(transactionId);
            ss.setFromAccount(fromAccount);
            ss.setToAccount(toAccount);
            ss.setCreatedAt(LocalDateTime.now());
            sessionSummaryRepo.save(ss);
            saved.add("session_summary");
        } catch (Exception e) {
            log.warn("[Memory] session_summary save failed: {}", e.getMessage());
        }

        String msg = saved.isEmpty()
                ? "Memory save failed"
                : "Saved: " + String.join(", ", saved);
        log.info("[Memory] saveMemory userId={} → {}", userId, msg);
        return Map.of("status", saved.isEmpty() ? "error" : "ok", "message", msg);
    }

    // ── Tool 3: searchMemory ─────────────────────────────────────────────────

    /**
     * Semantic similarity search over conversation_memory for the given userId.
     * Uses Gemini text-embedding-004 to embed the query, then pgvector cosine distance.
     */
    public Map<String, Object> searchMemory(String userId, String query) {
        try {
            float[] queryEmbedding = generateEmbedding(query);
            List<String> results   = semanticSearch(userId, queryEmbedding, 5);

            if (results.isEmpty()) {
                return Map.of("status", "ok", "results", List.of(),
                        "message", "No relevant memories found for: " + query);
            }

            String context = String.join("\n", results);
            log.info("[Memory] searchMemory userId={} query='{}' found {} results",
                    userId, query, results.size());
            return Map.of("status", "ok", "results", results, "context", context,
                    "message", "Found " + results.size() + " relevant memories.");

        } catch (Exception e) {
            log.warn("[Memory] searchMemory failed userId={}: {}", userId, e.getMessage());
            return Map.of("status", "error", "results", List.of(),
                    "message", "Search unavailable: " + e.getMessage());
        }
    }

    // ── Internal: Gemini embedding ────────────────────────────────────────────

    /**
     * Calls Gemini text-embedding-004 API (768 dimensions).
     * Returns zero vector if API key is absent or call fails.
     */
    float[] generateEmbedding(String text) {
        if (geminiApiKey.isBlank()) {
            log.debug("[Memory] No API key — returning zero embedding");
            return new float[768];
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + EMBEDDING_MODEL + ":embedContent?key=" + geminiApiKey;

            ObjectNode part = MAPPER.createObjectNode(); part.put("text", text);
            ArrayNode parts = MAPPER.createArrayNode();  parts.add(part);
            ObjectNode content = MAPPER.createObjectNode(); content.set("parts", parts);
            ObjectNode payload = MAPPER.createObjectNode(); payload.set("content", content);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                log.warn("[Memory] Gemini embedding HTTP {}", resp.statusCode());
                return new float[768];
            }

            JsonNode root   = MAPPER.readTree(resp.body());
            JsonNode values = root.path("embedding").path("values");
            float[]  result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) result[i] = (float) values.get(i).asDouble();

            log.debug("[Memory] Generated embedding: {} dims", result.length);
            return result;

        } catch (Exception e) {
            log.warn("[Memory] Embedding generation failed: {}", e.getMessage());
            return new float[768];
        }
    }

    // ── Internal: pgvector JDBC ───────────────────────────────────────────────

    private void saveConversationMemory(String userId, String content,
                                         float[] embedding, String memoryType) throws Exception {
        PGobject vec = toPGvector(embedding);
        jdbc.update(
            "INSERT INTO conversation_memory (user_id, content, embedding, memory_type, created_at) " +
            "VALUES (?, ?, ?, ?, NOW())",
            userId, content, vec, memoryType);
    }

    private List<String> semanticSearch(String userId, float[] queryEmbedding, int limit) {
        try {
            PGobject vec = toPGvector(queryEmbedding);
            return jdbc.query(
                "SELECT content FROM conversation_memory " +
                "WHERE user_id = ? " +
                "ORDER BY embedding <=> ? LIMIT ?",
                (rs, rowNum) -> rs.getString("content"),
                userId, vec, limit);
        } catch (Exception e) {
            log.warn("[Memory] semanticSearch failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static PGobject toPGvector(float[] values) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        sb.append("]");
        PGobject obj = new PGobject();
        obj.setType("vector");
        obj.setValue(sb.toString());
        return obj;
    }

    private static String nvl(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
