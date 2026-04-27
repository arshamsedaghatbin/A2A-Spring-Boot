package com.bank.client.model;

/**
 * A single event emitted to the client during SSE streaming.
 *
 * type values:
 *   "tip"      – fun financial loading message while agents work internally
 *   "question" – real question from the orchestrator, waiting for user reply (done=false)
 *   "result"   – final answer from the orchestrator, conversation complete (done=true)
 *   "error"    – something went wrong (done=true)
 */
public record StepEvent(
        String sessionId,
        String type,
        String text,
        boolean done
) {}
