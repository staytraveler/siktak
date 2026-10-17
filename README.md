# 우연한 식탁 (siktak)

처음 보는 4~6명을 성향이 맞는 한 테이블로 연결하는 소셜다이닝 서비스 '우연한 식탁'의 운영 자동화 MVP.

신청 → 결제 → 자동 매칭 → 자동 재배치 → 자동 확정 → 식당·단체방 안내 → 참석 → 피드백 → 다음 매칭까지,
**정상 흐름은 운영자 개입 없이 끝나고 운영자는 자동 해결되지 않은 예외만 처리한다.**

- 참가자 앱: `/app` (모바일 우선)
- 운영자 웹: `/admin`

## 기술 스택

| 영역 | 스택 | 배포 |
|---|---|---|
| Backend | Java 21, Spring Boot 4, Spring Data JPA, Flyway, Spring Security, ShedLock, springdoc-openapi | Render |
| Frontend | React, TypeScript, Vite, React Router, TanStack Query, axios, Tailwind CSS | Vercel |
| DB | PostgreSQL | Neon |

## 배포

```
GitHub main push
   ├─ GitHub Actions ── backend 테스트(Postgres 서비스 컨테이너) + frontend lint·build   (배포 안 함)
   ├─ Vercel ────────── frontend/  →  https://siktak-rho.vercel.app        (/app, /admin)
   └─ Render ────────── backend/Dockerfile → https://siktak.onrender.com  (/api/v1/**, /swagger-ui.html)
                              │
                              └─ Neon PostgreSQL (Singapore, production 브랜치, 쓸 때만 깨움)
```

| 대상 | URL |
|---|---|
| 참가자 앱 | https://siktak-rho.vercel.app/app |
| 운영자 웹 | https://siktak-rho.vercel.app/admin |
| API·Swagger | https://siktak.onrender.com/swagger-ui.html |

환경변수 목록과 설정 방법: [docs/integrations.md](docs/integrations.md#환경변수)

## 폴더 구조

```
siktak/
├── backend/    # Spring Boot (com.siktak.{도메인}/api·application·domain·infra)
├── frontend/   # Vite + React (/app 참가자, /admin 운영자)
├── docs/       # PRD, 도메인 명세, ERD, 결정 로그, 진행 기록
└── CLAUDE.md   # 개발 규칙
```

## 로컬 실행

### 사전 준비

- JDK 21, Node.js 20 이상
- 루트 `.env.example`을 참고해 환경변수를 준비한다 (DB는 Neon 개발 브랜치).

### Backend

```bash
cd backend
# 환경변수 DB_URL, DB_USERNAME, DB_PASSWORD 설정 후
./gradlew bootRun          # 기본 프로필: local
./gradlew test             # 테스트
```

- 헬스체크: `GET http://localhost:8080/api/v1/health` (DB 확인: `/api/v1/health/db`)
- DB 통합 테스트: `TEST_DB_URL`·`TEST_DB_USERNAME`·`TEST_DB_PASSWORD`(Neon dev)를 설정하면 `@Tag("db")` 테스트도 실행된다
- API 문서: `http://localhost:8080/swagger-ui.html`

### Frontend

```bash
cd frontend
npm install
# frontend/.env.local 에 VITE_API_BASE_URL=http://localhost:8080
npm run dev                # http://localhost:5173
```

## 문서

| 문서 | 내용 |
|---|---|
| [docs/prd.md](docs/prd.md) | PRD v0.2 원문 |
| [docs/prd-analysis.md](docs/prd-analysis.md) | PRD·심사 기준 분석 |
| [docs/domain/states.md](docs/domain/states.md) | 상태 전이 명세 |
| [docs/domain/matching.md](docs/domain/matching.md) | 매칭 알고리즘 명세 |
| [docs/erd.md](docs/erd.md) | 데이터 모델 |
| [docs/demo-scenario.md](docs/demo-scenario.md) | 시연 시나리오·시드 데이터 |
| [docs/integrations.md](docs/integrations.md) | 외부 연동 |
| [docs/decisions.md](docs/decisions.md) | 결정 로그 |
| [docs/progress.md](docs/progress.md) | 진행 기록 |
