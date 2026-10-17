-- ShedLock: 스케줄 작업 중복 실행 방지용 잠금 테이블
-- 도메인 테이블은 기능 작업에서 새 버전 파일로 추가한다 (docs/erd.md).
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
