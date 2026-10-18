package com.siktak.notification.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.siktak.notification.domain.Notification;
import com.siktak.notification.domain.NotificationSender;

/**
 * 앱 내 알림 발송. 알림 행이 곧 알림함이라 따로 보낼 곳이 없고, 성공하면 서비스가 SENT로 표시한다.
 * {@code siktak.notification.mock.fail=true}면 항상 실패해 재시도·예외 처리함 전환을 시연할 수 있다 (INF-08).
 */
@Component
@ConditionalOnProperty(name = "siktak.notification.channel", havingValue = "in-app", matchIfMissing = true)
public class InAppNotificationSender implements NotificationSender {

    private volatile boolean failInjection;

    public InAppNotificationSender(@Value("${siktak.notification.mock.fail:false}") boolean failInjection) {
        this.failInjection = failInjection;
    }

    /** 데모·테스트에서 실패 주입을 켜고 끈다. */
    public void setFailInjection(boolean failInjection) {
        this.failInjection = failInjection;
    }

    @Override
    public void send(Notification notification) {
        if (failInjection) {
            throw new IllegalStateException("모의 알림 발송 실패 (siktak.notification.mock.fail)");
        }
    }
}
