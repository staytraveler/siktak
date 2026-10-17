-- 6.1 이용권 보관(states.md M8·M8a, D2): 매칭 실패 신청 하나에 1회권 크레딧은 하나만 생긴다 (보관·미응답 처리 멱등).
CREATE UNIQUE INDEX uq_user_passes_credit_source
    ON user_passes (source_application_id)
    WHERE pass_type = 'SINGLE_CREDIT';
