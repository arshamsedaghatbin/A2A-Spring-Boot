package com.bank.client.controller;

import com.bank.client.model.ChatRequest;
import com.bank.client.model.ChatResponse;
import com.bank.client.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Start a new conversation.
     * Returns the first question from the agent (or final result if all info was provided upfront).
     */
    @PostMapping("/start")
    public ResponseEntity<ChatResponse> start(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.startChat(request.message()));
    }

    /**
     * Continue an existing conversation with the user's answer.
     * Keep calling until done=true.
     */
    @PostMapping("/{sessionId}/reply")
    public ResponseEntity<ChatResponse> reply(
            @PathVariable String sessionId,
            @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.reply(sessionId, request.message()));
    }

    // ── SSE streaming endpoints ───────────────────────────────────────────────

    /**
     * Start a conversation and stream every internal step in real-time.
     * Each SSE event named "step" carries a StepEvent JSON object.
     * The last event has done=true (result or question waiting for reply).
     */
    @PostMapping(value = "/stream/start", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStart(@RequestBody ChatRequest request) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChatController.class);
        logger.info("Starting streamed chat with request: {}", request);
        SseEmitter emitter = chatService.streamStart(request.message());
        logger.info("SSE stream started for request: {}", request);
        return emitter;
    }

    /**
     * Continue a streamed conversation with the user's answer.
     */
    @PostMapping(value = "/stream/{sessionId}/reply", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamReply(
            @PathVariable String sessionId,
            @RequestBody ChatRequest request) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ChatController.class);
        logger.info("Continuing streamed chat for sessionId: {}, request: {}", sessionId, request);
        SseEmitter emitter = chatService.streamReply(sessionId, request.message());
        logger.info("SSE stream reply sent for sessionId: {}", sessionId);
        return emitter;
    }
}
