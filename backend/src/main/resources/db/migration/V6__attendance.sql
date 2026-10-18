-- 7.1 참석 (erd.md 3-7 attendances). 매칭 확정 시 SCHEDULED로 생성된다 (states.md T1).
CREATE TABLE attendances (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id    BIGINT      NOT NULL UNIQUE REFERENCES applications (id),
    dining_table_id   BIGINT      NOT NULL REFERENCES dining_tables (id),
    user_id           BIGINT      NOT NULL REFERENCES users (id),
    attendance_status VARCHAR(20) NOT NULL,
    no_show_candidate BOOLEAN     NOT NULL DEFAULT FALSE,
    checked_in_at     TIMESTAMPTZ,
    canceled_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attendances_table ON attendances (dining_table_id);
