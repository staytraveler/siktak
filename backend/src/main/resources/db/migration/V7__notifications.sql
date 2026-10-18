-- 5.2 알림 (erd.md 3-6): notifications, notification_templates + 템플릿 12종 시드 (PRD 14장)

CREATE TABLE notification_templates (
    code           VARCHAR(50)  PRIMARY KEY,
    title_template VARCHAR(200) NOT NULL,
    body_template  TEXT         NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE notifications (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id),
    template_code VARCHAR(50)  NOT NULL REFERENCES notification_templates (code),
    channel       VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    title         VARCHAR(200) NOT NULL,
    body          TEXT         NOT NULL,
    payload       JSONB,
    scheduled_at  TIMESTAMPTZ  NOT NULL,
    send_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts      INT          NOT NULL DEFAULT 0,
    last_error    TEXT,
    sent_at       TIMESTAMPTZ,
    read_at       TIMESTAMPTZ,
    dedup_key     VARCHAR(150) NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_status ON notifications (send_status, scheduled_at);
CREATE INDEX idx_notifications_user ON notifications (user_id, id DESC);

INSERT INTO notification_templates (code, title_template, body_template) VALUES
('APPLICATION_SUBMITTED', '신청이 완료됐어요',
 '{{slotLabel}} 모임 신청이 접수됐어요. 신청 내용은 앱에서 확인하고, 매칭 전까지 수정·취소할 수 있어요.'),
('PAYMENT_PENDING', '결제를 진행해 주세요',
 '{{slotLabel}} 모임 참가비 결제가 필요해요. 앱의 신청 상세에서 결제 기한 안에 결제를 마쳐 주세요.'),
('PAYMENT_PAID', '결제가 완료됐어요',
 '결제가 완료됐어요. 자동 매칭은 {{matchRunAt}}에 실행되고, 결과는 앱 알림으로 알려드려요.'),
('REALLOCATING', '다른 테이블을 찾고 있어요',
 '처음 배치된 테이블이 성립하지 않아 같은 조건의 다른 테이블을 자동으로 찾고 있어요. 따로 하실 일은 없어요.'),
('MATCH_CONFIRMED', '테이블이 확정됐어요',
 '{{slotLabel}} 모임 테이블이 확정됐어요. 장소: {{venueLine}} · 집합 시간: {{gatherTime}}. 자세한 안내는 앱 단체방에서 확인하세요.'),
('MATCH_FAILED', '이번 일정은 매칭되지 않았어요',
 '{{reason}} {{alternatives}}일정 이동·이용권 보관·환불 중 하나를 앱에서 선택해 주세요.'),
('TRANSFERRED', '일정 이동이 완료됐어요',
 '{{slotLabel}} 모임으로 일정이 옮겨졌어요. 자동 매칭은 {{matchRunAt}}에 실행돼요.'),
('REFUND_REQUESTED', '환불을 접수했어요',
 '{{amount}} 환불을 접수했어요. 처리 상태는 앱에서 확인할 수 있어요.'),
('REFUND_COMPLETED', '환불이 완료됐어요',
 '{{amount}} 환불이 완료됐어요. 카드사에 따라 반영까지 며칠 걸릴 수 있어요.'),
('REMINDER_D1', '내일 모임이 있어요',
 '내일 {{slotLabel}} 모임이에요. 장소: {{venueLine}} · 예약명: {{reservationName}}. 환불은 진행 2일 전까지만 가능하고, 당일 취소와 단체방 퇴장은 할 수 없어요. 노쇼는 다음 신청에 불이익이 있을 수 있어요.'),
('FEEDBACK_REQUEST', '모임은 어떠셨나요?',
 '오늘 모임의 만족도와 안전 피드백을 남겨 주세요. 다음 매칭에 반영돼요.'),
('NEXT_SLOT_RECOMMEND', '다음 모임을 추천해요',
 '선호하시는 지역·요일에 맞는 다음 일정이 열렸어요. 앱에서 확인해 보세요.');
