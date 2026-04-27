package com.bank.client.service;

import com.bank.client.client.OrchestratorA2AClient;
import com.bank.client.client.OrchestratorA2AClient.A2AResponse;
import com.bank.client.model.ChatResponse;
import com.bank.client.session.SessionStore;
import com.bank.client.session.SessionStore.Session;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private final SessionStore sessionStore;
    private final OrchestratorA2AClient a2aClient;
    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    public ChatService(SessionStore sessionStore, OrchestratorA2AClient a2aClient) {
        this.sessionStore = sessionStore;
        this.a2aClient = a2aClient;
    }

    public ChatResponse startChat(String userMessage) {
        String userId = "user-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String adkSessionId = a2aClient.createAdkSession(userId);
        String sessionId = sessionStore.createSession(adkSessionId, userId);
        return send(sessionId, userMessage, userId);
    }

    public ChatResponse reply(String sessionId, String userMessage) {
        if (!sessionStore.exists(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }
        Session session = sessionStore.get(sessionId);
        return send(sessionId, userMessage, session.userId());
    }

    // ── Streaming ────────────────────────────────────────────────────────────

    public SseEmitter streamStart(String userMessage) {
        String userId = "user-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String adkSessionId = a2aClient.createAdkSession(userId);
        String sessionId = sessionStore.createSession(adkSessionId, userId);
        return doStream(sessionId, userMessage, userId);
    }

    public SseEmitter streamReply(String sessionId, String userMessage) {
        if (!sessionStore.exists(sessionId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId);
        }
        Session session = sessionStore.get(sessionId);
        return doStream(sessionId, userMessage, session.userId());
    }

    private SseEmitter doStream(String sessionId, String userMessage, String userId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        Session session = sessionStore.get(sessionId);

        streamExecutor.submit(() ->
                a2aClient.streamMessage(userMessage, session.adkSessionId(), userId, sessionId, emitter));

        emitter.onCompletion(() -> cleanupIfDone(sessionId));
        return emitter;
    }

    private void cleanupIfDone(String sessionId) {
        // session is removed by the sync path; SSE path cleans up here
    }

    // ── Sync (unchanged) ─────────────────────────────────────────────────────

    private ChatResponse send(String sessionId, String userMessage, String userId) {
        Session session = sessionStore.get(sessionId);
        A2AResponse response = a2aClient.sendMessage(userMessage, session.adkSessionId(), userId);

        if (response.done()) {
            sessionStore.remove(sessionId);
            return new ChatResponse(sessionId, "result", response.agentText(), true);
        }
        return new ChatResponse(sessionId, "question", response.agentText(), false);
    }
}
