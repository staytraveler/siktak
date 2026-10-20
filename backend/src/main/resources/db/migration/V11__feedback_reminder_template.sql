-- 피드백 미응답 1회 리마인드 (PRD 15장). 다음 신청을 막지 않는다.
INSERT INTO notification_templates (code, title_template, body_template) VALUES
('FEEDBACK_REMINDER', '피드백을 아직 기다리고 있어요',
 '지난 모임의 만족도와 안전 피드백을 한 번만 더 부탁드려요. 남겨 주신 내용은 다음 매칭에 반영돼요. 이 알림은 한 번만 보내요.');
