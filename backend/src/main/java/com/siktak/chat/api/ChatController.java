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
        List<ChatMessage> messages = chatService.messagesFor(chatRoomId, user.id());
        Map<Long, String> initials = chatService.senderInitials(messages);
        return messages.stream().map(m -> ChatMessageResponse.from(m, user.id(), initials)).toList();
    }

    /** senderId는 내 메시지일 때만 내려준다. senderLabel은 이니셜뿐이다 (실명·연락처 비노출). */
    public record ChatMessageResponse(Long id, String senderType, Long senderId, String senderLabel, boolean mine,
            String messageType, String body, Map<String, Object> payload, Instant createdAt) {

        public static ChatMessageResponse from(ChatMessage m, Long viewerId, Map<Long, String> initials) {
            boolean mine = m.getSenderId() != null && m.getSenderId().equals(viewerId);
            return new ChatMessageResponse(m.getId(), m.getSenderType().name(), mine ? m.getSenderId() : null,
                    m.getSenderId() == null ? null : initials.get(m.getSenderId()), mine, m.getMessageType().name(),
                    m.getBody(), m.getPayload(), m.getCreatedAt());
        }
    }
}
