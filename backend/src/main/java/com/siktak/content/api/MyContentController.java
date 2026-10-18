package com.siktak.content.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.application.application.ApplicationService;
import com.siktak.chat.application.ChatService;
import com.siktak.chat.domain.ChatMessage;
import com.siktak.common.security.AuthUser;
import com.siktak.matching.application.MatchingService;
import com.siktak.matching.domain.TableMember;

import lombok.RequiredArgsConstructor;

/** 내 테이블의 대화 주제·아이스브레이커 (CON-01). 단체방에 게시된 콘텐츠를 그대로 보여준다. */
@RestController
@RequestMapping("/api/v1/me/applications/{applicationId}/contents")
@RequiredArgsConstructor
public class MyContentController {

    private final ApplicationService applicationService;
    private final MatchingService matchingService;
    private final ChatService chatService;

    /** 아직 게시 전(행사 시작 전)이거나 테이블이 없으면 빈 목록. */
    @GetMapping
    public List<ContentResponse> contents(@AuthenticationPrincipal AuthUser user, @PathVariable Long applicationId) {
        applicationService.getOwned(user.id(), applicationId);
        return matchingService.activeMembership(applicationId)
                .map(TableMember::getDiningTableId)
                .flatMap(chatService::findByTable)
                .map(room -> chatService.messagesFor(room.getId(), user.id()).stream()
                        .filter(m -> m.getMessageType() == ChatMessage.MessageType.CONTENT)
                        .map(ContentResponse::from).toList())
                .orElse(List.of());
    }

    /** payload는 {@code topics}(제목·본문 목록)와 {@code icebreaker}. */
    public record ContentResponse(Long messageId, String body, Map<String, Object> payload, Instant postedAt) {

        static ContentResponse from(ChatMessage m) {
            return new ContentResponse(m.getId(), m.getBody(), m.getPayload(), m.getCreatedAt());
        }
    }
}
