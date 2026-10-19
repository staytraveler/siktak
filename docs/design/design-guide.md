# 디자인 가이드 — 참가자 앱 (감성 매거진 v3)

> 작성: 2026-10-19 · PM클로드 · 사용자 컨펌 완료 (3차 시안)
> 시안 원본: `docs/design/mockup/Main.html`(홈) · `Table.html`(오늘의 테이블) · `Resolve.html`(매칭 실패 해결) — 브라우저로 열어서 볼 것

## 1. 톤 (2026-10-20 개정: 둥근 에디토리얼)
기준 화면: `docs/design/reference/ref-home.png`. 크림 종이 바탕 + **둥근 카드**(14~16px) + 얇은 연한 테두리 + 아주 옅은 그림자 + 머스터드 히어로. 버튼은 10~12px 둥근 모서리, 칩·상태는 완전 pill. 이전 v3의 "둥근 모서리 없음·굵은 먹선·오프셋 그림자"는 **폐기**.

## 2. 토큰 (Tailwind v4 `@theme`로 등록)
| 이름 | 값 | 용도 |
|---|---|---|
| paper | #F7F1E6 | 앱 배경 |
| card | #FBF7F0 | 카드·입력 배경 |
| ink | #1C1A17 | 글자·주 버튼 |
| line | #E8DFD2 | 카드·구분선 (1px) |
| muted | #6B6258 | 보조 글자 |
| body | #4A443D | 본문 회색 |
| accent | #7E2A27 | 브랜드 와인 (날짜 블록, 링크, 활성 탭) |
| mustard | #EBB035 | 히어로 배경 (이미지 좌측 픽셀과 동일) |
| wine-soft / gold-soft / sage-soft | #F3E1DF / #F6E8C6 / #E3EAD9 | 상태 pill 연한 배경 |
| sage · sky · coral · cream | 기존 유지 | 아바타·대화 주제·배지 |

- 그림자 `shadow-print` = `0 2px 10px rgba(80,55,25,.08)` (이름은 유지, 값만 옅게).
- 참가자 아바타 색: mustard → sage → sky → coral → cream 순환.
- 카드 = `rounded-2xl border border-line bg-card`, 입력·작은 박스 = `rounded-xl border border-line`.

## 3. 글꼴 (Google Fonts, `index.html`에서 로드)
- 제목: **Noto Serif KR** 900 (자간 -1px 안팎)
- 본문: **Noto Sans KR** 400/500/700
- 영문 장식·숫자: **Playfair Display** 900 / italic 400 — 날짜·시각 큰 숫자, "Your table is ready." 같은 영문 한 줄
- 작은 라벨: 10~11px, letter-spacing 1.5~2px, 대문자 영문 또는 짧은 한글

## 4. 공통 컴포넌트 (`frontend/src/components/ui/`)
- `AppHeader` — 왼쪽 "우연한 식탁"(세리프), 오른쪽 원형 프로필 아이콘 버튼. (매스트헤드 호수 표기 제거)
- `HeroBlock` — 둥근 머스터드 카드, `homeHero` 이미지를 배경(`object-cover object-right`), 왼쪽에 영문 라벨 + 세리프 제목 + `HeroDesc`/`HeroDate`(아이콘 날짜). 상태별로 문구만 바뀜.
- `LinkButton`/`PrimaryButton` — 먹색 꽉 찬 둥근 버튼 (높이 52), `SecondaryButton` — card 바탕 + ink 1px 테두리
- `SlotTicket` — 왼쪽 와인색 날짜 블록(일·월·요일) + 지역(세리프) + 설명 + `Pill` 줄 + `>`. 선택 시 accent 링 + 순번
- `Pill` — 아이콘 달린 상태 pill (wine·gold·sage·plain), `StatusBadges`가 사용
- `TicketCard` — 확정 후 DATE/TIME/PLACE 3칸 점선 구분
- `SectionHeader` — 영문 이탤릭 제목 + 오른쪽 "전체 보기 →"(accent) + 아래 1px 선
- `BottomNav` — lucide 라인 아이콘 + 라벨, 활성은 와인색 + 아래 점
- `Sticker`(원형 D-n 배지), `Chip`(pill), `Avatar`, `PageTitle`(AppHeader + 영문 라벨 + 제목), `BackHeader`(와인 하단 둥근 헤더)
- 아이콘은 `lucide-react` 라인 아이콘 (strokeWidth 1.6)

## 5. 이미지 교체 여지 (사용자 요청 — 필수)
지금 그림은 CSS/SVG로 그린 임시 일러스트다. **나중에 사용자가 준비한 이미지 파일로 갈아끼울 수 있어야 한다.**
- 모든 일러스트는 `frontend/public/illustrations/` 안의 **별도 파일**로 둔다. 컴포넌트 안에 SVG를 직접 박지 않는다.
  - `home-table.svg` (홈 히어로: 위에서 본 테이블)
  - `resolve-table.svg` (매칭 실패: 빈 테이블과 시계)
  - `empty-state.svg` (빈 화면 공용)
  - `venue-placeholder.svg` (식당 사진 자리)
- 경로는 `frontend/src/design/assets.ts` **한 곳**에서만 관리: `export const ILLUSTRATIONS = { homeTable: '/illustrations/home-table.svg', ... }`
- `<Illustration name="homeTable" alt="" />` 컴포넌트 하나로 렌더 (`<img>`, `object-fit: cover`, 영역 크기는 부모가 정함). 파일이 없거나 로드 실패하면 영역을 paper 색으로 비워 둔다.
- 교체 방법: 같은 이름 파일을 덮어쓰거나(png/jpg면 `assets.ts` 확장자만 수정). `docs/design/README` 대신 이 문서 5장에 교체 방법을 유지한다.
- 식당 사진은 이후 API의 `venue.imageUrl`이 생기면 그걸 우선 쓰고, 없으면 `venuePlaceholder`.

## 6. 화면 적용 범위
참가자 앱 `/app/*` 전부 + 로그인. 운영자 `/admin/*`는 글꼴·색 토큰만 맞추고 레이아웃은 그대로 (데스크톱 표 위주라 과한 장식 금지).
