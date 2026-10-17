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

## 개발 환경·구조

- 2026-10-17 [CC] 로컬 개발 DB는 Neon 개발 브랜치를 사용(로컬 Postgres·Docker 미사용) — 개발 PC에 Docker·Postgres가 없고, CLAUDE.md의 운영/개발 브랜치 분리 방침과 일치. DB가 필요한 통합 테스트 방식은 첫 DB 기능 작업 때 결정
- 2026-10-17 [CC] Gradle 빌드 스크립트는 Groovy DSL — Spring Initializr 기본값, 참고 자료가 가장 많음
- 2026-10-17 [CC] Spring Boot 4.1.1 사용 — 세팅 시점 start.spring.io 최신 안정판
- 2026-10-17 [기획] DB 통합 테스트: CI는 GitHub Actions Postgres 서비스 컨테이너, 로컬은 Neon dev 브랜치, @Tag("db")로 구분 — 로컬 Docker 없음, Testcontainers 대신 CI 기본 기능 사용
- 2026-10-17 [기획] Neon은 scale-to-zero 유지. 헬스체크 DB 분리, 스케줄러는 메모리 일정 기반으로 DB 폴링 금지, 커넥션 풀 최소 유지 0 — 무료 사용량 절약, Neon 재기동 1초 내외로 지장 없음
- 2026-10-17 [기획] Neon 접속: DB_URL은 직접 연결(non-pooled) 주소, 운영 브랜치 이름은 `production` — Flyway 안정성, Neon 기본 브랜치명
- 2026-10-17 [CC] 헬스체크 분리: `/api/v1/health`는 DB 미조회 liveness(Render Health Check Path), `/api/v1/health/db`는 `SELECT 1` 결과를 DB 장애에도 HTTP 200 + `db:"DOWN"`으로 반환 — Neon 절전 유지, DB 장애가 재시작 루프로 번지지 않게
- 2026-10-17 [CC] Hikari `minimum-idle=0`, `idle-timeout=60s`, `keepalive-time=0`, `connection-timeout=5s` — 놀고 있는 커넥션이 Neon을 깨워두지 않게, DB 재기동·장애 시 요청이 30초씩 묶이지 않게
