# 결정 로그 (decisions.md)

> 형식: `- YYYY-MM-DD [태그] 결정 내용 — 근거`
> 태그: `[기획]` 기획 세션 결정 · `[CC]` 개발 세션(Claude Code) 결정
> 명세(`docs/domain/*`)와 함께 수정한다. 최신 항목을 각 섹션 아래에 추가한다.

## 상태·정책 (출처: `docs/domain/states.md` 10장)

- 2026-10-17 [기획] D1 이용권 구조: 1회권 + 멤버십(1·3·6개월 기간권, 한 번 결제, 자동 갱신·횟수 제한 없음) — APP-04는 선택만 요구, 정기결제는 MVP 범위 밖
- 2026-10-17 [기획] D2 이용권 보관: 새 상태 없이 매칭 `NO_MATCH` + 1회권 크레딧 발급(`UserPass`) — PRD enum 유지 원칙
- 2026-10-17 [기획] D3 일정 이동: 기존 신청 `TRANSFERRED` 종료 + 새 신청 생성, 결제 공유 — 한 신청에 `TRANSFERRED`·`WAITING` 동시 불가, 중복 결제 방지
- 2026-10-17 [기획] D4 확정 전·2일 전까지 자발 취소: 1회권 자동 환불 — PRD는 2일 전 이후 환불 불가만 명시
- 2026-10-17 [기획] D5 확정 후·2일 전까지 취소: 1회권 자동 환불(`CANCELED_EARLY`) — D4와 같은 기준
- 2026-10-17 [기획] D6 대체 일정 미응답: 원래 행사일 경과 시 보관 처리(환불처럼 되돌릴 수 없는 결정은 자동으로 하지 않음) — PRD 7.3 "참가자가 선택" 원칙을 최대한 유지
- 2026-10-17 [기획] D7 정책 수치: states.md 9장 설정값으로 관리 — 회사 정책 확정 시 값만 변경
- 2026-10-17 [기획] D8 미배치 + 남은 선택 회차 있음: `WAITING` 복귀(M4a) — PRD 7.4 재배치 3·4단계(다른 날짜)를 회차별 매칭 시각과 맞추기 위함

## 매칭 (출처: `docs/domain/matching.md` 10장)

