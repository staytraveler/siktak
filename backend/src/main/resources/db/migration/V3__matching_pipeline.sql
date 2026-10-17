-- 매칭 운영 연결 (4.4): 식당·수용량, 대화 콘텐츠, 예외 큐, 대체 일정 해결, 관계 데이터(차단·사람별 선호·만남 이력).
-- docs/erd.md 3-4·3-5·3-6·3-7·3-8 정의를 따른다. 규칙은 V2와 같다 (PK identity, timestamptz, enum은 VARCHAR, 복수 값은 JSONB).

-- ---------------------------------------------------------------------------
-- 3-5. 식당
-- ---------------------------------------------------------------------------
CREATE TABLE venues (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region_id   BIGINT       NOT NULL REFERENCES regions (id),
    name        VARCHAR(100) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    map_url     VARCHAR(500),
    price_range VARCHAR(30),  -- UNDER_20K / 20K_40K / OVER_40K (matching.md 4장)
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_venues_region ON venues (region_id);

CREATE TABLE venue_slot_capacities (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id            BIGINT      NOT NULL REFERENCES venues (id),
    event_slot_id       BIGINT      NOT NULL REFERENCES event_slots (id),
    max_tables          INT         NOT NULL CHECK (max_tables >= 0),
    max_seats_per_table INT         NOT NULL CHECK (max_seats_per_table > 0),
    reserved_tables     INT         NOT NULL DEFAULT 0 CHECK (reserved_tables >= 0),
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (venue_id, event_slot_id),
    CHECK (reserved_tables <= max_tables)
);
CREATE INDEX idx_venue_slot_capacities_slot ON venue_slot_capacities (event_slot_id);

ALTER TABLE dining_tables
    ADD CONSTRAINT fk_dining_tables_venue FOREIGN KEY (venue_id) REFERENCES venues (id);

-- ---------------------------------------------------------------------------
-- 3-6. 대화 콘텐츠
-- ---------------------------------------------------------------------------
CREATE TABLE conversation_contents (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content_type VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT         NOT NULL,
    tags         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- 3-4. 매칭: 대체 일정 해결, 배치 로그 보강
-- ---------------------------------------------------------------------------
CREATE TABLE match_resolutions (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id     BIGINT      NOT NULL UNIQUE REFERENCES applications (id),
    offered_slot_ids   JSONB       NOT NULL DEFAULT '[]'::jsonb,
    choice             VARCHAR(20),
    chosen_slot_id     BIGINT REFERENCES event_slots (id),
    new_application_id BIGINT REFERENCES applications (id),
    resolution_status  VARCHAR(20) NOT NULL,
    respond_by         TIMESTAMPTZ NOT NULL,
    responded_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_match_resolutions_status ON match_resolutions (resolution_status, respond_by);

-- 엔진 로그는 회차 기준(임시 테이블은 DB ID가 없다)이라 출발·도착 회차와 미배치 원인을 함께 남긴다 (matching.md 11장 I8).
ALTER TABLE match_placement_logs
    ADD COLUMN from_slot_id BIGINT REFERENCES event_slots (id),
    ADD COLUMN to_slot_id   BIGINT REFERENCES event_slots (id),
    ADD COLUMN cause_code   VARCHAR(50);
CREATE INDEX idx_match_placement_logs_run ON match_placement_logs (match_run_id, id);
CREATE INDEX idx_match_placement_logs_application ON match_placement_logs (application_id, id);

-- ---------------------------------------------------------------------------
-- 3-8. 예외 큐
-- ---------------------------------------------------------------------------
CREATE TABLE exception_cases (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_type       VARCHAR(30)  NOT NULL,
    case_status     VARCHAR(20)  NOT NULL,
    application_id  BIGINT REFERENCES applications (id),
    dining_table_id BIGINT REFERENCES dining_tables (id),
    ref_type        VARCHAR(30),
    ref_id          BIGINT,
    reason          TEXT         NOT NULL,
    detail          JSONB,
    case_key        VARCHAR(150) NOT NULL UNIQUE,
    resolved_by     BIGINT REFERENCES users (id),
    resolution_note TEXT,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_exception_cases_status ON exception_cases (case_status, id);

-- ---------------------------------------------------------------------------
-- 관계 데이터 (매칭 입력). 쓰기 기능은 7.x에서 붙는다.
-- ---------------------------------------------------------------------------
CREATE TABLE user_blocks (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    blocker_id BIGINT      NOT NULL REFERENCES users (id),
    blocked_id BIGINT      NOT NULL REFERENCES users (id),
    source     VARCHAR(20) NOT NULL,  -- USER_BLOCK / REPORT
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);

CREATE TABLE person_preferences (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_user_id    BIGINT      NOT NULL REFERENCES users (id),
    to_user_id      BIGINT      NOT NULL REFERENCES users (id),
    dining_table_id BIGINT      NOT NULL REFERENCES dining_tables (id),
    preference      VARCHAR(20) NOT NULL,  -- WANT_AGAIN / AVOID
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (from_user_id, to_user_id, dining_table_id)
);

CREATE TABLE user_encounters (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_low_id     BIGINT      NOT NULL REFERENCES users (id),
    user_high_id    BIGINT      NOT NULL REFERENCES users (id),
    dining_table_id BIGINT      NOT NULL REFERENCES dining_tables (id),
    met_at          TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_low_id, user_high_id, dining_table_id),
    CHECK (user_low_id < user_high_id)
);
