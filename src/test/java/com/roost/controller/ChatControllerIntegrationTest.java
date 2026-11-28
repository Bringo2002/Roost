package com.roost.controller;

import com.roost.dto.EditMessageRequestDto;
import com.roost.dto.ReactionRequestDto;
import com.roost.dto.SendMessageRequestDto;
import com.roost.exception.GlobalExceptionHandler;
import com.roost.model.Message;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link ChatController}. Exercises request
 * binding, Bean Validation, and response shaping for all 13 endpoints
 * without booting the full Spring context.
 */
class ChatControllerIntegrationTest {

    private ChatService chatService;
    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        user = new User();
        user.setId(1L);
        user.setName("Alice");
        user.setRole(Role.TENANT);

        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(principalResolver(user))
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    private static HandlerMethodArgumentResolver principalResolver(User principal) {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                return principal;
            }
        };
    }

    private Message stubMessage() {
        User sender = new User();
        sender.setId(1L);
        sender.setName("Alice");
        User recipient = new User();
        recipient.setId(2L);
        recipient.setName("Bob");
        Message m = new Message();
        m.setId(100L);
        m.setSender(sender);
        m.setRecipient(recipient);
        m.setContent("ciphertext");
        m.setNonce("nonce");
        m.setTimestamp(LocalDateTime.of(2025, 1, 1, 12, 0));
        return m;
    }

    // ================================================================
    //  POST /api/chat — sendMessage
    // ================================================================

    @Test
    @DisplayName("POST /api/chat — valid body → 200, service called")
    void sendMessage_success() throws Exception {
        when(chatService.sendMessage(any(User.class), any(SendMessageRequestDto.class))).thenReturn(stubMessage());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":2,\"content\":\"cipher\",\"nonce\":\"n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.content").value("ciphertext"));

        verify(chatService).sendMessage(any(User.class), any(SendMessageRequestDto.class));
    }

    @Test
    @DisplayName("POST /api/chat — missing recipientId → 400")
    void sendMessage_missingRecipientId() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"cipher\",\"nonce\":\"n\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("POST /api/chat — no auth → 401")
    void sendMessage_unauthenticated() {
        ChatController controller = new ChatController(chatService);
        SendMessageRequestDto dto = new SendMessageRequestDto();
        dto.setRecipientId(2L);
        dto.setContent("c");
        dto.setNonce("n");

        assertEquals(401, controller.sendMessage(null, dto).getStatusCode().value());
        verifyNoInteractions(chatService);
    }

    // ================================================================
    //  GET /api/chat/history/{userId}
    // ================================================================

    @Test
    @DisplayName("GET /api/chat/history/2 → 200, returns list")
    void getChatHistory_success() throws Exception {
        when(chatService.getChatHistory(any(), eq(2L), any(), any(), any())).thenReturn(List.of(stubMessage()));

        mockMvc.perform(get("/api/chat/history/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    @DisplayName("GET /api/chat/history/2?beforeId=50&limit=10 → query params forwarded")
    void getChatHistory_withParams() throws Exception {
        when(chatService.getChatHistory(any(), eq(2L), eq(50L), any(), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/history/2").param("beforeId", "50").param("limit", "10"))
                .andExpect(status().isOk());

        verify(chatService).getChatHistory(any(), eq(2L), eq(50L), any(), eq(10));
    }

    // ================================================================
    //  GET /api/chat/active
    // ================================================================

    @Test
    @DisplayName("GET /api/chat/active → 200, returns list")
    void getActiveChats_success() throws Exception {
        when(chatService.getActiveChats(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ================================================================
    //  GET /api/chat/unread-count
    // ================================================================

    @Test
    @DisplayName("GET /api/chat/unread-count → 200, {count: N}")
    void getUnreadCount_success() throws Exception {
        when(chatService.getUnreadCount(any())).thenReturn(5L);

        mockMvc.perform(get("/api/chat/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    // ================================================================
    //  POST /api/chat/mark-read/{userId}
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/mark-read/2 → 200, {marked: N}")
    void markAsRead_success() throws Exception {
        when(chatService.markAsRead(any(), eq(2L))).thenReturn(3);

        mockMvc.perform(post("/api/chat/mark-read/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marked").value(3));
    }

    // ================================================================
    //  PUT /api/chat/{messageId} — editMessage
    // ================================================================

    @Test
    @DisplayName("PUT /api/chat/10 — valid body → 200")
    void editMessage_success() throws Exception {
        when(chatService.editMessage(any(), eq(10L), eq("new"), eq("n2"))).thenReturn(stubMessage());

        mockMvc.perform(put("/api/chat/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"new\",\"nonce\":\"n2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @DisplayName("PUT /api/chat/10 — blank content → 400")
    void editMessage_blankContent() throws Exception {
        mockMvc.perform(put("/api/chat/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\",\"nonce\":\"n2\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("PUT /api/chat/10 — missing nonce → 400")
    void editMessage_missingNonce() throws Exception {
        mockMvc.perform(put("/api/chat/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"new\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    // ================================================================
    //  DELETE /api/chat/{messageId}
    // ================================================================

    @Test
    @DisplayName("DELETE /api/chat/10 → 200, {deleted: true}")
    void deleteMessage_success() throws Exception {
        mockMvc.perform(delete("/api/chat/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(chatService).deleteMessage(any(), eq(10L));
    }

    // ================================================================
    //  GET /api/chat/conversations
    // ================================================================

    @Test
    @DisplayName("GET /api/chat/conversations → 200, returns list")
    void getConversations_success() throws Exception {
        when(chatService.getConversations(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ================================================================
    //  POST /api/chat/react/{messageId}
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/react/10 — valid body → 200, {action: added}")
    void toggleReaction_success() throws Exception {
        when(chatService.toggleReaction(any(), eq(10L), eq("👍"))).thenReturn("added");

        mockMvc.perform(post("/api/chat/react/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"👍\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("added"));
    }

    @Test
    @DisplayName("POST /api/chat/react/10 — blank emoji → 400")
    void toggleReaction_blankEmoji() throws Exception {
        mockMvc.perform(post("/api/chat/react/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emoji\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }

    // ================================================================
    //  POST /api/chat/typing/{recipientId}
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/typing/2 → 200 empty")
    void sendTypingIndicator_success() throws Exception {
        mockMvc.perform(post("/api/chat/typing/2"))
                .andExpect(status().isOk());

        verify(chatService).recordTyping(eq(1L), eq(2L));
    }

    // ================================================================
    //  GET /api/chat/typing-status/{partnerId}
    // ================================================================

    @Test
    @DisplayName("GET /api/chat/typing-status/2 → 200, {typing: false}")
    void getTypingStatus_success() throws Exception {
        when(chatService.isTyping(eq(2L), eq(1L))).thenReturn(false);

        mockMvc.perform(get("/api/chat/typing-status/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typing").value(false));
    }

    // ================================================================
    //  POST /api/chat/{partnerId}/clear
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/5/clear → 200, {cleared: true}")
    void clearChat_success() throws Exception {
        mockMvc.perform(post("/api/chat/5/clear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(true));

        verify(chatService).clearChat(any(), eq(5L));
    }

    // ================================================================
    //  POST /api/chat/{partnerId}/delete-chat
    // ================================================================

    @Test
    @DisplayName("POST /api/chat/5/delete-chat → 200, {deleted: true}")
    void deleteChat_success() throws Exception {
        mockMvc.perform(post("/api/chat/5/delete-chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(chatService).deleteChat(any(), eq(5L));
    }

    // ================================================================
    //  Auth guard coverage — direct controller invocation with null user
    // ================================================================

    @Test
    @DisplayName("all endpoints return 401 when user is null")
    void allEndpoints_unauthenticated() {
        ChatController controller = new ChatController(chatService);

        assertEquals(401, controller.sendMessage(null, new SendMessageRequestDto()).getStatusCode().value());
        assertEquals(401, controller.getChatHistory(null, 2L, null, null, null).getStatusCode().value());
        assertEquals(401, controller.getActiveChats(null).getStatusCode().value());
        assertEquals(401, controller.getUnreadCount(null).getStatusCode().value());
        assertEquals(401, controller.markAsRead(null, 2L).getStatusCode().value());
        assertEquals(401, controller.editMessage(null, 10L, new EditMessageRequestDto()).getStatusCode().value());
        assertEquals(401, controller.deleteMessage(null, 10L).getStatusCode().value());
        assertEquals(401, controller.getConversations(null).getStatusCode().value());
        assertEquals(401, controller.toggleReaction(null, 10L, new ReactionRequestDto()).getStatusCode().value());
        assertEquals(401, controller.sendTypingIndicator(null, 2L).getStatusCode().value());
        assertEquals(401, controller.getTypingStatus(null, 2L).getStatusCode().value());
        assertEquals(401, controller.clearChat(null, 5L).getStatusCode().value());
        assertEquals(401, controller.deleteChat(null, 5L).getStatusCode().value());

        verifyNoInteractions(chatService);
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
