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
