package com.siktak.matching.domain.engine.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.siktak.matching.domain.engine.Candidate;
import com.siktak.matching.domain.engine.UserPair;
import com.siktak.profile.domain.Gender;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.Trait;

class TableScorerTest {

    private static final Offset<Double> E = within(1e-9);

    private static Candidate member(long id, int birthYear, Gender gender) {
        return member(id, birthYear, gender, null, Set.of(Interest.FOOD), Set.of(Trait.LISTENER), Set.of());
    }

    private static Candidate member(long id, int birthYear, Gender gender, Mbti mbti, Set<Interest> interests,
            Set<Trait> self, Set<Trait> preferred) {
        return new Candidate(id, id, birthYear, gender, mbti, interests, self, preferred, List.of(1L), true, true);
    }

    private static List<Candidate> genders(int male, int female) {
        List<Candidate> list = new ArrayList<>();
        long id = 1;
        for (int i = 0; i < male; i++) {
            list.add(member(id++, 1997, Gender.MALE));
        }
        for (int i = 0; i < female; i++) {
            list.add(member(id++, 1997, Gender.FEMALE));
        }
        return list;
    }

    private static List<Candidate> mbtis(Mbti... types) {
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            list.add(member(i + 1, 1997, Gender.MALE, types[i], Set.of(Interest.FOOD), Set.of(), Set.of()));
        }
        return list;
    }

    @Test
    void S1_0살차_1점_8살차_0점5() {
        assertThat(TableScorer.ageDensity(List.of(member(1, 1997, Gender.MALE), member(2, 1997, Gender.FEMALE))))
                .isCloseTo(1.0, E);
        assertThat(TableScorer.ageDensity(List.of(member(1, 1990, Gender.MALE), member(2, 1998, Gender.FEMALE))))
                .isCloseTo(0.5, E);
    }

    @Test
    void S2_성비() {
        assertThat(TableScorer.genderBalance(genders(3, 3))).isCloseTo(1.0, E);
        assertThat(TableScorer.genderBalance(genders(4, 2))).isCloseTo(1 - 2.0 / 6, E);
        assertThat(TableScorer.genderBalance(genders(6, 0))).isCloseTo(0.0, E);
        assertThat(TableScorer.genderBalance(genders(3, 2))).as("5명 3:2는 최선").isCloseTo(1.0, E);
        assertThat(TableScorer.genderBalance(genders(5, 0))).isCloseTo(1 - 4.0 / 5, E);
    }

    @Test
    void S3_전원_미입력이면_중립() {
        assertThat(TableScorer.mbtiBalance(genders(2, 2))).isCloseTo(0.5, E);
    }

    @Test
    void S3_입력자_1명이면_중립() {
        List<Candidate> members = new ArrayList<>(genders(2, 1));
        members.add(member(9, 1997, Gender.FEMALE, Mbti.ENFP, Set.of(), Set.of(), Set.of()));
        assertThat(TableScorer.mbtiBalance(members)).isCloseTo(0.5, E);
    }

    @Test
    void S3_축별_균형_EI_가중() {
        assertThat(TableScorer.mbtiBalance(mbtis(Mbti.ENFP, Mbti.ISTJ))).as("모든 축 균형").isCloseTo(1.0, E);
        assertThat(TableScorer.mbtiBalance(mbtis(Mbti.ESTJ, Mbti.ESTJ, Mbti.ESTJ, Mbti.ESTJ))).isCloseTo(0.0, E);
        // E/I 2:2, S/N 0:4, T/F 2:2, J/P 4:0 → 0.4 + 0 + 0.2 + 0
        assertThat(TableScorer.mbtiBalance(mbtis(Mbti.ENFJ, Mbti.ENTJ, Mbti.INFJ, Mbti.INTJ))).isCloseTo(0.6, E);
    }

    @Test
    void S3_미입력자는_계산에서_제외() {
        List<Candidate> members = new ArrayList<>(mbtis(Mbti.ENFP, Mbti.ISTJ));
        members.add(member(9, 1997, Gender.FEMALE));
        assertThat(TableScorer.mbtiBalance(members)).isCloseTo(1.0, E);
    }

    @Test
    void S4_공통관심사_쌍_비율() {
        List<Candidate> members = List.of(
                member(1, 1997, Gender.MALE, null, Set.of(Interest.FOOD), Set.of(), Set.of()),
                member(2, 1997, Gender.MALE, null, Set.of(Interest.FOOD, Interest.GAME), Set.of(), Set.of()),
                member(3, 1997, Gender.MALE, null, Set.of(Interest.MOVIE), Set.of(), Set.of()),
                member(4, 1997, Gender.MALE, null, Set.of(), Set.of(), Set.of()));
        assertThat(TableScorer.commonInterests(members)).isCloseTo(1.0 / 6, E);
    }

    @Test
    void S5_희망성향_충족_빈_희망성향은_1() {
        List<Candidate> members = List.of(
                member(1, 1997, Gender.MALE, null, Set.of(), Set.of(Trait.PLANNER), Set.of(Trait.HUMOROUS)),
                member(2, 1997, Gender.MALE, null, Set.of(), Set.of(Trait.HUMOROUS), Set.of()),
                member(3, 1997, Gender.MALE, null, Set.of(), Set.of(Trait.LISTENER), Set.of()),
                member(4, 1997, Gender.MALE, null, Set.of(), Set.of(Trait.LISTENER), Set.of()));
        assertThat(TableScorer.preferredTraits(members)).isCloseTo((1.0 / 3 + 1 + 1 + 1) / 4, E);
        assertThat(TableScorer.preferredTraits(genders(2, 2))).as("전원 희망 성향 없음").isCloseTo(1.0, E);
    }

    @Test
    void S6_이전_만남_쌍_감점() {
        List<Candidate> members = genders(2, 2);
        assertThat(TableScorer.newPeople(members, Set.of())).isCloseTo(1.0, E);
        assertThat(TableScorer.newPeople(members, Set.of(UserPair.of(2, 1)))).isCloseTo(1 - 1.0 / 6, E);
        assertThat(TableScorer.newPeople(members, Set.of(UserPair.of(1, 2), UserPair.of(3, 4), UserPair.of(1, 99))))
                .isCloseTo(1 - 2.0 / 6, E);
    }

    @Test
    void 총점은_원점수와_가중치의_곱의_합() {
        // S1 1, S2 1, S3 0.5, S4 1, S5 1, S6 1 → 20 + 20 + 9 + 16 + 14 + 12
        ScoreDetail detail = TableScorer.score(genders(2, 2), Set.of(), ScoreWeights.defaults());
        assertThat(detail.total()).isCloseTo(91.0, E);
        assertThat(detail.items()).extracting(ScoreDetail.Item::code).containsExactly(ScoreCode.values());
        assertThat(detail.item(ScoreCode.S3).raw()).isCloseTo(0.5, E);
        assertThat(detail.item(ScoreCode.S3).weighted()).isCloseTo(9.0, E);
    }
}
