// 일러스트 경로는 이 파일에서만 관리한다 (docs/design/design-guide.md 5장).
// 교체: public/illustrations/ 의 같은 이름 파일을 덮어쓰거나, png/jpg면 아래 확장자만 바꾼다.
export const ILLUSTRATIONS = {
  homeHero: '/illustrations/home-hero.jpg',
  homeTable: '/illustrations/home-table.svg',
  resolveTable: '/illustrations/resolve-table.svg',
  emptyState: '/illustrations/empty-state.svg',
  venuePlaceholder: '/illustrations/venue-placeholder.svg',
} as const

export type IllustrationName = keyof typeof ILLUSTRATIONS
