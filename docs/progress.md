# 진행 기록 (progress.md)

> 형식(CLAUDE.md 11장): 세션 종료 시 아래 형식으로 추가한다. 최신 기록을 위에 둔다.
>
> ```
> ## YYYY-MM-DD HH:MM
> - 완료: (PRD ID 포함)
> - 변경: (명세와 달라진 점)
> - 이슈: (막힌 것, 확인 필요)
> - 다음:
> ```

## 2026-10-21 (V1) 10.1 시연 문서·계정 정리
- 완료: 8.2 배포 확인(API·Vercel 라우트 전부 200), README.md 심사위원용 재작성, docs/demo-guide.md 신규(12단계 진행표)
- 변경: 계정 표는 시드(`DemoSeedData`)와 일치 확인. Swagger는 `/swagger-ui.html`(→ `/swagger-ui/index.html` 200)
- 이슈: (1) production DB의 S1~S3가 이미 `MATCHED` — 시연 전 시드 초기화 필요(운영 DB라 손대지 않음). (2) dev DB 회차 #345·식당 #253 개별 삭제는 권한 분류기가 거부해 미실행(SQL은 10.1 완료 보고). (3) 운영자 대시보드·예외 처리함 경로는 8.1 완료 후 demo-guide에 확정 필요
- 다음: PM 확인 → 10.x 통합 검증(J2 이후 화면 경로 확인)

## 2026-10-18 20:00 (V1, 8.2)
- 완료: 8.2 운영자 관리 — 회차 수정(매칭 시각 재등록), 식당 CRUD·회차별 수용량(`/admin/venues`), 매칭 가중치·최소 점수(MAT-15, `/admin/settings`), 신청자 목록·CSV 2종(PRD 16장, `/admin/applicants`), 피드백 요약·신고 검토(`/admin/feedback`), 피드백 제출 시 NEXT_SLOT_RECOMMEND 알림(RET-02)
  - 백엔드 API 11개(`/api/v1/admin/...`), 프론트 공통 `AdminLayout`(좌측 메뉴) + 페이지 4개 신규·회차 페이지 개편
  - 검증: 비DB 전체 테스트 통과(신규 6건), 프론트 build·lint 통과, 로컬(Neon dev) 기동 후 curl로 설정 저장·검증·회차 수정·식당/수용량·CSV·신청자·피드백 요약 확인
- 변경: V2 영역 `MatchingDataReader.number()` 4줄(설정 숫자가 `{"value":n}` 객체여도 읽음). 명세 변경 없음
- 이슈: `AdminLayout`은 8.1(V2) 레이아웃이 main에 없어 임시로 만든 것 — 병합 시 한쪽으로 통합 필요. 검증 중 Neon dev에 테스트 회차 #345(성수 12/20)·식당 #253이 남음(시드 초기화 시 사라짐)
- 다음: PM 검토 → 배포 확인

## 2026-10-18 19:30 (V1, main)
- 완료: 7.2 SAF-01·02, RET-01·02 — 신고·차단(즉시 분리 + 예외 SAFETY_REPORT), 운영자 조치(DISMISS/WARNING/RESTRICTION/PERMANENT_BAN), 참가 이력, 다음 일정 추천. 배포 환경 curl 검증 완료
- 변경: 대상 지정 applicationId+companionId, RESTRICTION 자동 해제 생략 (decisions.md)
- 이슈: 신고 사유 enum PRD 확인 필요, 병합본 DB 테스트 락 경합으로 미완
- 다음: PM 검토

## 2026-10-18 (V2, `feat/matching-engine`) 5.2 모임 안내 자동화
- 완료: INF-01·03·05·07·08, CON-01~03 — 알림 저장·발송·재시도·예외 전환, 상황별 템플릿 12종(V7), 단체방 공지·큐레이션·정책, 리마인드(`REMINDER_D1`)·콘텐츠(`CONTENT_POST`) 예약 게시, 알림함·콘텐츠 API·화면, `FEEDBACK_REQUEST`를 7.1 `EventEndedEvent`에 연결
  - 검증: 비DB 전체 통과, `GuidanceAutomationIntegrationTest` 5건(DB) 통과, 프론트 build·lint 통과
