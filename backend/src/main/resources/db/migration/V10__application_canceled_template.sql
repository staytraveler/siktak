-- 신청·참석 취소 안내 알림 (PRD 14장 "신청 완료: 수정·취소 방법", states.md 8장)
INSERT INTO notification_templates (code, title_template, body_template) VALUES
('APPLICATION_CANCELED', '취소가 접수됐어요',
 '{{slotLabel}} 참여가 취소됐어요. {{detail}}');
