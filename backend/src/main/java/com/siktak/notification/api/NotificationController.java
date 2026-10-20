package com.siktak.notification.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.siktak.common.security.AuthUser;
import com.siktak.notification.application.NotificationService;
import com.siktak.notification.domain.Notification;

import lombok.RequiredArgsConstructor;

/** 참가자 알림함 (INF-01). 본인 알림만 보인다. */
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthUser user) {
        return notificationService.inbox(user.id()).stream().map(NotificationResponse::from).toList();
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse read(@AuthenticationPrincipal AuthUser user, @PathVariable Long notificationId) {
        return NotificationResponse.from(notificationService.markRead(user.id(), notificationId));
    }

    public record NotificationResponse(Long id, String templateCode, String title, String body,
            Map<String, Object> payload, Instant sentAt, Instant readAt, String pushStatus) {

        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getTemplateCode(), n.getTitle(), n.getBody(), n.getPayload(),
                    n.getSentAt(), n.getReadAt(), n.getPushStatus());
        }
    }
}
