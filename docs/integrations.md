# 외부 연동 (integrations.md)

> 원칙(CLAUDE.md 8장): 모든 외부 연동은 인터페이스(포트) 뒤에 두고 Mock 구현을 항상 함께 만든다.
> 구현 선택은 설정값으로 전환하며, 데모 직전 불안정하면 설정 하나로 Mock으로 돌린다.

## 요약

| 연동 | 포트 | 설정 키 | 구현 | 상태 |
|---|---|---|---|---|
| 결제·환불 | `PaymentGateway` | `siktak.payment.provider` | `mock` / `toss` | 미구현 |
| 알림 | `NotificationSender` | `siktak.notification.channel` | `in-app` | 미구현 |

| 인프라 | 용도 | 상태 |
|---|---|---|
| Neon | PostgreSQL (production: 운영, dev: 로컬 개발), 쓸 때만 깨움 | 운영 중 |
| Render | 백엔드 (Docker) `https://siktak.onrender.com` | 운영 중 (Free + 외부 핑) |
| Vercel | 프론트엔드 `https://siktak-rho.vercel.app` | 운영 중 |
| GitHub Actions | 테스트 (배포는 하지 않음) | `.github/workflows/ci.yml` |

---

## 환경변수

| 키 | 위치 | 설명 |
|---|---|---|
| `DB_URL` | Render, 로컬 | `jdbc:postgresql://<host>/<db>?sslmode=require` (Neon 직접 연결 주소) |
| `DB_USERNAME` | Render, 로컬 | Neon 역할 이름 |
| `DB_PASSWORD` | Render, 로컬 | Neon 역할 비밀번호 |
| `JWT_SECRET` | Render, 로컬 | 32바이트 이상 랜덤 문자열 |
| `CORS_ALLOWED_ORIGINS` | Render | Vercel 프론트 URL (쉼표 구분) |
| `SPRING_PROFILES_ACTIVE` | Render | `prod` |
| `VITE_API_BASE_URL` | Vercel, 로컬 | Render 백엔드 URL |
| `TEST_DB_URL`·`TEST_DB_USERNAME`·`TEST_DB_PASSWORD` | 로컬, CI | DB 통합 테스트(`@Tag("db")`)용. 비우면 DB 테스트를 건너뛴다 |

키 목록은 루트 `.env.example`에도 있다. 실제 값은 커밋하지 않는다.

---

## 1. Neon (PostgreSQL)

- 프로젝트: `siktak`, 리전 AWS Asia Pacific (Singapore)
- 브랜치
  - `production`(기본 브랜치): 운영. Render가 접속한다.
  - `dev`: 로컬 개발과 로컬 DB 테스트용.
- 스키마는 Flyway가 앱 기동 시 자동으로 만든다 (`backend/src/main/resources/db/migration`).

### 접속 주소: 직접 연결(non-pooled)을 쓴다
- Neon 대시보드 → Connect → **Connection pooling 끄기** 상태의 주소를 쓴다. 호스트에 `-pooler`가 없는 주소다.
- 이유: Flyway 마이그레이션은 풀러(PgBouncer 트랜잭션 모드)보다 직접 연결에서 안정적이다. 앱 커넥션 수는 Hikari 기본값(10)으로 충분하다.
- JDBC 형식으로 바꾸는 법

  ```
  Neon:  postgresql://<user>:<password>@<host>/<db>?sslmode=require
  DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require
  DB_USERNAME=<user>
  DB_PASSWORD=<password>
  ```

  앞에 `jdbc:`를 붙이고, URI 안의 `<user>:<password>@`는 빼서 별도 키로 넣는다. `channel_binding` 같은 다른 쿼리 파라미터는 빼도 된다.

### DB 절전 원칙 (2026-10-17 사용자·PM 결정)
Render 서버는 상시 가동하지만 **Neon DB는 실제 작업이 있을 때만 깨운다.** Neon은 5분 무활동 시 절전하고 재기동은 1초 내외다. 무료 사용량(월 100 CU-hours)을 아끼는 게 목적이다.

| 항목 | 규칙 | 상태 |
|---|---|---|
| 헬스체크 | `/api/v1/health`는 DB 미조회(Render Health Check Path). DB 확인은 `/api/v1/health/db`로만 | 구현 (2.2) |
| 커넥션 풀 | Hikari `minimum-idle=0`, `idle-timeout=60s`, `keepalive-time=0` | 구현 (2.2) |
| 스케줄러 | DB를 폴링하지 않는다. 다음 실행 시각은 메모리에 들고 1분 틱은 메모리 시각만 비교, 도래했을 때만 DB 접근. 기동 시 1회 적재, 회차·예약 알림 생성·수정 시와 데모 시계 이동 시 갱신 | 원칙만 (2.4·4.5·5.x에서 구현) |
| ShedLock | 작업이 실제로 실행될 때만 락을 잡는다 (틱마다 잡지 않음) | 원칙만 |
| 알림 재시도 | 생성 시 즉시 발송, 실패 시 메모리 일정에 다음 시도 시각을 올린다. DB 폴링 워커 금지 | 원칙만 |

