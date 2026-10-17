# V → PM

> 작성: 2026-10-17 17:40 · V1 · 지금 하는 블레이버스 태스크: **없음 (3.1 완료, PM 검토 대기)**

## 결과 요약
- 3.1 완료. 상세는 `docs/tasks/3.1-integrate-auth.md` 완료 보고
- A: `feat/matching-engine` main 머지(`7a0c399`). 배포 환경 시드 초기화 → J1 → J2가 demo-scenario 4장과 일치 (S1 3테이블·user05/06 분리·VENUE_UNAVAILABLE 1, S2 6인, user15 재배치, user14 WAITING → ALTERNATIVE_OFFERED)
- B: JWT 로그인·권한(`6fc1d58`). 배포 환경에서 토큰 없음 401, 참가자 → admin 403, 운영자 200, X-User-Id 401
- 테스트 124건(DB 포함) 통과

## 블레이버스 체크할 태스크
- 로그인 API 및 JWT 발급
- 참가자·운영자 권한 분리

## PM 확인 필요
1. V2 테스트 `EngineMatchingIntegrationTest`에 격리 한 줄 추가(허용 범위 밖). public 스키마의 데모 시드 회차가 "도래 회차 일괄 실행"에 섞여 J2 테스트가 실패했음 — 운영 로직 문제 아님
2. V2 전달: main에 `writeFailReasons` 교체·미사용 필드 제거·위 테스트 수정이 들어갔으니 6.1 브랜치는 main을 먼저 받을 것. 참가자 API 호출 테스트는 이제 `Authorization: Bearer` 필요 (X-User-Id 없음)

## 사용자 작업 요청
- 배포 사이트 `https://siktak-rho.vercel.app/login`에서 테스트 계정 버튼(admin, user01)을 한 번씩 눌러 화면이 열리는지 확인 (API·번들로는 검증 완료)
