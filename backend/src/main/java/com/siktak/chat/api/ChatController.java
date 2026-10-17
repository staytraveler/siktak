package com.siktak.chat.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/chat-rooms")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping("/{chatRoomId}/messages")
    public List<ChatMessageResponse> messages(@AuthenticationPrincipal AuthUser user, @PathVariable Long chatRoomId) {
        return chatService.messagesFor(chatRoomId, user.id()).stream().map(ChatMessageResponse::from).toList();
    }

    public record ChatMessageResponse(Long id, String senderType, Long senderId, String messageType, String body,
            Map<String, Object> payload, Instant createdAt) {

        static ChatMessageResponse from(ChatMessage m) {
            return new ChatMessageResponse(m.getId(), m.getSenderType().name(), m.getSenderId(),
                    m.getMessageType().name(), m.getBody(), m.getPayload(), m.getCreatedAt());
        }
    }
}
