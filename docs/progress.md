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

## 2026-10-17 13:56 (V2, `feat/matching-engine`)
- 완료: 4.1 매칭 엔진 코어 — MAT-02~12. `com.siktak.matching.domain.engine`(순수 함수, H1~H7, S1~S6, 절차 5-1~5-8, 사유 코드 7-2), 선택지 enum `com.siktak.profile.domain`
  - 테스트 41개: E1~E19·E23·E24 엣지 케이스 24개, 점수 단위 10개, 시드 J1 시나리오 7개. 전체 `./gradlew test` 43개 통과
  - 시드 J1 결과가 demo-scenario.md 4장과 일치 (S2 6인, user15 → S1 2단계 재배치, user14 DEFERRED, user16 제외, S1 5·5·4, user05/06 분리)
- 변경: 명세 변경 없음. `[해석]` 9건은 4.1 완료 보고 "PM 확인 필요"에 정리. 구현 결정 3건 decisions.md [CC]
- 이슈: 커밋·push는 사용자 승인 대기 (전역 규칙: 커밋·푸시는 사용자가 말할 때만)
- 다음: PM 검토 → 4.4(MatchRun 기록·상태 전이)에서 엔진 연결

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
