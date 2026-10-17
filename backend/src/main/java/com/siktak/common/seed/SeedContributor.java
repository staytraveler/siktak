package com.siktak.common.seed;

/**
 * demo-v1 시드 확장 지점. 다른 도메인(식당·수용량·대화 콘텐츠 등)이 시드에 데이터를 붙일 때 이 인터페이스를 구현한 빈을 만든다.
 * <ul>
 *   <li>기본 시드(지역·회차·사용자·프로필·신청) 투입 후, {@link #order()} 오름차순으로 같은 트랜잭션에서 호출된다.</li>
 *   <li>회차·사용자 ID는 {@link SeedContext}에서 시드 코드(S1~S5, user01 등)로 찾는다.</li>
 *   <li>시드 초기화가 테이블을 비운 뒤 다시 호출되므로 "없으면 삽입" 검사는 필요 없다.</li>
 * </ul>
 */
public interface SeedContributor {

    /** 낮을수록 먼저. 식당(100) → 콘텐츠(200)처럼 여유를 두고 정한다. */
    default int order() {
        return 100;
    }

    void contribute(SeedContext context);
}