- 2026-10-17 [기획] MD1 우선순위 반영 방식: 가중치 내림차순 합산(사전식 비교 아님) — 운영자가 가중치 조정 가능
- 2026-10-17 [기획] MD2 연령: 8살은 필수, 그 안에서 밀집도 가산 — PRD MAT-03·04 통합 해석
- 2026-10-17 [기획] MD3 MBTI 궁합: 축별 균형(E/I 가중) — PRD 미정의. 회사 기준 받으면 S3만 교체
- 2026-10-17 [기획] MD4 성비: 동수(홀수면 1명 차)를 최선 — PRD 미정의
- 2026-10-17 [기획] MD5 최소 점수: 55 — 시연하며 조정
- 2026-10-17 [기획] MD6 선택지 목록: matching.md 4장 표 — CTO 자료 화면 기준
- 2026-10-17 [기획] MD7 회차 선호: 선택 순서를 우선순위로 사용(`application_slot_choices.priority`) — 매칭 명세 결정
- 2026-10-17 [기획] MD8 미배치 + 남은 선택 회차 있음: `WAITING` 복귀 — states.md M4a 추가
- 2026-10-17 [기획] MD9 알고리즘: 연령 그룹 → 균등 분리 → 교환 기반 로컬 서치 — 결정적, 수백 명 규모에서 충분
- 2026-10-17 [CC] 매칭 선택지 enum(`Gender`, `Trait`, `Interest`, `ConversationStyle`, `Mbti`)은 `com.siktak.profile.domain`에 둔다 — 프로필 속성이고, 매칭 엔진은 이를 읽기만 한다
- 2026-10-17 [CC] 로컬 서치(5-3 5번)는 테이블 쌍을 순서대로 돌며 쌍마다 점수 합이 가장 크게 오르는 교환을 적용하고, 한 바퀴 동안 개선이 없거나 교환 500회면 끝낸다 — 매 반복 전체 최선 1개 방식은 한 연령 그룹 300명에 59초, 이 방식은 1.6초(600명 5.6초)이고 평균 점수 차이 없음
- 2026-10-17 [CC] 선호 점수는 항목별 정수 개수를 센 뒤 한 번만 나눈다(S5 포함) — 멤버 순서와 무관하게 같은 값이 나와 결정성(E23)을 보장
- 2026-10-17 [CC] 엔진 v2는 `TableMatcher` 포트를 구현하지 않고 `MatchingService.runForSlot`에서 `EngineMatchingService`로 분기한다 — 포트가 회차 하나·신청 ID만 다뤄 여러 회차 재배치·사유·실패를 담을 수 없음. 스케줄러·운영자 버튼 진입점은 그대로
- 2026-10-17 [CC] v2 실행 대상 = 호출 회차 + 매칭 시각이 도래한 미매칭 회차 전부, run_key = `engine-v1:slots-{대상 회차 ID 해시}`. 이미 매칭된 회차로 부르면 그 회차를 포함한 실행을 돌려준다 — matching.md 5-0, 스케줄러가 회차마다 불러도 한 번만 실행(E20)
- 2026-10-17 [CC] 엔진 후보의 선택 회차에서 이미 지난(매칭 끝·시각 경과) 회차는 뺀다 — 그래야 J2에서 user14가 "대기"가 아니라 "실패"로 판단됨
- 2026-10-17 [CC] 미배치 대기(deferred)는 M4 없이 M4a만, 실패는 M4 → M6(+1회권 P5). `respond_by` = 1순위 회차 행사 시작 시각 — 4.4 지시서 문구 그대로, D6 "원래 행사일"
- 2026-10-17 [CC] 프로필(출생연도·성별) 없는 신청은 `DATA_MISSING` 예외로 남기고 WAITING 유지, 계정 제한(H2)은 `SAFETY_REPORT` 예외, 미결제는 로그만 — matching.md 5-1
- 2026-10-17 [CC] 배치 로그 decision에 `DEFERRED`·`EXCLUDED` 추가, `from_slot_id`·`to_slot_id`·`cause_code` 컬럼 추가(V3). 임시 테이블은 DB ID가 없어 `to_table_id`는 확정 로그에만 — 엔진 로그를 손실 없이 저장
- 2026-10-17 [CC] 식당 미배정 테이블에도 첫 배정 시 예약명을 저장하고 재배정 때 그대로 쓴다 — 4.2 PM 답 2번

## 개발 환경·구조

