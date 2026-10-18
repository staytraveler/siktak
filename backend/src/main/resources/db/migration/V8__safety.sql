-- 7.2 신고·운영자 조치 (erd.md 3-7). user_blocks는 V3에 이미 있다.
CREATE TABLE safety_reports (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id     BIGINT      NOT NULL REFERENCES users (id),
    reported_id     BIGINT      NOT NULL REFERENCES users (id),
    dining_table_id BIGINT      REFERENCES dining_tables (id),
    reason_type     VARCHAR(30) NOT NULL,
    description     TEXT        NOT NULL,
    evidence_url    VARCHAR(500),
    report_status   VARCHAR(20) NOT NULL,  -- RECEIVED / REVIEWING / RESOLVED / DISMISSED
    handled_by      BIGINT      REFERENCES users (id),
    handled_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (reporter_id <> reported_id)
);
-- 같은 테이블에서 같은 사람을 다시 신고해도 1건 (멱등)
CREATE UNIQUE INDEX uq_safety_reports_pair_table ON safety_reports (reporter_id, reported_id, dining_table_id);
CREATE INDEX idx_safety_reports_status ON safety_reports (report_status, id);

CREATE TABLE user_sanctions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    sanction_type VARCHAR(20) NOT NULL,  -- WARNING / RESTRICTION / PERMANENT_BAN
    reason        TEXT        NOT NULL,
    report_id     BIGINT      REFERENCES safety_reports (id),
    starts_at     TIMESTAMPTZ NOT NULL,
    ends_at       TIMESTAMPTZ,
    operator_id   BIGINT      NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 한 신고에서 같은 조치가 두 번 기록되지 않게 (resolve 재호출 멱등)
CREATE UNIQUE INDEX uq_user_sanctions_report ON user_sanctions (report_id) WHERE report_id IS NOT NULL;
CREATE INDEX idx_user_sanctions_user ON user_sanctions (user_id);
