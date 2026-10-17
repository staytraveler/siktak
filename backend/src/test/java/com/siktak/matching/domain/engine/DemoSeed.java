package com.siktak.matching.domain.engine;

import static com.siktak.matching.domain.engine.Fixtures.D;
import static com.siktak.matching.domain.engine.Fixtures.cand;
import static com.siktak.matching.domain.engine.Fixtures.slot;
import static com.siktak.profile.domain.Interest.CAREER;
import static com.siktak.profile.domain.Interest.EXHIBITION;
import static com.siktak.profile.domain.Interest.FOOD;
import static com.siktak.profile.domain.Interest.GAME;
import static com.siktak.profile.domain.Interest.MOVIE;
import static com.siktak.profile.domain.Interest.MUSIC;
import static com.siktak.profile.domain.Interest.PET;
import static com.siktak.profile.domain.Interest.READING;
import static com.siktak.profile.domain.Interest.SPORTS;
import static com.siktak.profile.domain.Interest.TRAVEL;
import static com.siktak.profile.domain.Trait.ADVENTUROUS;
import static com.siktak.profile.domain.Trait.CURIOUS;
import static com.siktak.profile.domain.Trait.EMPATHETIC;
import static com.siktak.profile.domain.Trait.HUMOROUS;
import static com.siktak.profile.domain.Trait.LISTENER;
import static com.siktak.profile.domain.Trait.PLANNER;

import java.util.List;
import java.util.Set;

import com.siktak.profile.domain.Mbti;

/**
 * docs/demo-scenario.md 3장 시드의 J1(S1·S2 매칭) 입력. 신청 ID·사용자 ID = userNN의 NN.
 *
 * <p>S2 그룹(user17~22)의 성향은 이 클래스의 값이 정본이다 (demo-scenario.md 3-3에 같은 값을 적었다).
 */
public final class DemoSeed {

    public static final long S1 = 1; // 성수, D
    public static final long S2 = 2; // 홍대/합정, D
    public static final long S3 = 3; // 성수, D+1 (J1 실행 대상 아님)

    static final long SEONGSU = 1;
    static final long HONGDAE = 2;

    private DemoSeed() {
    }

