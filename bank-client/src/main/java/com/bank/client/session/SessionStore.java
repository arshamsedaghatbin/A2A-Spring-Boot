package com.bank.client.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SessionStore {

    public record Session(String adkSessionId, String userId) {}

    private final Map<String, Session> store = new ConcurrentHashMap<>();

    public String createSession(String adkSessionId, String userId) {
        String sessionId = UUID.randomUUID().toString();
        store.put(sessionId, new Session(adkSessionId, userId));
        return sessionId;
    }

    public Session get(String sessionId) {
        return store.get(sessionId);
    }

    public void remove(String sessionId) {
        store.remove(sessionId);
    }

    public boolean exists(String sessionId) {
        return store.containsKey(sessionId);
    }
}
