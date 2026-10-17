# PM → V

> 작성: 2026-10-17 14:18 · PM클로드

## 2.2 · 2.3 검토: 승인 👍
- 2.3 훌륭해. 상태 전이표 전체 + 감사 로그 + 멱등까지 깔아둔 게 이후 작업의 기반이 된다.
- **2.2 배포 완료**: 백엔드 `https://siktak.onrender.com`(Free, db UP), 프론트 `https://siktak-rho.vercel.app`(/admin 404 없음), CORS 반영 완료. 사용자가 cron-job.org로 10분 핑 설정 중. README·`integrations.md`에 URL 반영하고 2.2 완료 보고 마무리해줘. GitHub Actions 결과는 사용자 확인 후 전달.
- PM 확인 필요 답변은 2.4 지시서 5장에 있다.

## 다음 작업 (바로 시작)
**`docs/tasks/2.4-demo-clock-seed.md`** — 데모 시계, 시간 기반 작업 실행기, demo-v1 시드, `/admin/demo`. 0번(V2 4.1 머지)부터.
- 시작할 때와 블레이버스 태스크가 바뀔 때마다 `v-to-pm.md` 맨 위 "작성" 줄에 **지금 하는 블레이버스 태스크 이름**을 적어줘. 사용자가 타이머를 바꾼다.
- 끝나면 완료 보고·`v-to-pm.md` 쓰고 "PM에게 전달해주세요".

## 추가 (14:25) — V2와 영역 분담 (꼭 지켜줘)
- V2가 지금 **4.4(엔진 운영 연결)** 를 시작했다. 너의 2.4 0번에서 `feat/matching-engine`을 main에 머지하면 V2의 4.1·4.2가 같이 들어온다.
- **마이그레이션 번호: V2가 `V3__`, 너는 다음부터 `V4__` 이후.**
- 영역: 너 = `common`, `slot`, `application`, `payment`, `chat`, 시드·데모·스케줄러 / V2 = `matching`, `venue`, `content`, `exception`. 상대 영역 파일은 수정하지 말고 필요하면 `v-to-pm.md`로 요청.
- V2가 demo-v1 시드에 **식당·수용량·대화 콘텐츠**를 붙일 예정이다. 시드 구조에 확장 지점(예: `SeedContributor` 인터페이스)을 만들어 두면 V2가 거기에 붙인다.
- 가격대 코드 확정: `UNDER_20K`, `20K_40K`, `OVER_40K` (matching.md 4장). demo-v1 프로필 `price_range`에 적당히 분배해 넣어줘.
- `TableMatcher` 포트·`StatusTransitionService`·`ScheduledJobRegistry` 의 시그니처를 바꿔야 하면 V2에 영향이 가니 먼저 `v-to-pm.md`로 알려줘.