- 변경: 알림 이벤트는 동기 `@EventListener`(지시서의 AFTER_COMMIT과 다름). decisions.md [CC] 참조
- 이슈: 배포 환경 J1→J3→J4 확인 미실시, `NEXT_SLOT_RECOMMEND` 발송 계기 미구현
- 다음: PM 검토

## 2026-10-18 (7.1)
- 완료: 7.1 FDB-01~04, MAT-06 — 참석(V6 `attendances`, 확정 시 T1)·체크인(T2)·행사 종료 작업 `EVENT_END`(노쇼 후보 표시·예외 처리함·만남 이력·`EventEndedEvent`)·피드백 확장(재참여·추천, ATTENDED만)·사람별 선호(비공개, 동석자 이름만)·데모 버튼 3종·화면(체크인·피드백·선호·데모 도구)
  - 검증: 7.1 DB 통합 테스트(시드 → J1 → 체크인 → J5 → 피드백·AVOID → 2회차 → J6, AVOID 쌍 다른 테이블) 통과, 전체 `./gradlew test` 통과(DB 포함), 프론트 build 통과
- 변경: `ExceptionCase` 종류 `NO_SHOW_CANDIDATE` 추가(erd.md 반영), Flyway `out-of-order: true`, V2 영역 `EngineMatchingService`·`MatchingService`에 이벤트 발행 한 줄씩. decisions.md [CC] 7건. 기존 테스트 2건 수정(RefundConcurrency 프로필 누락 — 3.2 이후 깨져 있었음, FlowSkeleton 피드백 조건)
- 이슈: Neon 왕복이 느려 DB 통합 테스트가 클래스당 5~10분, 전체 약 1시간. V2와 동시에 돌리면 TRUNCATE 락·시드 삭제로 서로 실패시킴
- 다음: PM 검토 → 8.x 운영자 참석 관리(T5 확정) 화면

## 2026-10-17 17:40 (3.1)
- 완료: 3.1 4.4 통합·배포 검증 + 로그인·권한 — ACC-01(테스트 계정 로그인), PRD 16장(운영자 권한)
  - `feat/matching-engine` main 머지(`7a0c399`), 식당·콘텐츠 SeedContributor, `Application.recordMatchFailReason`
  - JWT 로그인(`6fc1d58`): `/api/v1/auth/login`·`/me`, X-User-Id·dev/users 제거, admin API OPERATOR 전용, `/login` + 테스트 계정 버튼
  - 검증: 테스트 124건(DB 포함) 통과, 배포 환경에서 401·403·200·CORS, 시드 초기화 → J1 → J2 결과가 demo-scenario 4장과 일치
- 변경: decisions.md [CC] 4건(인증 방식, JWT 실제 시각, 테스트 계정 하드코딩, DB 테스트 격리). V2 테스트 파일 격리 한 줄 수정
- 이슈: 브라우저 버튼 클릭은 사용자 확인 필요. 머지 직후 DB 테스트 2건이 낡은 기대값·격리 부족으로 실패 → 수정
- 다음: PM 다음 지시 (3.2 프로필·신청)

## 2026-10-17 17:05 (V2, `feat/matching-engine`) 6.1 매칭 실패 해결
- 완료: STA-04~06, PAY-02·03 — main(2.4) 머지 후 일정 이동(M7)·이용권 보관(M8)·자동 환불(P6~P12)·미응답 보관(M8a)·중복 환불 방지, 참가자 선택 API·화면, 운영자 환불 재처리·수동 완료 API
  - 검증: main(3.1) 머지 후 전체 `./gradlew test` 128건 통과(DB 포함, public), 프론트 build·lint 통과
