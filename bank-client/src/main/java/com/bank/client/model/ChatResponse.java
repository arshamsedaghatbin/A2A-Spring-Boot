package com.bank.client.model;

public record ChatResponse(
        String sessionId,
        String type,      // "question" | "result" | "error"
        String text,
        boolean done
) {}