    public static MatchingInput j1Input() {
        List<SlotInfo> slots = List.of(slot(S1, D, SEONGSU), slot(S2, D, HONGDAE));
        List<Candidate> candidates = List.of(
                // 3-1. S1 성수 그룹
                cand(1).female().born(1997).mbti(Mbti.ENFP).interests(FOOD, TRAVEL, MOVIE)
                        .self(EMPATHETIC, HUMOROUS).prefers(HUMOROUS, CURIOUS).slots(S1).build(),
                cand(2).male().born(1996).mbti(Mbti.ISTJ).interests(FOOD, SPORTS, CAREER)
                        .self(PLANNER, LISTENER).prefers(EMPATHETIC).slots(S1).build(),
                cand(3).female().born(1998).mbti(Mbti.INFJ).interests(READING, EXHIBITION, MOVIE)
                        .self(LISTENER, EMPATHETIC).prefers(CURIOUS, HUMOROUS).slots(S1).build(),
                cand(4).male().born(1995).mbti(Mbti.ESTP).interests(SPORTS, TRAVEL, GAME)
                        .self(ADVENTUROUS, HUMOROUS).prefers(EMPATHETIC, CURIOUS).slots(S1).build(),
                cand(5).female().born(1999).mbti(Mbti.ENTJ).interests(CAREER, READING, FOOD)
                        .self(PLANNER, CURIOUS).prefers(PLANNER).slots(S1).build(),
                cand(6).male().born(1997).mbti(Mbti.INTP).interests(GAME, MOVIE, MUSIC)
                        .self(CURIOUS).prefers(CURIOUS, LISTENER).slots(S1).build(),
                cand(7).female().born(2000).mbti(Mbti.ESFP).interests(MUSIC, FOOD, PET)
                        .self(HUMOROUS, ADVENTUROUS).prefers(HUMOROUS).slots(S1).build(),
                cand(8).male().born(1998).mbti(Mbti.ENFJ).interests(TRAVEL, EXHIBITION, FOOD)
                        .self(EMPATHETIC, PLANNER).prefers(LISTENER).slots(S1).build(),
                cand(9).female().born(1996).mbti(Mbti.ISFJ).interests(PET, READING, FOOD)
                        .self(LISTENER, EMPATHETIC).prefers(EMPATHETIC).slots(S1).build(),
                cand(10).male().born(1999).mbti(Mbti.ENTP).interests(MOVIE, GAME, TRAVEL)
                        .self(CURIOUS, HUMOROUS).prefers(ADVENTUROUS).slots(S1).build(),
                cand(11).female().born(1997).mbti(Mbti.INFP).interests(MUSIC, EXHIBITION, MOVIE)
                        .self(EMPATHETIC, CURIOUS).prefers(LISTENER, EMPATHETIC).slots(S1).build(),
                cand(12).male().born(1995).mbti(Mbti.ESTJ).interests(SPORTS, CAREER, FOOD)
                        .self(PLANNER, HUMOROUS).prefers(ADVENTUROUS).slots(S1).build(),
                cand(13).female().born(1998).mbti(Mbti.ISTP).interests(TRAVEL, SPORTS, PET)
                        .self(ADVENTUROUS, LISTENER).prefers(HUMOROUS, PLANNER).slots(S1).build(),
                // 3-2. 특수 역할 (성향 기본값: 본인 LISTENER, 희망 EMPATHETIC)
                cand(14).male().born(1985).mbti(Mbti.ISFP).interests(FOOD, MUSIC)
                        .self(LISTENER).prefers(EMPATHETIC).slots(S1, S3).build(),
                cand(15).female().born(1999).mbti(Mbti.ENFJ).interests(EXHIBITION, FOOD, TRAVEL)
                        .self(LISTENER).prefers(EMPATHETIC).slots(S2, S1).build(),
                cand(16).male().born(1997).mbti(Mbti.INTJ).interests(GAME, READING)
                        .self(LISTENER).prefers(EMPATHETIC).slots(S1).unpaid().build(),
                // 3-3. S2 홍대/합정 그룹
                cand(17).female().born(1991).mbti(Mbti.ESFJ).interests(FOOD, TRAVEL, PET)
                        .self(EMPATHETIC, HUMOROUS).prefers(LISTENER).slots(S2).build(),
                cand(18).male().born(1990).mbti(Mbti.INTJ).interests(CAREER, READING, SPORTS)
                        .self(PLANNER, LISTENER).prefers(HUMOROUS).slots(S2).build(),
                cand(19).female().born(1993).mbti(Mbti.ENFP).interests(MUSIC, MOVIE, FOOD)
                        .self(HUMOROUS, CURIOUS).prefers(EMPATHETIC).slots(S2).build(),
                cand(20).male().born(1992).mbti(Mbti.ISFP).interests(GAME, MUSIC, TRAVEL)
                        .self(LISTENER).prefers(CURIOUS).slots(S2).build(),
                cand(21).female().born(1992).mbti(Mbti.ESTJ).interests(EXHIBITION, CAREER, FOOD)
                        .self(PLANNER, EMPATHETIC).prefers(HUMOROUS).slots(S2).build(),
                cand(22).male().born(1991).mbti(Mbti.ENTP).interests(SPORTS, TRAVEL, MOVIE)
                        .self(ADVENTUROUS, HUMOROUS).prefers(PLANNER).slots(S2).build());

        Set<UserPair> blocked = Set.of(UserPair.of(5, 6)); // user05 ↔ user06 (과거 신고)
        return new MatchingInput(slots, candidates, blocked, Set.of(), MatchingConfig.defaults());
    }
}
