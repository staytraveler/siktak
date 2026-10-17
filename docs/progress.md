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

## 2026-10-17 16:13 (V2, `feat/matching-engine`) 4.4 매칭 운영 연결
- 완료: MAT-07·08·10·11·12·13, INF-02, STA-03·04 — main 머지(`181f466`) 후 기능별 커밋·push
  - `d1d3b37` V3 스키마(식당·수용량·콘텐츠·대체 일정 해결·예외 큐·차단·사람별 선호·만남 이력, 배치 로그 컬럼), `exception` 패키지, 식당 배정 서비스
  - `bd124c0` 엔진 v2 파이프라인(`EngineMatchingService`): 도래 회차 일괄 실행, 저장·상태 전이·대체 일정·단체방·식당 배정, `siktak.matching.engine=v2|simple`
  - `c79688a` 조회 API(실행·배치 로그·테이블·예외·참가자 매칭 근거), 식당 재배정 API, `DemoVenueSeed`·`ConversationContentSeeder`
  - 검증: DB 통합 테스트(Neon dev, 별도 스키마) J1·J2·시드 3건 통과 — demo-scenario 4장 J1·J2 결과와 일치, E20 재실행 중복 없음. 비DB 전체 테스트 통과
- 변경: decisions.md [CC] 7건. ERD 대비 배치 로그 decision에 DEFERRED·EXCLUDED, from/to_slot_id·cause_code 추가
- 이슈: `applications.match_fail_reason`은 쓰기 메서드가 없어 [임시] SQL로 갱신(V1에 도메인 메서드 요청). 시드 연결·프로필 가격대는 V1 시드에 요청
- 다음: PM 검토 → main 머지 지시 대기

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