- **사용량 확인 위치**: Neon 대시보드 → 프로젝트 → Monitoring(또는 Billing의 Usage)에서 Compute hours.
- 이 원칙이 깨졌는지 보려면 Monitoring에서 아무 요청이 없는 시간대에 compute가 Idle(suspended)로 내려가는지 확인한다.

## 2. Render (백엔드)

- 서비스: `https://siktak.onrender.com` — Web Service, Runtime **Docker**, Root Directory `backend`, Region **Singapore**
- 인스턴스: **Free** (2026-10-17 사용자 결정). Free는 15분 무요청 시 잠들므로 **cron-job.org가 10분마다 `/api/v1/health`를 호출**해 깨워 둔다. 이 경로는 DB를 조회하지 않아 Neon 절전에는 영향이 없다. 잠들었다 깨면 메모리 일정(시간 기반 작업 실행기)은 기동 시 DB에서 다시 적재하고 지난 작업은 catch-up 실행한다
  - CLAUDE.md 4장의 "Starter 이상(Free 금지)"와 다르다 → PM 확인 필요
- 배포: `main` 푸시 시 자동 배포 (Auto-Deploy On). Actions에서 배포하지 않는다.
- Health Check Path: `/api/v1/health` (DB를 조회하지 않는 liveness — Neon을 깨우지 않는다)
  - DB 상태는 `/api/v1/health/db`로 확인한다. DB가 내려가도 HTTP 200 + `db: "DOWN"`이라 DB 장애가 인스턴스 재시작 루프로 번지지 않는다.
- 이미지: `backend/Dockerfile` (빌드 temurin 21 JDK → 실행 temurin 21 JRE, 테스트 제외, `-XX:MaxRAMPercentage=75 -XX:+UseSerialGC`, 포트는 Render가 주는 `PORT`)
- 환경변수: 위 표의 `DB_*`(Neon **production**), `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `SPRING_PROFILES_ACTIVE=prod`
- 바꾸는 법: Render 대시보드 → 서비스 → Environment → 값 수정 → Save (저장 시 재배포)
- 확인: `https://<render-url>/api/v1/health`, `https://<render-url>/api/v1/health/db`, `https://<render-url>/swagger-ui.html`

## 3. Vercel (프론트엔드)

- 프로젝트: `https://siktak-rho.vercel.app` — GitHub `siktak` 레포 import, Root Directory `frontend`, Framework Vite
- 배포: `main` 푸시 시 자동 배포
- SPA 라우팅: `frontend/vercel.json`의 rewrite로 모든 경로를 `index.html`로 보낸다 (`/admin` 새로고침 404 방지)
- 환경변수: `VITE_API_BASE_URL` = Render 서비스 URL. **빌드 시점에 주입되므로 값을 바꾸면 재배포해야 한다.**
- 바꾸는 법: Vercel 대시보드 → 프로젝트 → Settings → Environment Variables → 수정 후 Deployments에서 Redeploy
- 프론트 URL이 정해지면 Render의 `CORS_ALLOWED_ORIGINS`에 넣는다.

## 4. Mock 결제

- 포트: `PaymentGateway`
- 설정: `siktak.payment.provider=mock`
- 용도: 로컬 개발, 테스트, 데모 기본값. PRD PAY-01·PAY-03은 해커톤에서 모의 결제·모의 환불을 허용한다.
- 동작: (구현 시 작성) 승인·거절·환불 성공·환불 실패를 재현하는 방법
- 멱등성: (구현 시 작성) 멱등 키 처리 방식

## 5. 토스페이먼츠 (테스트 모드)

- 포트: `PaymentGateway`
- 설정: `siktak.payment.provider=toss`
- 환경변수: `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY` (테스트 키, 값은 `.env`에만)
- 관련 PRD: PAY-04(P1 실결제 연동)
- 사용 API: (구현 시 작성) 결제 승인, 결제 취소(전체·부분)
- 실패 처리: (구현 시 작성) 재시도 정책, `ExceptionCase` 전환 조건
- Mock 전환 방법: `siktak.payment.provider=mock`으로 변경 후 재배포

## 6. 앱 내 알림

- 포트: `NotificationSender`
- 설정: `siktak.notification.channel=in-app`
- 용도: PRD 14장 알림 시나리오, INF-01·INF-07·INF-08
- 동작: (구현 시 작성) 저장 방식, 단체방 시스템 메시지와의 관계
- 재시도: `policy.notification-retry-max`(기본 3) 후 반복 실패만 `ExceptionCase` 전환
- 이후 확장: 웹 푸시, 알림톡 (MVP 범위 밖)
