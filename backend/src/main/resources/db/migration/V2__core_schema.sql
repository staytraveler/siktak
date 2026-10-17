-- 핵심 스키마 (docs/erd.md 3장·4장). 2.3 워킹 스켈레톤 범위.
-- venues, notifications, exception_cases 등 나머지 테이블은 해당 기능 작업에서 V3 이후로 추가한다.
-- 규칙: PK identity, created_at/updated_at timestamptz, 금액 BIGINT, enum은 VARCHAR, 복수 선택은 JSONB.

-- ---------------------------------------------------------------------------
-- 3-1. 사용자
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id         VARCHAR(100) NOT NULL UNIQUE,
    password_hash    VARCHAR(255),
    auth_provider    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL',
    provider_user_id VARCHAR(255),
    role             VARCHAR(20)  NOT NULL,
    name             VARCHAR(100) NOT NULL,
    phone            VARCHAR(30),
    account_status   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE profiles (
    user_id            BIGINT PRIMARY KEY REFERENCES users (id),
    birth_year         INT,
    gender             VARCHAR(10),
    job                VARCHAR(100),
    mbti               VARCHAR(4),
    interests          JSONB NOT NULL DEFAULT '[]'::jsonb,
    self_traits        JSONB NOT NULL DEFAULT '[]'::jsonb,
    preferred_traits   JSONB NOT NULL DEFAULT '[]'::jsonb,
    conversation_style VARCHAR(20),
    price_range        VARCHAR(30),
    food_restrictions  TEXT,
    allergies          TEXT,
    privacy_agreed_at  TIMESTAMPTZ,
    policy_agreed_at   TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 3-2. 회차
-- ---------------------------------------------------------------------------
CREATE TABLE regions (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event_slots (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region_id         BIGINT      NOT NULL REFERENCES regions (id),
    starts_at         TIMESTAMPTZ NOT NULL,
    ends_at           TIMESTAMPTZ NOT NULL,
    capacity          INT         NOT NULL,
    apply_deadline_at TIMESTAMPTZ NOT NULL,
    match_run_at      TIMESTAMPTZ NOT NULL,
    slot_status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_slots_match_run_at ON event_slots (match_run_at);

-- ---------------------------------------------------------------------------
-- 3-3. 신청 · 결제 · 이용권
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id              BIGINT       NOT NULL REFERENCES users (id),
    purpose              VARCHAR(30)  NOT NULL,
    amount               BIGINT       NOT NULL,
    provider             VARCHAR(20)  NOT NULL,
    order_id             VARCHAR(100) NOT NULL UNIQUE,
    provider_payment_key VARCHAR(255),
    tx_status            VARCHAR(20)  NOT NULL,
    approved_at          TIMESTAMPTZ,
    canceled_at          TIMESTAMPTZ,
    refund_key           VARCHAR(100) UNIQUE,
    retry_count          INT          NOT NULL DEFAULT 0,
    last_error           TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_passes (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id               BIGINT      NOT NULL REFERENCES users (id),
    pass_type             VARCHAR(20) NOT NULL,
    starts_at             TIMESTAMPTZ NOT NULL,
    expires_at            TIMESTAMPTZ,
    pass_status           VARCHAR(20) NOT NULL,
    source_payment_id     BIGINT REFERENCES payments (id),
    source_application_id BIGINT,
    used_application_id   BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE applications (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id),
    pass_type           VARCHAR(20) NOT NULL,
    payment_id          BIGINT REFERENCES payments (id),
    user_pass_id        BIGINT REFERENCES user_passes (id),
    application_status  VARCHAR(20) NOT NULL,
    payment_status      VARCHAR(30),
    match_status        VARCHAR(30),
    assigned_slot_id    BIGINT REFERENCES event_slots (id),
    transferred_from_id BIGINT REFERENCES applications (id),
    payment_due_at      TIMESTAMPTZ,
    match_fail_reason   TEXT,
    submitted_at        TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_applications_match_status ON applications (match_status);
CREATE INDEX idx_applications_user_id ON applications (user_id);

ALTER TABLE user_passes
    ADD CONSTRAINT fk_user_passes_source_application FOREIGN KEY (source_application_id) REFERENCES applications (id),
    ADD CONSTRAINT fk_user_passes_used_application FOREIGN KEY (used_application_id) REFERENCES applications (id);

CREATE TABLE application_slot_choices (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT      NOT NULL REFERENCES applications (id),
    event_slot_id  BIGINT      NOT NULL REFERENCES event_slots (id),
    priority       INT         NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (application_id, event_slot_id)
);
CREATE INDEX idx_application_slot_choices_event_slot_id ON application_slot_choices (event_slot_id);

-- ---------------------------------------------------------------------------
-- 3-4. 매칭
-- ---------------------------------------------------------------------------
CREATE TABLE match_runs (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_key           VARCHAR(100) NOT NULL UNIQUE,
    target_date       DATE         NOT NULL,
    algorithm_version VARCHAR(50)  NOT NULL,
    params            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    run_status        VARCHAR(20)  NOT NULL,
    candidate_count   INT          NOT NULL DEFAULT 0,
    summary           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    started_at        TIMESTAMPTZ  NOT NULL,
    finished_at       TIMESTAMPTZ,
    failure_reason    TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE dining_tables (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_slot_id     BIGINT      NOT NULL REFERENCES event_slots (id),
    match_run_id      BIGINT      NOT NULL REFERENCES match_runs (id),
    table_status      VARCHAR(20) NOT NULL,
    member_count      INT         NOT NULL,
    total_score       NUMERIC(6, 2),
    score_detail      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    summary           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    venue_id          BIGINT,     -- FK는 venues 테이블 생성 시(V3 이후) 추가
    reservation_name  VARCHAR(100),
    venue_assigned_at TIMESTAMPTZ,
    confirmed_at      TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE table_members (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dining_table_id BIGINT      NOT NULL REFERENCES dining_tables (id),
    application_id  BIGINT      NOT NULL REFERENCES applications (id),
    user_id         BIGINT      NOT NULL REFERENCES users (id),
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    joined_at       TIMESTAMPTZ NOT NULL,
    left_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 한 신청이 동시에 두 테이블에 배치되지 않게 한다 (PRD 7.4)
CREATE UNIQUE INDEX uq_table_members_active_application ON table_members (application_id) WHERE active;

CREATE TABLE match_placement_logs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    match_run_id   BIGINT      NOT NULL REFERENCES match_runs (id),
    application_id BIGINT      NOT NULL REFERENCES applications (id),
    decision       VARCHAR(20) NOT NULL,
    from_table_id  BIGINT,
    to_table_id    BIGINT,
    realloc_step   INT,
    reason_code    VARCHAR(50) NOT NULL,
    reason_text    TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 3-6. 단체방
-- ---------------------------------------------------------------------------
CREATE TABLE chat_rooms (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dining_table_id BIGINT      NOT NULL UNIQUE REFERENCES dining_tables (id),
    room_status     VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_members (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_room_id BIGINT      NOT NULL REFERENCES chat_rooms (id),
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    joined_at    TIMESTAMPTZ NOT NULL,
    left_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (chat_room_id, user_id)
);

CREATE TABLE chat_messages (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_room_id BIGINT      NOT NULL REFERENCES chat_rooms (id),
    sender_type  VARCHAR(10) NOT NULL,
    sender_id    BIGINT,
    message_type VARCHAR(20) NOT NULL,
    body         TEXT        NOT NULL,
    payload      JSONB,
    message_key  VARCHAR(150) UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_messages_room ON chat_messages (chat_room_id, id);

-- ---------------------------------------------------------------------------
-- 3-7. 피드백
-- ---------------------------------------------------------------------------
CREATE TABLE feedbacks (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id      BIGINT      NOT NULL UNIQUE REFERENCES applications (id),
    dining_table_id     BIGINT      NOT NULL REFERENCES dining_tables (id),
    user_id             BIGINT      NOT NULL REFERENCES users (id),
    table_rating        INT         NOT NULL CHECK (table_rating BETWEEN 1 AND 5),
    conversation_rating INT         NOT NULL CHECK (conversation_rating BETWEEN 1 AND 5),
    venue_rating        INT         NOT NULL CHECK (venue_rating BETWEEN 1 AND 5),
    rejoin_intent       INT CHECK (rejoin_intent BETWEEN 1 AND 5),
    recommend_score     INT CHECK (recommend_score BETWEEN 0 AND 10),
    comment             TEXT,
    submitted_at        TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 3-8. 운영 · 기록
-- ---------------------------------------------------------------------------
CREATE TABLE status_histories (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    target_type     VARCHAR(20) NOT NULL,
    target_id       BIGINT      NOT NULL,
    axis            VARCHAR(20) NOT NULL,
    from_status     VARCHAR(30),
    to_status       VARCHAR(30) NOT NULL,
    transition_code VARCHAR(10) NOT NULL,
    actor_type      VARCHAR(20) NOT NULL,
    actor_id        BIGINT,
    reason          TEXT,
    match_run_id    BIGINT REFERENCES match_runs (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_status_histories_target ON status_histories (target_type, target_id);

CREATE TABLE settings (
    key        VARCHAR(100) PRIMARY KEY,
    value      JSONB        NOT NULL,
    updated_by BIGINT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