- 2026-10-17 [CC] 로컬 개발 DB는 Neon 개발 브랜치를 사용(로컬 Postgres·Docker 미사용) — 개발 PC에 Docker·Postgres가 없고, CLAUDE.md의 운영/개발 브랜치 분리 방침과 일치. DB가 필요한 통합 테스트 방식은 첫 DB 기능 작업 때 결정
- 2026-10-17 [CC] Gradle 빌드 스크립트는 Groovy DSL — Spring Initializr 기본값, 참고 자료가 가장 많음
- 2026-10-17 [CC] Spring Boot 4.1.1 사용 — 세팅 시점 start.spring.io 최신 안정판
- 2026-10-17 [기획] DB 통합 테스트: CI는 GitHub Actions Postgres 서비스 컨테이너, 로컬은 Neon dev 브랜치, @Tag("db")로 구분 — 로컬 Docker 없음, Testcontainers 대신 CI 기본 기능 사용
- 2026-10-17 [기획] Neon은 scale-to-zero 유지. 헬스체크 DB 분리, 스케줄러는 메모리 일정 기반으로 DB 폴링 금지, 커넥션 풀 최소 유지 0 — 무료 사용량 절약, Neon 재기동 1초 내외로 지장 없음
- 2026-10-17 [기획] Neon 접속: DB_URL은 직접 연결(non-pooled) 주소, 운영 브랜치 이름은 `production` — Flyway 안정성, Neon 기본 브랜치명
- 2026-10-17 [CC] 헬스체크 분리: `/api/v1/health`는 DB 미조회 liveness(Render Health Check Path), `/api/v1/health/db`는 `SELECT 1` 결과를 DB 장애에도 HTTP 200 + `db:"DOWN"`으로 반환 — Neon 절전 유지, DB 장애가 재시작 루프로 번지지 않게
- 2026-10-17 [CC] [임시] 인증은 요청 헤더 `X-User-Id`로 사용자를 식별(비밀번호 없음), `/api/v1/dev/users`로 테스트 계정 목록 제공 — 2.3 워킹 스켈레톤용. 3.1에서 JWT(Authorization 헤더)로 교체하고 dev API 제거
- 2026-10-17 [CC] 상태 전이표는 `Transition` enum 하나로 states.md 전체(A1~A5, P1~P12·P1a·P7a, M1~M12·M4a·M8a, T1~T5)를 정의. 전이 주체가 표와 다르면 거부, 이미 목표 상태면 상태는 그대로 두고 `[no-op]` 사유로 이력만 기록 — states.md 0장 3·5항
- 2026-10-17 [CC] [임시] 2.3 매칭은 운영자 버튼으로 실행하지만 M2 이력의 actor_type은 표대로 `SCHEDULER`, 누른 운영자는 actor_id와 reason에 남김 — 스케줄러(2.4·4.5) 도입 전 대체 트리거
- 2026-10-17 [CC] [임시] `SimpleSequentialMatcher`는 미배치자의 상태를 바꾸지 않고 WAITING으로 둔다(match_runs.summary에만 기록) — 재배치·대안 제시(M4~M6)는 4.x·6.x
- 2026-10-17 [CC] [임시] 피드백은 참석 체크인 없이 매칭 CONFIRMED 신청이면 제출 가능 — 참석(T1·T2)은 7.x
- 2026-10-17 [CC] 매칭기는 `TableMatcher` 인터페이스 뒤에 둔다. 매칭 run_key = `slot-{회차ID}:{알고리즘 버전}`로 같은 회차·같은 엔진은 한 번만 실행 — V2 엔진 교체 시 버전이 달라지므로 새 실행이 가능
- 2026-10-17 [CC] V2 마이그레이션: `dining_tables.venue_id` FK는 venues 생성 시(V3 이후) 추가, `feedbacks.rejoin_intent`·`recommend_score`는 DB에서 nullable(FDB-03 입력은 7.x에서 필수화), 모든 테이블에 created_at·updated_at(status_histories 포함) — ERD 공통 규칙
- 2026-10-17 [CC] `Profile` JPA 엔티티는 2.3에서 만들지 않고 시드는 SQL로 넣음 — V2가 `profile.domain`에 선택지 enum을 만들고 있어 충돌 방지. 3.x 프로필 작업에서 추가
- 2026-10-17 [CC] [임시] 개발용 시드 러너(`DevSeedRunner`, 지역 3·운영자 1·참가자 6·회차 1, 없을 때만 삽입)를 기본 켬(`SIKTAK_DEV_SEED_ENABLED`, 배포 환경 포함) — 배포 환경에서도 2.3 흐름 확인. 2.4 정식 시드로 대체
- 2026-10-17 [CC] Hikari `minimum-idle=0`, `idle-timeout=60s`, `keepalive-time=0`, `connection-timeout=5s` — 놀고 있는 커넥션이 Neon을 깨워두지 않게, DB 재기동·장애 시 요청이 30초씩 묶이지 않게