- 변경: P7b 전이 추가(사용자 확인). decisions.md [CC] 8건. V1 영역 최소 수정(`Transition`, `Application.transferOf`, 테스트 기대값 2건)
- 이슈: PM 확인 5건 모두 승인(17:55). V5 유지(V4 비움). `v2work` 스키마 삭제. DB 테스트 한 번에 5~9분
- 다음: 기능별 커밋 → main(3.1) 머지 → main 머지·push 완료. 다음 지시 5.2

## 2026-10-17 16:13 (V2, `feat/matching-engine`) 4.4 매칭 운영 연결
- 완료: MAT-07·08·10·11·12·13, INF-02, STA-03·04 — main 머지(`181f466`) 후 기능별 커밋·push
  - `d1d3b37` V3 스키마(식당·수용량·콘텐츠·대체 일정 해결·예외 큐·차단·사람별 선호·만남 이력, 배치 로그 컬럼), `exception` 패키지, 식당 배정 서비스
  - `bd124c0` 엔진 v2 파이프라인(`EngineMatchingService`): 도래 회차 일괄 실행, 저장·상태 전이·대체 일정·단체방·식당 배정, `siktak.matching.engine=v2|simple`
  - `c79688a` 조회 API(실행·배치 로그·테이블·예외·참가자 매칭 근거), 식당 재배정 API, `DemoVenueSeed`·`ConversationContentSeeder`
  - 검증: DB 통합 테스트(Neon dev, 별도 스키마) J1·J2·시드 3건 통과 — demo-scenario 4장 J1·J2 결과와 일치, E20 재실행 중복 없음. 비DB 전체 테스트 통과
- 변경: decisions.md [CC] 7건. ERD 대비 배치 로그 decision에 DEFERRED·EXCLUDED, from/to_slot_id·cause_code 추가
- 이슈: `applications.match_fail_reason`은 쓰기 메서드가 없어 [임시] SQL로 갱신(V1에 도메인 메서드 요청). 시드 연결·프로필 가격대는 V1 시드에 요청
- 다음: PM 검토 → main 머지 지시 대기

## 2026-10-17 16:13 (2.4)
- 완료: 2.4 데모 시계·시간 기반 작업 실행기·demo-v1 시드·/admin/demo — PRD 18장 시연 전반, MAT-01(시각 기반 자동 실행)
  - V2 4.1·4.2 main 머지, 2.2 URL 반영·완료 보고
  - DemoClock(offset 메모리) + DemoClockService(변경 시만 settings 저장, 기동 시 1회 적재)
  - ScheduledJobRegistry(메모리) + ScheduledJobRunner(1분 틱, 도래 작업만 ShedLock, 기동 catch-up, 재시도 3회) + MatchRunJobHandler, 회차 생성 시 등록
  - DemoSeedService(TRUNCATE → offset 0 → 지역 3·회차 5·사용자 31·프로필·신청 27 → SeedContributor) + 빈 DB 자동 투입, DevSeedRunner 제거
  - 데모 API·화면(J1~J6, +1시간, +1일, 시드 초기화)
  - 검증: 테스트 115건 통과, 로컬 Neon dev·배포 Render 모두 시드 초기화 → J1 → S1·S2 자동 매칭 확인
- 변경: decisions.md [CC] 7건(데모 시계, 실행기, ShedLock 트랜잭션 매니저, 락 시각, 시드 규칙, SeedContributor, 테스트 프로필). V2 영역 MatchingService 최소 수정 1건(사용자 승인)
- 이슈: ShedLock 기본 설정이 바깥 트랜잭션을 커밋하는 버그 발견·수정(테스트 데이터가 Neon dev에 커밋됐었음). 시드 초기화가 느림(로컬 35초, Render 21초). 가짜 매칭이라 연령 조건 미적용(4.4에서 해결)
- 다음: PM 다음 지시. V2 4.4 머지 후 배포 시드 재초기화

