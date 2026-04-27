package orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fetches agent instruction prompts from Langfuse on startup and refreshes every 5 minutes.
 * Falls back to hardcoded defaults if Langfuse is unavailable.
 *
 * Usage in Agent.java:
 *   .instruction(promptService.getPrompt("bank-orchestrator"))
 */
@Component
public class LangfusePromptService {

    private static final Logger log = LoggerFactory.getLogger(LangfusePromptService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String     baseUrl;
    private final String     authHeader;

    /** Live cache — populated from Langfuse, falls back to hardcoded. */
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /** Hardcoded fallbacks — used when Langfuse is unreachable. */
    private final Map<String, String> fallbacks;

    private final ScheduledExecutorService scheduler;

    public LangfusePromptService(
            @Value("${langfuse.base-url:http://localhost:3000}") String baseUrl,
            @Value("${langfuse.public-key}") String publicKey,
            @Value("${langfuse.secret-key}") String secretKey) {

        this.baseUrl    = baseUrl;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes());

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.fallbacks = buildFallbacks();

        // Daemon scheduler — never blocks shutdown
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "prompt-refresh");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void initialize() {
        // Pre-fill cache with fallbacks so getPrompt() never returns empty
        cache.putAll(fallbacks);
        // Fetch from Langfuse (overwrites fallbacks if available)
        fetchAll();
        // Refresh every 5 minutes
        scheduler.scheduleAtFixedRate(this::fetchAll, 5, 5, TimeUnit.MINUTES);
    }

    /** Returns the latest prompt for the given agent name. Never throws. */
    public String getPrompt(String name) {
        return cache.getOrDefault(name, fallbacks.getOrDefault(name, ""));
    }

    // ── Langfuse fetch ────────────────────────────────────────────────────────

    private void fetchAll() {
        for (String name : fallbacks.keySet()) {
            fetchOne(name);
        }
    }

    private void fetchOne(String name) {
        try {
            String url = baseUrl + "/api/public/prompts/" + name;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", authHeader)
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 404) {
                // Prompt not uploaded yet — keep fallback, upload it
                uploadFallback(name);
                return;
            }
            if (resp.statusCode() != 200) {
                log.debug("[Prompts] GET '{}' returned HTTP {}", name, resp.statusCode());
                return;
            }

            JsonNode root = MAPPER.readTree(resp.body());

            // Handle text prompt (type: "text", field: "prompt": "string")
            // Handle chat prompt (type: "chat", field: "prompt": [{role,content}])
            String type = root.path("type").asText("text");
            String text;
            if ("text".equals(type)) {
                text = root.path("prompt").asText("");
            } else {
                // chat: extract first system message content
                JsonNode msgs = root.path("prompt");
                text = msgs.isArray() && msgs.size() > 0
                        ? msgs.get(0).path("content").asText("")
                        : root.path("prompt").toString();
            }

            if (!text.isBlank()) {
                cache.put(name, text);
                log.info("[Prompts] Loaded '{}' v{} from Langfuse",
                        name, root.path("version").asInt(-1));
            }

        } catch (Exception e) {
            log.debug("[Prompts] Could not fetch '{}': {}", name, e.getMessage());
        }
    }

    private void uploadFallback(String name) {
        try {
            String text = fallbacks.get(name);
            if (text == null) return;

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("name",   name);
            payload.put("prompt", text);
            payload.put("type",   "text");
            payload.set("config", MAPPER.createObjectNode());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/public/prompts"))
                    .header("Content-Type",  "application/json")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 300) {
                log.info("[Prompts] Auto-uploaded fallback prompt '{}'", name);
            } else {
                log.debug("[Prompts] Upload '{}' returned HTTP {}", name, resp.statusCode());
            }
        } catch (Exception e) {
            log.debug("[Prompts] Upload '{}' failed: {}", name, e.getMessage());
        }
    }

    // ── Hardcoded fallbacks ───────────────────────────────────────────────────

    private static Map<String, String> buildFallbacks() {
        return Map.of(

            "bank-orchestrator", """
                You are a bank orchestrator. Follow these steps IN ORDER:

                STEP 1 — Delegate to bank-info-collector to collect all required info.
                  - bank-info-collector will ask the user for missing fields one at a time.
                  - If it returns a question (status: waiting), pass that question directly
                    to the user and wait for their reply.
                  - On each new user message, delegate to bank-info-collector again
                    until it returns status: complete.

                STEP 2 — Once bank-info-collector returns complete:
                  - Delegate to auth-agent to verify userId
                  - Delegate to balance-agent to check fromAccount balance
                  - Delegate to transfer-agent to execute the transfer

                STEP 3 — Report result clearly with transaction ID.

                For balance-only requests: go directly to balance-agent.
                For auth-only requests: go directly to auth-agent.
                """,

            "quality-evaluator", """
                You are a quality evaluator for banking requests.
                Check the conversation history for these fields:
                - userId (for TRANSFER and AUTH)
                - fromAccount (for TRANSFER and BALANCE)
                - toAccount (for TRANSFER only)
                - amount (for TRANSFER only)

                Default request type is TRANSFER.

                If ALL required fields for the request type are present:
                  → Call exit_loop tool to stop the loop

                If ANY field is missing:
                  → Do NOT call exit_loop
                  → Just respond with what is missing (e.g. "Missing: userId")
                """,

            "prompt-enhancer", """
                You are a prompt enhancer for a banking assistant.
                Ask the user for ONE missing piece of information at a time.
                Priority: userId → fromAccount → toAccount → amount
                Be friendly and short (1-2 sentences).
                Match the user's language (Farsi or English).
                After asking your question, you MUST call pause_for_user_input tool
                so the user can respond.
                """,

            "auth-agent", """
                You are an authentication agent for a banking system.
                Verify the user's identity based on their userId.
                Return: "User {userId} is verified and authenticated." on success.
                Return: "Authentication failed for {userId}." on failure.
                """,

            "balance-agent", """
                You are a balance inquiry agent for a banking system.
                Check the balance of the given account.
                Return the current balance clearly: "Account {accountId} balance: {amount} IRR"
                If the account does not exist, return: "Account {accountId} not found."
                """,

            "transfer-agent", """
                You are a money transfer agent for a banking system.
                Execute the transfer from fromAccount to toAccount for the given amount.
                On success, return: "Transfer successful. Transaction ID: TXN-{id}. {amount} IRR transferred from {from} to {to}."
                On failure, return the specific error message.
                """
        );
    }
}
