package com.siktak.common.seed;

import java.util.List;

/**
 * demo-v1 시드 정본 값 (docs/demo-scenario.md 2·3·6장). 출생연도·회차 선택·차단 관계는 문서 값 그대로다 —
 * 바꾸면 4장 예상 매칭 결과가 깨진다. 표에 없는 값(직업·가격대·대화 스타일, user23~29·demo01의 MBTI·관심사·성향)은
 * 문서 8장 허용에 따라 [임의]로 채웠다.
 */
final class DemoSeedData {

    static final String VERSION = "demo-v1";
    static final String ADMIN_LOGIN = "admin";
    static final String ADMIN_PASSWORD = "siktak-admin!";
    static final String USER_PASSWORD = "siktak1234";

    /** 지역 (2장) */
    static final List<String> REGIONS = List.of("성수", "홍대/합정", "강남");

    /**
     * 참가자. slots는 선택 순서 = 우선순위. paid=false면 결제 대기(PENDING)로 남긴다.
     * membership: 문서상 멤버십 이용권 사용자 — 3.4 user_passes 도입 전까지 1회권 결제 완료로 넣는다.
     */
    record Person(String loginId, String name, String gender, int birthYear, String mbti, List<String> interests,
            List<String> selfTraits, List<String> preferredTraits, List<String> slots, boolean paid,
            String membership) {
    }

    private static Person p(String id, String name, String g, int birth, String mbti, List<String> interests,
            List<String> self, List<String> preferred, List<String> slots) {
        return new Person(id, name, g, birth, mbti, interests, self, preferred, slots, true, null);
    }

    private static Person withMembership(Person p, String membership) {
        return new Person(p.loginId(), p.name(), p.gender(), p.birthYear(), p.mbti(), p.interests(), p.selfTraits(),
                p.preferredTraits(), p.slots(), p.paid(), membership);
    }

    private static Person unpaid(Person p) {
        return new Person(p.loginId(), p.name(), p.gender(), p.birthYear(), p.mbti(), p.interests(), p.selfTraits(),
                p.preferredTraits(), p.slots(), false, p.membership());
    }

    private static final List<String> DEFAULT_SELF = List.of("LISTENER");
    private static final List<String> DEFAULT_PREFERRED = List.of("EMPATHETIC");

