package com.siktak.chat.api;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.common.security.AuthUser;

import lombok.RequiredArgsConstructor;

/** 단체방 메시지 전송 (9.2). 채팅이라 멱등 키·상태 전이는 없다. */
@RestController
@RequestMapping("/api/v1/tables/{tableId}/messages")
@RequiredArgsConstructor
public class TableChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatController.ChatMessageResponse send(@AuthenticationPrincipal AuthUser user, @PathVariable Long tableId,
            @RequestBody SendMessageRequest request) {
        ChatMessage message = chatService.postUserMessage(tableId, user.id(), request.content());
        return ChatController.ChatMessageResponse.from(message, user.id(),
                chatService.senderInitials(java.util.List.of(message)));
    }

    public record SendMessageRequest(String content) {
    }
}
