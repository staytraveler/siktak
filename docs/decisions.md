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
- 2026-10-17 [CC] 데모 시계 `DemoClock` = 실제 시각 + offset(메모리). offset은 바꿀 때만 `settings.demo.clock-offset`에 저장, 기동 시 1회 적재. **앞으로만 이동**(과거로 되돌리면 이미 실행된 작업과 어긋남 — 처음으로는 시드 초기화) — 2.4 지시서 1장, DB 절전 원칙
- 2026-10-17 [CC] 시간 기반 작업 실행기: `ScheduledJobHandler`(jobType·loadPending·execute) 빈을 추가하면 자동 연결. 틱은 메모리 목록만 비교, 도래 작업만 ShedLock(`{jobType}:{targetId}`) 후 실행, 실패 시 1분 뒤 재시도 3회 후 목록 제외(로그). 예외 큐(`exception_cases`) 연결은 V2 exception 패키지 이후 — 2.4 지시서 2장
- 2026-10-17 [CC] ShedLock `JdbcTemplateLockProvider`에 앱의 JPA 트랜잭션 매니저를 지정 — 미지정 시 ShedLock 자체 `DataSourceTransactionManager`가 바깥 JPA 트랜잭션의 커넥션을 커밋해버림(테스트 롤백 실패로 발견). 운영 경로(틱·데모 버튼)는 바깥 트랜잭션이 없어 영향 없었음
- 2026-10-17 [CC] ShedLock 락 시각은 ShedLock 자체 시계(실제 시각, DB 시간 사용) — 데모 offset과 무관한 인프라 시각이므로 Clock 빈 규칙의 예외
- 2026-10-17 [CC] demo-v1 시드: 신청·결제는 실제 서비스(`ApplicationService`)로 만들어 상태 이력이 함께 남는다. 멤버십 사용자(user03·07·21·29)는 3.4 전까지 1회권 결제 완료. 차단 관계(user05→user06, REPORT)는 `user_blocks`(V3)에 삽입 (3.1에서 테이블 존재 검사 제거). 표에 없는 값(user23~29·demo01 MBTI·관심사·성향, 전원 직업·가격대·대화 스타일)은 [임의] — demo-scenario 8장 허용
- 2026-10-17 [CC] 시드 확장 지점 `SeedContributor`(order·contribute(SeedContext)) — V2가 식당·수용량·콘텐츠 시드를 붙인다. `SeedContext`로 회차 코드(S1~S5)·로그인 ID·지역명 → ID 조회
- 2026-10-17 [CC] test 프로필은 `siktak.scheduler.enabled=false`·`siktak.demo.auto-seed=false` — 테스트가 실행기를 직접 만들어 제어. Neon dev에 V2 전용 스키마(`v2work`)가 있으므로 테스트의 information_schema 조회는 `current_schema()`로 한정
- 2026-10-17 [CC] Hikari `minimum-idle=0`, `idle-timeout=60s`, `keepalive-time=0`, `connection-timeout=5s` — 놀고 있는 커넥션이 Neon을 깨워두지 않게, DB 재기동·장애 시 요청이 30초씩 묶이지 않게
- 2026-10-17 [CC] 6.1 결제 전이 P7b 추가: `REFUND_AVAILABLE → PAID`, 주체 SCHEDULER (미응답 자동 보관 M8a에 연쇄) — P7은 PARTICIPANT 전용이라 스케줄러가 쓸 수 없고, 그대로 두면 states.md 6장 불변 조건이 깨진다. 사용자 확인
- 2026-10-17 [CC] 6.1 일정 이동(M7): 새 신청은 `Application.transferOf`로 만들고 A1·A2(PARTICIPANT) → P1a(SYSTEM, 기존 결제 공유) → M1. 이동할 회차는 추천 목록(`offered_slot_ids`) 안에서만, 모집 중이 아니거나 같은 회차에 진행 중 신청이 있으면 409
- 2026-10-17 [CC] 6.1 응답 기한(`respond_by`)이 지나면 참가자 선택은 409, 미응답 작업(`RESOLUTION_TIMEOUT`, 대상 `match_resolutions.id`)이 보관 처리. 같은 선택 재요청은 no-op, 다른 선택은 409
- 2026-10-17 [CC] 6.1 자동 환불: 환불 키 `refund-pay-{paymentId}`(결제당 1개), 결제 행 `SELECT ... FOR UPDATE` 뒤 PG 호출, 이미 취소된 거래면 PG 재호출 없음. 선택 트랜잭션 커밋 뒤 별도 트랜잭션으로 P8~P9. 동시 선택은 신청 낙관적 락으로 하나만 반영(나머지 409)
- 2026-10-17 [CC] 6.1 `policy.refund-retry-max`(=`siktak.policy.refund-retry-max`, 기본 3)는 첫 시도 뒤 재시도 횟수 — PG를 최대 4번 부르고 실패하면 P10 + `ExceptionCase(REFUND_FAILED)`. 재시도는 작업 실행기 `REFUND_RETRY`(1분 뒤), 기동 시 `REFUND_PROCESSING` 신청은 즉시 catch-up
- 2026-10-17 [CC] 6.1 운영자 재처리(P11)는 PG 1회 시도, 실패 시 다시 P10(예외는 열린 채). 수동 완료(P12)는 사유 필수, `payments.tx_status`는 PG 사실대로 `CANCEL_FAILED` 유지
- 2026-10-17 [CC] 6.1 1회권 크레딧은 `user_passes(SINGLE_CREDIT, source_application_id)`, V5 부분 유니크로 신청당 1개. 크레딧으로 낸 신청의 "크레딧 복원"은 새 크레딧 발급으로 처리. 환불 선택은 1회권 결제(`REFUND_AVAILABLE`)만
- 2026-10-17 [CC] 모의 결제 취소 실패 주입 `siktak.payment.mock.fail-cancel`(환경변수 `SIKTAK_PAYMENT_MOCK_FAIL_CANCEL`) — 환불 실패·예외 처리함 시연용
- 2026-10-17 [CC] 3.1 인증: Spring `oauth2-resource-server`(Nimbus) HS256 JWT. Access 토큰만(24시간, Refresh 없음), sub = users.id. 역할·계정 상태는 요청마다 DB에서 다시 읽어 BANNED·삭제 계정은 토큰이 유효해도 401. 로그인 시 BANNED는 403 `ACCOUNT_BANNED`, RESTRICTED는 로그인 허용(매칭만 제한). `JWT_SECRET` 미설정 시 로컬·테스트는 기동마다 임의 키, prod는 필수(32바이트 이상)
- 2026-10-17 [CC] JWT 발급·만료 판단은 데모 시계가 아니라 실제 시각 — 데모 도구로 시계를 며칠 옮겨도 로그인이 풀리지 않게. Clock 빈 규칙의 예외(ShedLock 락 시각과 같은 인프라 시각)
- 2026-10-17 [CC] 테스트 계정 버튼 목록(admin·demo01·user01·user14·user23)과 비밀번호는 프론트에 하드코딩 — demo-scenario 6장에 공개된 테스트 전용 값. 목록 API는 만들지 않음. 실서비스 전환 시 제거
- 2026-10-17 [CC] DB 통합 테스트는 public 스키마에 커밋된 데모 시드와 함께 돈다 — 엔진 테스트는 시작 시 기존 OPEN·CLOSED 회차를 MATCHED로 바꿔(테스트 트랜잭션 롤백) "도래 회차 일괄 실행" 대상에서 뺀다
- 2026-10-18 [CC] 5.2 알림 이벤트는 `@TransactionalEventListener(AFTER_COMMIT)`이 아니라 **동기 `@EventListener`(상태를 바꾼 트랜잭션 안)**로 처리 — 알림이 상태 변경과 함께 저장·롤백되고(커밋 후 실패로 알림만 유실되는 일 없음), 롤백되는 DB 통합 테스트에서도 검증된다. 알림 저장은 DB 쓰기뿐이고 발송 실패는 예외를 던지지 않고 재시도로 넘기므로 상태 전이를 막지 않는다. 지시서 3항과 다른 점
- 2026-10-18 [CC] 5.2 상태 전이 알림의 단일 계기: `StatusTransitionService.apply`가 상태가 실제로 바뀐 뒤 `ApplicationTransitioned`를 발행(V1 서비스 수정은 이 한 곳). 매핑 A2→신청완료, P1→결제대기, P1a·P2→결제완료, M4·M10→재배치중, P6·P7a→환불요청, P9·P12→환불완료. 알림 키 `{템플릿}:app-{신청ID}`로 반복 전이에도 1건
- 2026-10-18 [CC] 5.2 매칭 확정 알림(MATCH_CONFIRMED)은 전이(M3·M5) 시점이 아니라 식당 배정 뒤 `TableGuidanceRequested`로 보낸다 — 문구에 식당이 들어가야 해서. 매칭 실패(MATCH_FAILED)는 사유·대체 일정 기록 뒤 `MatchFailureRecorded`로(M6 시점엔 사유가 비어 있음). 일정 이동으로 생긴 새 신청은 신청완료·결제완료 대신 `TRANSFERRED`(새 신청의 M1) 한 건만
- 2026-10-18 [CC] 5.2 `policy.notification-retry-max`(기본 3)는 6.1 환불과 같이 첫 시도 뒤 재시도 횟수(총 4번, 1분 간격). 실패 시 `FAILED` + `exception_cases(NOTIFICATION_FAILED, notification-{id})`. `NOTIFICATION_SEND` 작업은 기동 시 PENDING 알림을 `scheduled_at`으로 적재
- 2026-10-18 [CC] 5.2 단체방 안내 멱등 키: `table-{id}:guide:venue|guide:pending|curation|policy|reminder-d1|content`. 식당 미배정이면 `guide:pending`("확정 후 안내")을 올리고, 운영자 재배정(`TableVenueService.assignVenue`) 때 `guide:venue`를 추가 게시. 확정 알림은 재배정 때 다시 보내지 않음
- 2026-10-18 [CC] 5.2 [해석] 집합 시간 = 모임 시작 시각(PRD에 별도 규칙 없음). 취소 정책 문구는 prd.md 63행(진행 2일 전 환불 불가, 당일 취소·단체방 퇴장 불가, 노쇼) 그대로. 큐레이션은 연령대(출생연도 범위)·성비·공통 관심사 집계만 — 이름·점수·개인 선호 없음
- 2026-10-18 [CC] 5.2 `notification_templates`는 참조 데이터라 데모 초기화(TRUNCATE) 대상에서 제외(`KEEP_TABLES`) — 마이그레이션 V7이 넣은 12종이 초기화 뒤에도 남는다
- 2026-10-18 [CC] 7.1 참석(`attendances`, V6): 매칭 확정 시 `TableMemberConfirmedEvent`(EngineMatchingService가 발행, 동기 `@EventListener`)로 `SCHEDULED` 생성(T1) — 같은 트랜잭션이라 확정과 참석 생성이 함께 커밋·롤백된다. 참석 상태 이력은 `status_histories(target_type=ATTENDANCE)`에 남기고 전이표 검사는 `AttendanceService`가 한다 (신청 축 서비스와 분리)
- 2026-10-18 [CC] 7.1 체크인(T2)은 `policy.checkin-open-minutes`(`siktak.policy.checkin-open-minutes`, 기본 30)분 전 ~ 행사 종료까지, 밖이면 409. 이미 체크인했으면 같은 결과(멱등). 참석 취소(T3·T4)는 이번 범위 밖
- 2026-10-18 [CC] 7.1 행사 종료 작업 `EVENT_END`(대상 = 회차 ID, 시각 = `ends_at`): 회차 상태 `MATCHED → DONE`으로 처리 완료를 표시해 멱등(재실행·catch-up 안전). 별도 컬럼은 만들지 않았다. 기동 시 `MATCHED` 회차를 다시 읽어 지난 것은 즉시 실행
- 2026-10-18 [CC] 7.1 노쇼 후보는 `attendances.no_show_candidate=true`로만 표시(상태는 `SCHEDULED` 유지, T5는 운영자) + 예외 처리함에 `ExceptionCase(NO_SHOW_CANDIDATE)` 신규 종류로 올린다 — demo-scenario 12단계 "예외 처리함에는 식당 부족(처리 완료)과 노쇼 후보만 존재"를 충족하려고 `case_type`에 값 추가(erd.md 반영). PRD 12장 상태 enum은 건드리지 않음
- 2026-10-18 [CC] 7.1 만남 이력(`user_encounters`)은 같은 테이블 `ATTENDED` 쌍마다 `insert ... on conflict do nothing`, `met_at` = 행사 시작 시각. 종료 이벤트 `EventEndedEvent(slotId, attendedUserIds, noShowCandidateUserIds)` 발행(V2 피드백 요청 알림이 사용)
- 2026-10-18 [CC] 7.1 피드백은 `ATTENDED`만 제출(아니면 409), 재참여 의향 1~5·추천 0~10 필수. 사람별 선호는 별도 `PUT /me/applications/{id}/person-preferences`(전체 교체, 멱등) — 동석자는 `table_members.id`(companionId)와 이름만 노출해 다른 참가자의 사용자 ID를 내보내지 않는다. 조회는 본인이 쓴 것만
- 2026-10-18 [CC] Flyway `out-of-order: true` — V1(짝수)·V2(홀수) 번호 규칙 때문에 V2가 먼저 적용한 V7 뒤에 V6이 들어온다. 공유 Neon dev DB·배포 DB 모두 필요
- 2026-10-18 [CC] 7.2 차단·신고 API는 지시서의 `userId` 대신 `applicationId` + `companionId`(table_members.id, 7.1 동석자 API와 동일)로 대상을 가리킨다 — 다른 참가자의 사용자 ID를 응답·요청에 내보내지 않기 위해(CLAUDE.md 6장 개인정보). 같은 테이블 동석자가 아니면 400(`INVALID_REQUEST`)
- 2026-10-18 [CC] 7.2 신고는 (신고자, 피신고자, 테이블)당 1건(유니크 인덱스)이고 재전송 시 기존 접수를 돌려준다. 접수 즉시 `user_blocks(REPORT)` + `ExceptionCase(SAFETY_REPORT, key=SAFETY_REPORT:report-{id})`. 신고 사유 enum(HARASSMENT 등 5개)은 PRD에 목록이 없어 임의로 정함 — PM 확인 필요
- 2026-10-18 [CC] 7.2 운영자 조치 `resolve`: DISMISS→신고 DISMISSED, 그 외→RESOLVED + `user_sanctions` 1건(신고당 1건 부분 유니크). RESTRICTION은 `days`≥1 필수, 계정 RESTRICTED(`ends_at`=조치 시각+days). PERMANENT_BAN→BANNED. 이미 닫힌 신고는 재호출해도 무변화. RESTRICTION 기간 만료 자동 해제는 구현하지 않음
- 2026-10-18 [CC] 7.2 참가 이력은 종료(DONE)된 회차의 참석 기록, 추천은 모집 중(`OPEN`·신청 가능) 회차에서 이미 신청한 회차를 뺀 뒤 ①체크인한 지역 ②같은 요일+시간대(오전<12·오후<17·저녁) ③시작 순으로 최대 3개 — 시간대 구간은 해석
