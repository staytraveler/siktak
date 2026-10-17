# V → PM

> 작성: 2026-10-17 · V클로드 (2.2 진행 중 — 사용자 계정 작업 대기. 2.2 완료 시 덮어씀)

## 결과 요약
- 병렬 작업 준비 완료
  - 4.1 지시서·V2 핸드오프·tasks README 커밋 + push (`756cf48`)
  - `backend/gradle.properties`(`-Xmx1g`, workers 2) 커밋 (`27e7844`) 후 worktree 생성: `../siktak-matching` = `feat/matching-engine` (main `27e7844` 기준)
  - `com.siktak.matching`은 건드리지 않음
- 2.2 B1~B6 코드·문서 완료, `main` push (`2743a33`). 로컬 백엔드 테스트 4건 통과(DB 테스트 1건은 TEST_DB_URL 없어 skip), 프론트 build·lint 통과
  - B1 `backend/Dockerfile`(의존성 레이어 캐시, 테스트 제외, temurin 21 JRE, SerialGC·RAM 75%, non-root), `.dockerignore`, `gradlew` 실행 권한(100755) git에 기록
  - B2 DB 절전 원칙 반영: `/api/v1/health`(DB 미조회 liveness), `/api/v1/health/db`(SELECT 1, 장애 시 200+DOWN). Hikari `minimum-idle=0`·`idle-timeout=60s`·`keepalive-time=0`·`connection-timeout=5s`. `@Scheduled` 틱 새로 만들지 않음
  - B4 `frontend/vercel.json` rewrite, `/app`에 서버·DB·서버 시각 표시
  - B5 `.github/workflows/ci.yml`(backend: postgres:17 서비스 + `./gradlew test` / frontend: npm ci·lint·build), `test` 프로필, `DatabaseMigrationTest`(`@Tag("db")`, TEST_DB_URL 없으면 skip)
  - B6 decisions(PM [기획] 3건 + [CC] 2건), integrations(Neon·Render·Vercel·환경변수·DB 절전 원칙 표), README 배포 구조, CLAUDE.md 6장 한 줄 추가
- 미검증: 로컬 Docker 없음 → `docker build`는 첫 Render 배포로 확인. GitHub Actions 결과는 레포 비공개·`gh` 미설치로 V가 확인 불가 → 사용자 확인 필요

## 블레이버스 체크할 태스크
- [ ] Neon DB 생성 및 Flyway 연결 — 코드 완료, Render 배포 후 production에 테이블 생성 확인 필요
- [ ] Render 백엔드 배포 및 환경변수 설정 — 사용자 작업 대기
- [ ] Vercel 프론트 배포 및 CORS·라우팅 설정 — 사용자 작업 대기
- [ ] GitHub Actions 테스트 자동화 — 워크플로 push 완료, 첫 실행 결과 확인 대기

## PM 확인 필요
- 없음 (13:40 지시 모두 반영). 참고: `.vscode/settings.json`이 작업 트리에 새로 생겼는데 사용자 IDE 설정으로 보여 커밋하지 않았다.

## 사용자 작업 요청
1. **GitHub Actions 확인**: GitHub → `siktak` → Actions에서 `2743a33` 실행의 backend·frontend 잡이 초록인지 확인. backend 잡 로그에서 `DatabaseMigrationTest`가 skip이 아닌 통과인지 확인(실패 시 로그 전달).
2. **Neon** (A1)
   - 두 브랜치(`production`, `dev`) 모두 Connect에서 **Connection pooling 끈 주소**를 복사
   - 로컬 `siktak/.env` 생성 후 dev 값 입력: `DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require`, `DB_USERNAME`, `DB_PASSWORD`, 그리고 같은 값으로 `TEST_DB_URL`·`TEST_DB_USERNAME`·`TEST_DB_PASSWORD`, `JWT_SECRET`(32바이트 이상 랜덤). 값은 V에게 보내지 말고 파일에만.
3. **Render** (A2): New → Web Service → `siktak` 레포, Root Directory `backend`, Runtime **Docker**, Region **Singapore**, Instance **Starter 이상**, Branch `main`, Auto-Deploy On, Health Check Path **`/api/v1/health`**
   - Environment: `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`(**production** 브랜치), `JWT_SECRET`(로컬과 다른 값), `SPRING_PROFILES_ACTIVE=prod`, `CORS_ALLOWED_ORIGINS`는 Vercel URL 받기 전이면 임시로 `http://localhost:5173`
   - 배포 후 **서비스 URL** 전달
4. **Vercel** (A3): New Project → `siktak` 레포, Root Directory `frontend`, Framework Vite, Env `VITE_API_BASE_URL`=Render URL → 배포 후 **프론트 URL** 전달
5. URL 두 개를 받으면 V가 README·문서 반영하고, Render `CORS_ALLOWED_ORIGINS`에 Vercel URL을 넣도록 안내 → 완료 조건 확인
