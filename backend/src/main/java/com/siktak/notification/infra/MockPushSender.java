package com.siktak.notification.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.siktak.notification.domain.Notification;
import com.siktak.notification.domain.PushSender;

import lombok.extern.slf4j.Slf4j;

/**
 * 모의 앱 푸시. 외부 푸시 서비스 없이 발송 사실만 기록한다 (MVP는 외부 API를 쓰지 않는다).
 * 브라우저에서는 앱이 열려 있을 때 프론트가 알림함을 읽어 시스템 알림으로 띄운다.
 * {@code siktak.notification.push.fail=true}면 항상 실패한다 (푸시 실패 시연용).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "siktak.notification.push.provider", havingValue = "mock", matchIfMissing = true)
public class MockPushSender implements PushSender {

    private volatile boolean fail;

    public MockPushSender(@Value("${siktak.notification.push.fail:false}") boolean fail) {
        this.fail = fail;
    }

    public void setFail(boolean fail) {
        this.fail = fail;
    }

    @Override
    public void push(Notification notification) {
        if (fail) {
            throw new IllegalStateException("모의 푸시 발송 실패 (siktak.notification.push.fail)");
        }
        log.debug("Mock push → user {} [{}] {}", notification.getUserId(), notification.getTemplateCode(),
                notification.getTitle());
    }
}