## 2026-10-17 (V2, `feat/matching-engine`) 4.2 순수 도메인 로직
- 완료: DB 없는 계산부 4개, 기능별 커밋·push
  - INF-02 식당 자동 배정 `com.siktak.venue.domain.assign` (`e1a8574`)
  - CON-01·02 대화 주제 선택 `com.siktak.content.domain` + 시드 `seed/conversation-contents.json` 28개 (`9ea8257`)
  - STA-04·05 대체 일정 추천 `com.siktak.matching.domain.alternative` (`9646e7d`)
  - matching.md 6장 확정 후 취소 재배치 `MatchingEngine.reallocateAfterCancel` (E21·E22) (`4e4923e`)
  - 4.1 PM 확정 반영: `[해석]` 주석 → matching.md 11장 I번호, 신규 사유 코드 문구, demo-scenario 3-3 성향 열 (`fc63359`에 포함)
  - 테스트: 4.2 신규 29개, 전체 `./gradlew test` 72개 통과
- 변경: 명세 변경 없음. 회차 상태는 V1의 `SlotStatus`와 겹치지 않게 `open` boolean으로 받는다
- 이슈: 가격대 코드 값이 정의되지 않음(문자열 일치로 비교) — 4.2 완료 보고 "PM 확인 필요"
- 다음: PM 검토 → V1 흐름(2.3)에 연결

## 2026-10-17 14:07 (2.3)
- 완료: 2.3 워킹 스켈레톤 — APP-01~03, PAY-01(모의), STA-01, MAT-01(가짜, 버튼 실행), INF-04(단체방+시스템 메시지), FDB-02
  - 공통 에러 응답·`@RestControllerAdvice`(401·403 포함), 임시 `X-User-Id` 인증, Clock 기반 감사 시각
  - Flyway V2: ERD 18개 테이블·제약·부분 유니크
  - 상태 전이표(`Transition`) + `StatusTransitionService` + status_histories, 사용 전이 A1·A2·P1·P2·M1·M2·M3
  - `TableMatcher` 포트 + `SimpleSequentialMatcher`, run_key 멱등, 단체방·시스템 메시지 멱등, 피드백 1회
  - 프론트 `/app`, `/app/applications/:id`, `/admin`, `/admin/slots`
  - 검증: 테스트 30건 통과(DB 통합 2건 Neon dev), 로컬 API 흐름 수동 확인, 2.2 DB 테스트도 Neon dev에서 통과
- 변경: decisions.md [CC] 9건(임시 인증, 전이 no-op·주체 검사, M2 주체, 미배치 WAITING 유지, 피드백 조건, run_key, V2 스키마 세부, Profile 엔티티 보류, 시드 러너)
- 이슈: 브라우저 클릭 검증은 사용자 확인 필요. 배포 환경 확인은 Render·Vercel 연결 대기. Windows 셸 curl은 한글 본문을 UTF-8로 안 보내 테스트 시 주의(서버 문제 아님)
- 다음: 2.2 마무리(URL 반영·CORS·완료 조건) → 다음 지시서

## 2026-10-17 13:56 (V2, `feat/matching-engine`)
- 완료: 4.1 매칭 엔진 코어 — MAT-02~12. `com.siktak.matching.domain.engine`(순수 함수, H1~H7, S1~S6, 절차 5-1~5-8, 사유 코드 7-2), 선택지 enum `com.siktak.profile.domain`
  - 테스트 41개: E1~E19·E23·E24 엣지 케이스 24개, 점수 단위 10개, 시드 J1 시나리오 7개. 전체 `./gradlew test` 43개 통과
  - 시드 J1 결과가 demo-scenario.md 4장과 일치 (S2 6인, user15 → S1 2단계 재배치, user14 DEFERRED, user16 제외, S1 5·5·4, user05/06 분리)
