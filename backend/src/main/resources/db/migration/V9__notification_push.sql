-- INF-01·07 앱 푸시: 인앱 알림과 함께 푸시 발송 결과를 남긴다 (모의 푸시 채널, 외부 API 없음)
ALTER TABLE notifications
    ADD COLUMN push_status  VARCHAR(20),
    ADD COLUMN push_sent_at TIMESTAMPTZ;
