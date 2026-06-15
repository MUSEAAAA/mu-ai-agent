package com.muse.muaiagent.controller;

import com.muse.muaiagent.agent.muManus;
import com.muse.muaiagent.service.KnowledgeEconomyService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private KnowledgeEconomyService knowledgeEconomyService;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @GetMapping("/knowledge_economy/chat/sync")
    public String doChatSync(String message, String chatId) {
        return knowledgeEconomyService.doChat(message, chatId);
    }

    @GetMapping(value = "/knowledge_economy/chat/sseMediaType", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatSSEMediaType(String message, String chatId) {
        return knowledgeEconomyService.doChatByStream(message, chatId);
    }

    @GetMapping(value = "/knowledge_economy/chat/sse")
    public Flux<ServerSentEvent<String>> doChatSSE(String message, String chatId) {
        return knowledgeEconomyService.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping("/knowledge_economy/chat/sse/emitter")
    public SseEmitter doChatSseEmitter(String message, String chatId) {
        SseEmitter emitter = new SseEmitter(180000L);
        knowledgeEconomyService.doChatByStream(message, chatId)
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(chunk);
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        muManus muManus = new muManus(allTools, dashscopeChatModel);
        return muManus.runStream(message);
    }

    @DeleteMapping("/chat/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable String chatId) {
        knowledgeEconomyService.clearChatMemory(chatId);
        return ResponseEntity.noContent().build();
    }
}