- 변경: 명세 변경 없음. `[해석]` 9건은 4.1 완료 보고 "PM 확인 필요"에 정리. 구현 결정 3건 decisions.md [CC]
- 이슈: 커밋·push는 사용자 승인 대기 (전역 규칙: 커밋·푸시는 사용자가 말할 때만)
- 다음: PM 검토 → 4.4(MatchRun 기록·상태 전이)에서 엔진 연결

## 2026-10-17 13:45 (2.2 중간)
- 완료: 2.2 배포 파이프라인 코드·문서(B1~B6), 병렬 작업 준비(4.1 지시서 커밋, `../siktak-matching` worktree, gradle.properties)
  - 헬스체크 분리 `/api/v1/health`(liveness) · `/api/v1/health/db`, Hikari 절전 설정, Dockerfile, `vercel.json`, CI 워크플로, `DatabaseMigrationTest`(@Tag db)
  - 검증: 로컬 백엔드 테스트 4건 통과(DB 1건 skip), 프론트 build·lint 통과, `main` push `2743a33`
- 변경: PM 13:40 "DB 절전 원칙" 반영 — 헬스체크 DB 분리, 커넥션 풀 최소 0. 스케줄러 메모리 일정 방식은 원칙만 문서화(integrations.md), 구현은 2.4·4.5·5.x
- 이슈: 로컬 Docker 없어 `docker build` 미검증, GitHub Actions 결과는 레포 비공개·`gh` 없음으로 미확인
- 다음: 사용자 Neon `.env`·Render·Vercel 설정 → URL 반영·CORS → 2.2 완료 조건 확인

## 2026-10-17 13:30
- 완료: 레포·프로젝트 초기 세팅 (기능 구현·배포 없음)
  - docs: `prd.md`(PDF 텍스트화, 기능 ID 58개 대조 완료), `decisions.md`(D1~D8, MD1~MD9 이관), `integrations.md` 뼈대, `progress.md`
  - 루트: `.gitignore`, `.env.example`, `README.md`
  - backend: Spring Boot 4.1.1 / Java 21 / Gradle(Groovy). 도메인 패키지 14개 골격(`package-info.java`), `Clock` 빈(Asia/Seoul), 최소 `SecurityConfig`(stateless, CORS 환경변수), ShedLock 설정 + `V1__init.sql`(shedlock 테이블), `GET /api/v1/health`, `application.yml`/`-local`/`-prod`
  - frontend: Vite + React + TypeScript, React Router·TanStack Query·axios·Tailwind CSS v4, `/app`·`/admin` 빈 페이지, `VITE_API_BASE_URL`
  - 검증: `./gradlew build` 통과(테스트 2건), `npm run build`·`npm run lint` 통과
- 변경: 명세 변경 없음. 개발 환경 결정 3건을 `decisions.md`에 [CC]로 기록(로컬 DB = Neon 개발 브랜치, Gradle Groovy, Boot 4.1.1)
- 이슈:
  - 개발 PC에 Docker·로컬 Postgres가 없다. 앱을 로컬에서 띄우거나 DB 통합 테스트를 하려면 Neon 개발 브랜치 접속 정보가 필요하다. 현재 테스트는 DB 없이 도는 `@WebMvcTest`뿐이다
  - `pdftotext`로는 PRD 한글이 깨져서, 킥오프 때 받은 동일 PRD(12쪽) 텍스트로 옮기고 기능 ID·상태 enum·수치를 PDF에서 추출한 값과 대조했다
  - `vite-plugin-pwa`, OpenAPI 타입 생성(`src/api/generated/`), `vercel.json` SPA rewrite, CI는 이번 범위 밖이라 아직 없다
- 다음: Neon 개발 브랜치 연결과 로컬 기동 확인 → 첫 도메인 작업(ERD 기반 V2 마이그레이션, 상태 전이 + audit log)
