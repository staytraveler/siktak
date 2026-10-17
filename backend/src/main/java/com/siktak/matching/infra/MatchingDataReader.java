package com.siktak.matching.infra;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.siktak.matching.domain.engine.MatchingConfig;
import com.siktak.matching.domain.engine.UserPair;
import com.siktak.matching.domain.engine.score.ScoreWeights;
import com.siktak.profile.domain.Gender;
import com.siktak.profile.domain.Interest;
import com.siktak.profile.domain.Mbti;
import com.siktak.profile.domain.Trait;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 매칭 입력용 읽기 전용 조회: 프로필, 차단·{@code AVOID} 관계, 만남 이력, 설정.
 *
 * <p>프로필·관계 테이블은 아직 JPA 엔티티가 없어(프로필 3.x, 안전 7.x) SQL로 읽는다.
 */
@Component
@RequiredArgsConstructor
public class MatchingDataReader {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * 매칭에 필요한 사용자 프로필.
     *
     * @param complete 출생연도·성별이 있어야 매칭할 수 있다 (없으면 DATA_MISSING)
     */
    public record UserProfile(long userId, boolean accountActive, Integer birthYear, Gender gender, Mbti mbti,
            Set<Interest> interests, Set<Trait> selfTraits, Set<Trait> preferredTraits, String priceRange) {

        public boolean complete() {
            return birthYear != null && gender != null;
        }
    }

    public Map<Long, UserProfile> profiles(Collection<Long> userIds) {
        Map<Long, UserProfile> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select u.id, u.account_status, p.birth_year, p.gender, p.mbti, p.interests::text as interests,
                       p.self_traits::text as self_traits, p.preferred_traits::text as preferred_traits, p.price_range
                from users u left join profiles p on p.user_id = u.id
                where u.id in (:ids)
                """, Map.of("ids", userIds), rs -> {
            long id = rs.getLong("id");
            Integer birthYear = (Integer) rs.getObject("birth_year");
            result.put(id, new UserProfile(
                    id,
                    "ACTIVE".equals(rs.getString("account_status")),
                    birthYear,
                    parseEnum(Gender.class, rs.getString("gender")),
                    parseEnum(Mbti.class, rs.getString("mbti")),
                    parseEnumSet(Interest.class, rs.getString("interests")),
                    parseEnumSet(Trait.class, rs.getString("self_traits")),
                    parseEnumSet(Trait.class, rs.getString("preferred_traits")),
                    rs.getString("price_range")));
        });
        return result;
    }

    /** 차단·신고(user_blocks)와 다시 만나고 싶지 않음(person_preferences AVOID). 한쪽만 있어도 쌍 하나 (H6). */
    public Set<UserPair> blockedPairs(Collection<Long> userIds) {
        Set<UserPair> pairs = new HashSet<>();
        if (userIds.isEmpty()) {
            return pairs;
        }
        Map<String, Object> params = Map.of("ids", userIds);
        jdbc.query("select blocker_id, blocked_id from user_blocks where blocker_id in (:ids) and blocked_id in (:ids)",
                params, rs -> {
                    pairs.add(UserPair.of(rs.getLong(1), rs.getLong(2)));
                });
        jdbc.query("""
                select from_user_id, to_user_id from person_preferences
                where preference = 'AVOID' and from_user_id in (:ids) and to_user_id in (:ids)
                  and from_user_id <> to_user_id
                """, params, rs -> {
            pairs.add(UserPair.of(rs.getLong(1), rs.getLong(2)));
        });
        return pairs;
    }

    /** 이전에 같은 테이블에서 만난 쌍 (S6). */
    public Set<UserPair> encounters(Collection<Long> userIds) {
        Set<UserPair> pairs = new HashSet<>();
        if (userIds.isEmpty()) {
            return pairs;
        }
        jdbc.query("""
                select distinct user_low_id, user_high_id from user_encounters
                where user_low_id in (:ids) and user_high_id in (:ids)
                """, Map.of("ids", userIds), rs -> {
            pairs.add(new UserPair(rs.getLong(1), rs.getLong(2)));
        });
        return pairs;
    }

    /**
     * {@code settings}의 {@code matching.*} 값. 없는 키는 기본값 (matching.md 9장).
     * <ul>
     *   <li>{@code matching.weights}: {@code {"S1":20,...,"S6":12}}</li>
     *   <li>{@code matching.min-score}, {@code matching.max-swap-iterations}, {@code matching.max-merge-rounds},
     *       {@code matching.alternative-count}: 숫자</li>
     * </ul>
     */
    public MatchingConfig config() {
        Map<String, JsonNode> values = new HashMap<>();
        jdbc.query("select key, value::text as value from settings where key like 'matching.%'", Map.of(), rs -> {
            values.put(rs.getString("key"), JSON.readTree(rs.getString("value")));
        });
        MatchingConfig d = MatchingConfig.defaults();
        ScoreWeights w = d.weights();
        JsonNode weights = values.get("matching.weights");
        if (weights != null) {
            w = new ScoreWeights(
                    weights.path("S1").asDouble(w.s1()), weights.path("S2").asDouble(w.s2()),
                    weights.path("S3").asDouble(w.s3()), weights.path("S4").asDouble(w.s4()),
                    weights.path("S5").asDouble(w.s5()), weights.path("S6").asDouble(w.s6()));
        }
        return new MatchingConfig(
                w,
                number(values, "matching.min-score", d.minScore()),
                (int) number(values, "matching.max-swap-iterations", d.maxSwapIterations()),
                (int) number(values, "matching.max-merge-rounds", d.maxMergeRounds()),
                (int) number(values, "matching.alternative-count", d.alternativeCount()));
    }

    private static double number(Map<String, JsonNode> values, String key, double fallback) {
        JsonNode node = values.get(key);
        return node == null || !node.isNumber() ? fallback : node.asDouble();
    }

    /** 알 수 없는 값은 버린다 (선택지 목록은 matching.md 4장). */
    static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value.trim())) {
                return e;
            }
        }
        return null;
    }

    static <E extends Enum<E>> Set<E> parseEnumSet(Class<E> type, String jsonArray) {
        EnumSet<E> set = EnumSet.noneOf(type);
        if (jsonArray == null || jsonArray.isBlank()) {
            return set;
        }
        for (String value : JSON.readValue(jsonArray, String[].class)) {
            E e = parseEnum(type, value);
            if (e != null) {
                set.add(e);
            }
        }
        return set;
    }
}
