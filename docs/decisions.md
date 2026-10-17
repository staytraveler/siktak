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
- 2026-10-17 [CC] 데모 시계 `DemoClock` = 실제 시각 + offset(메모리). offset은 바꿀 때만 `settings.demo.clock-offset`에 저장, 기동 시 1회 적재. **앞으로만 이동**(과거로 되돌리면 이미 실행된 작업과 어긋남 — 처음으로는 시드 초기화) — 2.4 지시서 1장, DB 절전 원칙
- 2026-10-17 [CC] 시간 기반 작업 실행기: `ScheduledJobHandler`(jobType·loadPending·execute) 빈을 추가하면 자동 연결. 틱은 메모리 목록만 비교, 도래 작업만 ShedLock(`{jobType}:{targetId}`) 후 실행, 실패 시 1분 뒤 재시도 3회 후 목록 제외(로그). 예외 큐(`exception_cases`) 연결은 V2 exception 패키지 이후 — 2.4 지시서 2장
- 2026-10-17 [CC] ShedLock `JdbcTemplateLockProvider`에 앱의 JPA 트랜잭션 매니저를 지정 — 미지정 시 ShedLock 자체 `DataSourceTransactionManager`가 바깥 JPA 트랜잭션의 커넥션을 커밋해버림(테스트 롤백 실패로 발견). 운영 경로(틱·데모 버튼)는 바깥 트랜잭션이 없어 영향 없었음
- 2026-10-17 [CC] ShedLock 락 시각은 ShedLock 자체 시계(실제 시각, DB 시간 사용) — 데모 offset과 무관한 인프라 시각이므로 Clock 빈 규칙의 예외
- 2026-10-17 [CC] demo-v1 시드: 신청·결제는 실제 서비스(`ApplicationService`)로 만들어 상태 이력이 함께 남는다. 멤버십 사용자(user03·07·21·29)는 3.4 전까지 1회권 결제 완료. 차단 관계(user05→user06, REPORT)는 `user_blocks` 테이블(V2 V3)이 있을 때만 삽입. 표에 없는 값(user23~29·demo01 MBTI·관심사·성향, 전원 직업·가격대·대화 스타일)은 [임의] — demo-scenario 8장 허용
- 2026-10-17 [CC] 시드 확장 지점 `SeedContributor`(order·contribute(SeedContext)) — V2가 식당·수용량·콘텐츠 시드를 붙인다. `SeedContext`로 회차 코드(S1~S5)·로그인 ID·지역명 → ID 조회
- 2026-10-17 [CC] test 프로필은 `siktak.scheduler.enabled=false`·`siktak.demo.auto-seed=false` — 테스트가 실행기를 직접 만들어 제어. Neon dev에 V2 전용 스키마(`v2work`)가 있으므로 테스트의 information_schema 조회는 `current_schema()`로 한정
- 2026-10-17 [CC] Hikari `minimum-idle=0`, `idle-timeout=60s`, `keepalive-time=0`, `connection-timeout=5s` — 놀고 있는 커넥션이 Neon을 깨워두지 않게, DB 재기동·장애 시 요청이 30초씩 묶이지 않게
