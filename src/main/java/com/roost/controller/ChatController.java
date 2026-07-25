package com.roost.controller;

import com.roost.dto.ConversationSummaryDto;
import com.roost.model.Message;
import com.roost.model.User;
import com.roost.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<Message> sendMessage(@AuthenticationPrincipal User sender,
                                                @RequestBody Map<String, Object> payload) {
        if (sender == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(chatService.sendMessage(sender, payload));
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Message>> getChatHistory(@AuthenticationPrincipal User user,
                                                          @PathVariable Long userId) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(chatService.getChatHistory(user, userId));
    }

    @GetMapping("/active")
    public ResponseEntity<List<User>> getActiveChats(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(chatService.getActiveChats(user));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("count", chatService.getUnreadCount(user)));
    }

    @PostMapping("/mark-read/{userId}")
    public ResponseEntity<Map<String, Integer>> markAsRead(@AuthenticationPrincipal User user,
                                                             @PathVariable Long userId) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("marked", chatService.markAsRead(user, userId)));
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<Message> editMessage(@AuthenticationPrincipal User user,
                                                @PathVariable Long messageId,
                                                @RequestBody Map<String, Object> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        Object content = payload.get("content");
        Object nonce = payload.get("nonce");
        return ResponseEntity.ok(chatService.editMessage(
                user, messageId,
                content != null ? content.toString() : null,
                nonce != null ? nonce.toString() : null));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Map<String, Boolean>> deleteMessage(@AuthenticationPrincipal User user,
                                                                @PathVariable Long messageId) {
        if (user == null) return ResponseEntity.status(401).build();
        chatService.deleteMessage(user, messageId);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationSummaryDto>> getConversations(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(chatService.getConversations(user));
    }

    @PostMapping("/react/{messageId}")
    public ResponseEntity<Map<String, String>> toggleReaction(@AuthenticationPrincipal User user,
                                                                @PathVariable Long messageId,
                                                                @RequestBody Map<String, String> payload) {
        if (user == null) return ResponseEntity.status(401).build();
        String action = chatService.toggleReaction(user, messageId, payload.get("emoji"));
        return ResponseEntity.ok(Map.of("action", action));
    }

    @PostMapping("/typing/{recipientId}")
    public ResponseEntity<Void> sendTypingIndicator(@AuthenticationPrincipal User user,
                                                      @PathVariable Long recipientId) {
        if (user == null) return ResponseEntity.status(401).build();
        chatService.recordTyping(user.getId(), recipientId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/typing-status/{partnerId}")
    public ResponseEntity<Map<String, Boolean>> getTypingStatus(@AuthenticationPrincipal User user,
                                                                  @PathVariable Long partnerId) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of("typing", chatService.isTyping(partnerId, user.getId())));
    }
}
