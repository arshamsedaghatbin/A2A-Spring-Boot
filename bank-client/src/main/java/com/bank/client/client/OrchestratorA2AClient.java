package com.bank.client.client;

import com.bank.client.model.StepEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class OrchestratorA2AClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorA2AClient.class);
    private static final String APP_NAME = "bank-orchestrator";
    /** Max SSE events per invocation before we abort — guards against runaway loops */
    private static final int MAX_EVENTS = 50;

    private final RestClient restClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrchestratorA2AClient(@Value("${orchestrator.url}") String orchestratorUrl,
                                  @Value("${orchestrator.timeout-seconds:60}") int timeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(orchestratorUrl)
                .requestFactory(factory)
                .build();
    }

    public record A2AResponse(String agentText, boolean done) {}

    /** Creates a new ADK session and returns its ID. */
    public String createAdkSession(String userId) {
        String raw = restClient.post()
                .uri("/apps/{app}/users/{user}/sessions", APP_NAME, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(String.class);
        try {
            return mapper.readTree(raw).path("id").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ADK session", e);
        }
    }

    /** Sends a user message and returns the agent's response. */
    public A2AResponse sendMessage(String userText, String adkSessionId, String userId) {
        ObjectNode body = buildRunBody(userText, adkSessionId, userId);
        log.debug("POST /run: appName={} sessionId={}", APP_NAME, adkSessionId);

        String raw = restClient.post()
                .uri("/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        log.debug("Response events: {}", raw);
        return parseEvents(raw);
    }

    private A2AResponse parseEvents(String raw) {
        try {
            JsonNode events = mapper.readTree(raw);

            String lastOrchestratorText = "";
            boolean hasRemoteAgentEvent = false;
            boolean hasEscalate = false;

            for (JsonNode event : events) {
                String author = event.path("author").asText("");

                // Check if any remote banking agent responded → conversation is done
                if (author.equals("auth-agent") || author.equals("balance-agent") || author.equals("transfer-agent")) {
                    hasRemoteAgentEvent = true;
                }

                // Capture escalate flag (loop paused for user input)
                if (event.path("actions").path("escalate").asBoolean(false)) {
                    hasEscalate = true;
                }

                // The bank-orchestrator's final text is what the user sees
                if (author.equals(APP_NAME)) {
                    String text = extractText(event);
                    if (!text.isBlank()) {
                        lastOrchestratorText = text;
                    }
                }
            }

            boolean done = hasRemoteAgentEvent || (!hasEscalate && !lastOrchestratorText.isBlank());
            return new A2AResponse(lastOrchestratorText, done);

        } catch (Exception e) {
            log.error("Failed to parse events: {}", raw, e);
            return new A2AResponse("Failed to communicate with orchestrator.", true);
        }
    }

    /**
     * Streams events from /run_sse and emits human-readable StepEvents to the SseEmitter.
     * Blocks until the agent finishes or pauses for user input.
     */
    public void streamMessage(String userText, String adkSessionId, String userId,
                              String sessionId, SseEmitter emitter) {
        ObjectNode body = buildRunBody(userText, adkSessionId, userId);
        try {
            restClient.post()
                    .uri("/run_sse")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((req, resp) -> {
                        boolean hasEscalate = false;
                        boolean hasRemoteAgent = false;
                        String[] lastPromptText = {""};
                        String[] lastAgentText = {""};
                        boolean[] emittedFinal = {false};
                        int[] eventCount = {0};

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (++eventCount[0] > MAX_EVENTS) {
                                    log.warn("Max events ({}) reached for session, aborting stream", MAX_EVENTS);
                                    break;
                                }
                                if (!line.startsWith("data:")) continue;
                                String json = line.substring(5).trim();
                                if (json.isEmpty()) continue;

                                JsonNode event = mapper.readTree(json);
                                String author = event.path("author").asText("");
                                if (event.path("actions").path("escalate").asBoolean(false)) hasEscalate = true;
                                if (isRemoteAgent(author)) hasRemoteAgent = true;

                                if (author.equals("prompt-enhancer")) {
                                    String t = extractText(event);
                                    if (!t.isBlank()) lastPromptText[0] = t;
                                }

                                // track any non-orchestrator text as fallback
                                if (!author.equals(APP_NAME)) {
                                    String t = extractText(event);
                                    if (!t.isBlank()) lastAgentText[0] = t;
                                }

                                StepEvent step = toStep(event, author, sessionId,
                                        hasEscalate, hasRemoteAgent, lastPromptText[0]);
                                if (step != null) {
                                    emitter.send(SseEmitter.event()
                                            .name("step")
                                            .data(mapper.writeValueAsString(step)));
                                    if (step.type().equals("question") || step.type().equals("result"))
                                        emittedFinal[0] = true;
                                    if (step.done()) break;
                                }
                            }
                        }

                        // orchestrator never relayed the agent's message (e.g. balance-agent asked for account)
                        if (!emittedFinal[0]) {
                            StepEvent fallback;
                            if (!lastAgentText[0].isBlank()) {
                                boolean isQuestion = lastAgentText[0].endsWith("?") || lastAgentText[0].endsWith("؟");
                                fallback = new StepEvent(sessionId,
                                        isQuestion ? "question" : "result",
                                        lastAgentText[0], !isQuestion);
                            } else {
                                fallback = new StepEvent(sessionId, "error",
                                        "The agent did not respond in time. Please try again.", true);
                            }
                            emitter.send(SseEmitter.event().name("step")
                                    .data(mapper.writeValueAsString(fallback)));
                        }

                        emitter.complete();
                        return null;
                    });
        } catch (Exception e) {
            log.error("SSE stream error", e);
            try {
                emitter.send(SseEmitter.event().name("step")
                        .data(mapper.writeValueAsString(
                                new StepEvent(sessionId, "error", e.getMessage(), true))));
                emitter.complete();
            } catch (Exception ignore) {
                emitter.completeWithError(e);
            }
        }
    }

    private ObjectNode buildRunBody(String userText, String adkSessionId, String userId) {
        ObjectNode body = mapper.createObjectNode();
        body.put("appName", APP_NAME);
        body.put("userId", userId);
        body.put("sessionId", adkSessionId);
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        ArrayNode parts = mapper.createArrayNode();
        ObjectNode part = mapper.createObjectNode();
        part.put("text", userText);
        parts.add(part);
        message.set("parts", parts);
        body.set("newMessage", message);
        return body;
    }

    private StepEvent toStep(JsonNode event, String author, String sessionId,
                             boolean hasEscalate, boolean hasRemoteAgent, String lastPromptText) {
        JsonNode parts = event.path("content").path("parts");
        String text = "";
        boolean hasFunctionCall = false;

        String functionName = "";
        for (JsonNode p : parts) {
            if (p.has("text")) text = p.path("text").asText().trim();
            if (p.has("functionCall")) {
                hasFunctionCall = true;
                functionName = p.path("functionCall").path("name").asText("");
            }
        }

        String transferTarget = event.path("actions").path("transferToAgent").asText("");

        // sub-agents → filtered out entirely
        if (!author.equals(APP_NAME)) return null;

        // ── Orchestrator only ─────────────────────────────────────────────────
        if (!transferTarget.isBlank())
            return new StepEvent(sessionId, "tip", FunTips.forDelegating(transferTarget), false);

        if (hasFunctionCall)
            return new StepEvent(sessionId, "tip", FunTips.forCalling(), false);

        if (!text.isBlank()) {
            if (text.equalsIgnoreCase("waiting for user input.") && !lastPromptText.isBlank())
                return new StepEvent(sessionId, "question", lastPromptText, false);
            boolean done = hasRemoteAgent || !hasEscalate;
            return new StepEvent(sessionId, done ? "result" : "question", text, done);
        }

        return null;
    }

    private boolean isRemoteAgent(String author) {
        return author.equals("auth-agent") || author.equals("balance-agent") || author.equals("transfer-agent");
    }

    private String extractText(JsonNode event) {
        JsonNode parts = event.path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    return part.path("text").asText().trim();
                }
            }
        }
        return "";
    }
}