    static final List<Person> PEOPLE = List.of(
            // 3-1. S1 성수 그룹 (1995~2000)
            p("user01", "지민", "F", 1997, "ENFP", List.of("FOOD", "TRAVEL", "MOVIE"), List.of("EMPATHETIC", "HUMOROUS"), List.of("HUMOROUS", "CURIOUS"), List.of("S1")),
            p("user02", "도윤", "M", 1996, "ISTJ", List.of("FOOD", "SPORTS", "CAREER"), List.of("PLANNER", "LISTENER"), List.of("EMPATHETIC"), List.of("S1")),
            withMembership(p("user03", "서아", "F", 1998, "INFJ", List.of("READING", "EXHIBITION", "MOVIE"), List.of("LISTENER", "EMPATHETIC"), List.of("CURIOUS", "HUMOROUS"), List.of("S1")), "MEMBERSHIP_1M"),
            p("user04", "준호", "M", 1995, "ESTP", List.of("SPORTS", "TRAVEL", "GAME"), List.of("ADVENTUROUS", "HUMOROUS"), List.of("EMPATHETIC", "CURIOUS"), List.of("S1")),
            p("user05", "하은", "F", 1999, "ENTJ", List.of("CAREER", "READING", "FOOD"), List.of("PLANNER", "CURIOUS"), List.of("PLANNER"), List.of("S1")),
            p("user06", "민재", "M", 1997, "INTP", List.of("GAME", "MOVIE", "MUSIC"), List.of("CURIOUS"), List.of("CURIOUS", "LISTENER"), List.of("S1")),
            withMembership(p("user07", "유나", "F", 2000, "ESFP", List.of("MUSIC", "FOOD", "PET"), List.of("HUMOROUS", "ADVENTUROUS"), List.of("HUMOROUS"), List.of("S1")), "MEMBERSHIP_3M"),
            p("user08", "태윤", "M", 1998, "ENFJ", List.of("TRAVEL", "EXHIBITION", "FOOD"), List.of("EMPATHETIC", "PLANNER"), List.of("LISTENER"), List.of("S1")),
            p("user09", "소율", "F", 1996, "ISFJ", List.of("PET", "READING", "FOOD"), List.of("LISTENER", "EMPATHETIC"), List.of("EMPATHETIC"), List.of("S1")),
            p("user10", "시우", "M", 1999, "ENTP", List.of("MOVIE", "GAME", "TRAVEL"), List.of("CURIOUS", "HUMOROUS"), List.of("ADVENTUROUS"), List.of("S1")),
            p("user11", "지아", "F", 1997, "INFP", List.of("MUSIC", "EXHIBITION", "MOVIE"), List.of("EMPATHETIC", "CURIOUS"), List.of("LISTENER", "EMPATHETIC"), List.of("S1")),
            p("user12", "건우", "M", 1995, "ESTJ", List.of("SPORTS", "CAREER", "FOOD"), List.of("PLANNER", "HUMOROUS"), List.of("ADVENTUROUS"), List.of("S1")),
            p("user13", "다인", "F", 1998, "ISTP", List.of("TRAVEL", "SPORTS", "PET"), List.of("ADVENTUROUS", "LISTENER"), List.of("HUMOROUS", "PLANNER"), List.of("S1")),
            // 3-2. 특수 역할 (성향 기본값: 본인 LISTENER, 희망 EMPATHETIC)
            p("user14", "현우", "M", 1985, "ISFP", List.of("FOOD", "MUSIC"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S1", "S3")),
            p("user15", "서연", "F", 1999, "ENFJ", List.of("EXHIBITION", "FOOD", "TRAVEL"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S2", "S1")),
            unpaid(p("user16", "지후", "M", 1997, "INTJ", List.of("GAME", "READING"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S1"))),
            // 3-3. S2 홍대/합정 그룹 (1990~1993)
            p("user17", "수빈", "F", 1991, "ESFJ", List.of("FOOD", "TRAVEL", "PET"), List.of("EMPATHETIC", "HUMOROUS"), List.of("LISTENER"), List.of("S2")),
            p("user18", "재원", "M", 1990, "INTJ", List.of("CAREER", "READING", "SPORTS"), List.of("PLANNER", "LISTENER"), List.of("HUMOROUS"), List.of("S2")),
            p("user19", "예린", "F", 1993, "ENFP", List.of("MUSIC", "MOVIE", "FOOD"), List.of("HUMOROUS", "CURIOUS"), List.of("EMPATHETIC"), List.of("S2")),
            p("user20", "동현", "M", 1992, "ISFP", List.of("GAME", "MUSIC", "TRAVEL"), List.of("LISTENER"), List.of("CURIOUS"), List.of("S2")),
            withMembership(p("user21", "채원", "F", 1992, "ESTJ", List.of("EXHIBITION", "CAREER", "FOOD"), List.of("PLANNER", "EMPATHETIC"), List.of("HUMOROUS"), List.of("S2")), "MEMBERSHIP_1M"),
            p("user22", "승민", "M", 1991, "ENTP", List.of("SPORTS", "TRAVEL", "MOVIE"), List.of("ADVENTUROUS", "HUMOROUS"), List.of("PLANNER"), List.of("S2")),
            // 3-4. S3 실패 그룹 · S5 대체 일정 그룹 · 2회차 신규 — MBTI·관심사·성향 [임의]
            p("user23", "은지", "F", 1986, "ESFJ", List.of("FOOD", "EXHIBITION"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S3")),
            p("user24", "상훈", "M", 1987, "ISTJ", List.of("SPORTS", "CAREER"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S3")),
            p("user25", "하린", "F", 1996, "ENFP", List.of("TRAVEL", "MUSIC"), List.of("HUMOROUS"), List.of("CURIOUS"), List.of()),
            p("user26", "도현", "M", 1998, "INTP", List.of("GAME", "MOVIE"), List.of("CURIOUS"), List.of("HUMOROUS"), List.of()),
            p("user27", "미정", "F", 1984, "INFP", List.of("READING", "FOOD"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S5")),
            p("user28", "정훈", "M", 1986, "ESTP", List.of("SPORTS", "TRAVEL"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S5")),
            withMembership(p("user29", "보람", "F", 1988, "ENFJ", List.of("EXHIBITION", "PET"), DEFAULT_SELF, DEFAULT_PREFERRED, List.of("S5")), "MEMBERSHIP_6M"),
            // 3-5. 심사위원 시연 계정 — 프로필 입력 완료, 신청 전
            p("demo01", "심사위원", "F", 1997, "ENFP", List.of("FOOD", "TRAVEL", "EXHIBITION"), List.of("EMPATHETIC", "CURIOUS"), List.of("HUMOROUS"), List.of()));

    /** 프로필 부가 값 [임의] — 순서대로 돌려 쓴다 */
    static final List<String> JOBS = List.of("개발자", "디자이너", "마케터", "교사", "간호사", "회계사", "연구원", "기획자");
    static final List<String> PRICE_RANGES = List.of("UNDER_20K", "20K_40K", "OVER_40K");
    static final List<String> CONVERSATION_STYLES = List.of("BALANCED", "LIVELY", "QUIET");

    /** 사전 차단 관계 (3-1): user05 ↔ user06, 과거 신고로 생성 */
    static final List<String[]> BLOCKS = List.<String[]>of(new String[] {"user05", "user06"});

    private DemoSeedData() {
    }
}
