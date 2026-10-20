package com.siktak.matching.domain.engine;

import static com.siktak.matching.domain.engine.constraint.HardConstraints.MAX_AGE_GAP;
import static com.siktak.matching.domain.engine.constraint.HardConstraints.TABLE_MAX;
import static com.siktak.matching.domain.engine.constraint.HardConstraints.TABLE_MIN;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.siktak.matching.domain.engine.constraint.HardConstraints;
import com.siktak.matching.domain.engine.score.ScoreDetail;
import com.siktak.matching.domain.engine.score.TableScorer;
import com.siktak.profile.domain.Gender;

/**
 * 매칭 1회 실행의 작업 상태와 절차 (matching.md 5-1~5-8).
 *
 * <p>결정성 규칙: 순회는 정렬된 목록·{@link TreeMap}으로만 하고 {@link HashMap}은 조회에만 쓴다.
 * 동점은 application ID 오름차순(먼저 만든 쪽)으로 정한다.
 */
final class MatchingRun {

    private static final double EPS = 1e-9;
    private static final Comparator<Candidate> BY_ID = Comparator.comparingLong(Candidate::applicationId);
    private static final Comparator<Candidate> BY_AGE =
            Comparator.comparingInt(Candidate::birthYear).thenComparingLong(Candidate::applicationId);

    private final MatchingInput input;
    private final MatchingConfig config;
    private final Set<UserPair> blocked;
    private final Set<UserPair> encounters;
    private final Set<UserPair> wantAgain;

    private final Map<Long, SlotInfo> slots = new TreeMap<>();
    private final Map<Long, Candidate> candidates = new TreeMap<>();
    private final Map<Long, Long> originSlot = new HashMap<>();
    private final List<TempTable> tables = new ArrayList<>();
    /** 미배치자 → 미배치 원인. application ID 순으로 순회한다. */
    private final TreeMap<Long, ReasonCode> unplaced = new TreeMap<>();
    private final Map<Long, Relocation> relocations = new HashMap<>();
    private final List<PlacementLog> logs = new ArrayList<>();
    private final List<ExcludedCandidate> excluded = new ArrayList<>();

    private int nextTableSeq;
    private int tablesCreated;
    private int splitGroups;
    private int mergedTables;
    private int reallocatedCount;
    private int dissolvedTables;

    MatchingRun(MatchingInput input) {
        this.input = input;
        this.config = input.config();
        this.blocked = input.blockedPairs();
        this.encounters = input.encounters();
        this.wantAgain = input.wantAgain();
    }

    MatchingResult execute() {
        extractCandidates();

        // 5-2·5-3 최초 배치와 회차 내 테이블 구성
        for (Map.Entry<Long, List<Candidate>> pool : poolsByFirstChoice().entrySet()) {
            long slotId = pool.getKey();
            Built built = buildTables(slotId, pool.getValue());
            built.unplaced().forEach(unplaced::put);
            splitGroups += built.splitGroups();
            tablesCreated += built.tables().size();
            tables.addAll(built.tables());
            for (TempTable t : built.tables()) {
                for (Candidate m : t.members) {
                    log(m.applicationId(), PlacementDecision.PLACED, null, slotId, null,
                            ReasonCode.PLACED_FIRST_CHOICE, null);
                }
            }
        }

        // 5-4 점수 검증
        for (long slotId : slots.keySet()) {
            enforceMinScore(slotId);
        }

        reallocate(); // 5-5
        merge(); // 5-6
        validateTables(); // 5-7
        List<ProposedTable> proposed = confirmTables();

        // 5-8 미배치자 처리
        List<UnplacedCandidate> deferred = new ArrayList<>();
        List<UnplacedCandidate> failed = new ArrayList<>();
        for (Map.Entry<Long, ReasonCode> e : unplaced.entrySet()) {
            Candidate c = candidates.get(e.getKey());
            long origin = originSlot.get(c.applicationId());
            ReasonCode cause = e.getValue();
            boolean futureChoiceLeft = c.slotChoices().stream().anyMatch(s -> !slots.containsKey(s));
            if (futureChoiceLeft) {
                deferred.add(new UnplacedCandidate(c.applicationId(), c.userId(), origin, ReasonCode.DEFERRED, cause));
                log(c.applicationId(), PlacementDecision.DEFERRED, origin, null, null, ReasonCode.DEFERRED, cause);
            } else {
                failed.add(new UnplacedCandidate(c.applicationId(), c.userId(), origin, cause, cause));
                log(c.applicationId(), PlacementDecision.FAILED, origin, null, null, cause, cause);
            }
        }

        MatchingSummary summary = new MatchingSummary(
                input.candidates().size(),
                excluded.size(),
                tablesCreated,
                splitGroups,
                mergedTables,
                reallocatedCount,
                dissolvedTables,
                proposed.size(),
                proposed.stream().mapToInt(t -> t.members().size()).sum(),
                deferred.size(),
                failed.size());
        return new MatchingResult(proposed, logs, deferred, failed, excluded, summary);
    }

    // ---------------------------------------------------------------- 5-1 후보 추출

    private void extractCandidates() {
        for (SlotInfo s : input.slots()) {
            if (slots.put(s.id(), s) != null) {
                throw new IllegalArgumentException("회차 ID 중복: " + s.id());
            }
        }
        Set<Long> seen = new HashSet<>();
        for (Candidate c : input.candidates().stream().sorted(BY_ID).toList()) {
            if (!seen.add(c.applicationId())) {
                throw new IllegalArgumentException("신청 ID 중복: " + c.applicationId());
            }
            Long origin = c.slotChoices().stream().filter(slots::containsKey).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "이번 실행 대상 회차를 선택하지 않은 신청: " + c.applicationId()));
            if (!c.paid()) {
                exclude(c, ReasonCode.NOT_PAID); // H1
            } else if (!c.accountActive()) {
                exclude(c, ReasonCode.ACCOUNT_RESTRICTED); // H2
            } else {
                candidates.put(c.applicationId(), c);
                originSlot.put(c.applicationId(), origin);
            }
        }
    }

    private void exclude(Candidate c, ReasonCode reason) {
        excluded.add(new ExcludedCandidate(c.applicationId(), c.userId(), reason));
        log(c.applicationId(), PlacementDecision.EXCLUDED, null, null, null, reason, null);
    }

    /** 5-2. 각 후보를 이번 실행 대상 회차 중 1순위 회차의 후보군에 넣는다. */
    private Map<Long, List<Candidate>> poolsByFirstChoice() {
        Map<Long, List<Candidate>> pools = new TreeMap<>();
        for (Candidate c : candidates.values()) {
            pools.computeIfAbsent(originSlot.get(c.applicationId()), k -> new ArrayList<>()).add(c);
        }
        return pools;
    }

    // ---------------------------------------------------------------- 5-3 회차 내 테이블 구성

    private record Built(List<TempTable> tables, Map<Long, ReasonCode> unplaced, int splitGroups) {
    }

    private Built buildTables(long slotId, List<Candidate> pool) {
        List<Candidate> sorted = pool.stream().sorted(BY_AGE).toList();
        List<TempTable> built = new ArrayList<>();
        Map<Long, ReasonCode> left = new TreeMap<>();
        int splits = 0;

        // 1. 연령 그룹: 출생연도가 가장 빠른 사람부터 +8 이내를 한 그룹으로 묶고, 남은 사람으로 반복. (matching.md 11장 I1)
        int i = 0;
        while (i < sorted.size()) {
            int start = sorted.get(i).birthYear();
            int j = i;
            while (j < sorted.size() && sorted.get(j).birthYear() - start <= MAX_AGE_GAP) {
                j++;
            }
            List<Candidate> group = sorted.subList(i, j);
            i = j;

            // 2. 그룹 크기별 처리
            int n = group.size();
            if (n < TABLE_MIN) {
                ReasonCode reason = pool.size() < TABLE_MIN ? ReasonCode.UNDER_MIN_SIZE : ReasonCode.AGE_GAP;
                group.forEach(c -> left.put(c.applicationId(), reason));
                continue;
            }
            List<TempTable> groupTables = new ArrayList<>();
            if (n <= TABLE_MAX) {
                groupTables.add(newTable(slotId, group));
            } else if (n == TABLE_MAX + 1) {
                Candidate out = leftoverOfSeven(group);
                // matching.md 11장 I2: 7명 중 남는 1명의 사유는 UNDER_MIN_SIZE.
                left.put(out.applicationId(), ReasonCode.UNDER_MIN_SIZE);
                groupTables.add(newTable(slotId, without(group, out)));
            } else {
                groupTables.addAll(splitEvenly(slotId, group));
                splits++;
            }

            resolveConflicts(groupTables, left); // 4. 차단 관계 해소
            for (TempTable t : List.copyOf(groupTables)) {
                if (t.size() < TABLE_MIN) {
                    // matching.md 11장 I3: 차단 해소로 4명 미만이 된 테이블은 해체하고 남은 멤버는 UNDER_MIN_SIZE.
                    groupTables.remove(t);
                    t.members.forEach(m -> left.put(m.applicationId(), ReasonCode.UNDER_MIN_SIZE));
                }
            }
            improve(groupTables); // 5. 점수 개선
            built.addAll(groupTables);
        }
        return new Built(built, left, splits);
    }

    /**
     * n = 7: 6명 조합 7가지를 전수 비교한다. 충돌 쌍이 적은 조합 → 점수가 높은 조합 순.
     *
     * <p>동점이면 application ID가 큰 사람(나중 신청)을 뺀다 (matching.md 11장 I2).
     */
    private Candidate leftoverOfSeven(List<Candidate> group) {
        Candidate best = null;
        int bestConflicts = Integer.MAX_VALUE;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate out : group.stream().sorted(BY_ID.reversed()).toList()) {
            List<Candidate> rest = without(group, out);
            int conflicts = HardConstraints.conflicts(rest, blocked);
            double score = score(rest);
            if (conflicts < bestConflicts || (conflicts == bestConflicts && score > bestScore + EPS)) {
                best = out;
                bestConflicts = conflicts;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * n ≥ 8: k = ceil(n / 6)개 테이블로 최대한 균등하게 나눈다 (MAT-09).
     * 3. 초기 배정: 성별로 나눈 뒤 나이순으로 k개 테이블에 번갈아 배정한다.
     */
    private List<TempTable> splitEvenly(long slotId, List<Candidate> group) {
        int n = group.size();
        int k = (n + TABLE_MAX - 1) / TABLE_MAX;
        int[] capacity = new int[k];
        List<List<Candidate>> parts = new ArrayList<>();
        for (int t = 0; t < k; t++) {
            capacity[t] = n / k + (t < n % k ? 1 : 0);
            parts.add(new ArrayList<>());
        }
        List<Candidate> order = new ArrayList<>();
        order.addAll(group.stream().filter(c -> c.gender() == Gender.MALE).sorted(BY_AGE).toList());
        order.addAll(group.stream().filter(c -> c.gender() != Gender.MALE).sorted(BY_AGE).toList());
        int t = 0;
        for (Candidate c : order) {
            while (parts.get(t).size() >= capacity[t]) {
                t = (t + 1) % k;
            }
            parts.get(t).add(c);
            t = (t + 1) % k;
        }
        return parts.stream().map(p -> newTable(slotId, p)).toList();
    }

    /**
     * 4. 충돌(H6 차단 관계·같은 사용자) 해소. 다른 테이블 멤버와 교환해 충돌 쌍 수를 줄이고,
     * 교환으로 안 되면 충돌 쌍 중 application ID가 큰 쪽을 뺀다 ({@code BLOCKED_PAIR}).
     */
    private void resolveConflicts(List<TempTable> group, Map<Long, ReasonCode> left) {
        int iterations = 0;
        while (iterations < config.maxSwapIterations() && group.stream().anyMatch(t -> conflicts(t.members) > 0)) {
            Swap best = null;
            for (int a = 0; a < group.size(); a++) {
                for (int b = a + 1; b < group.size(); b++) {
                    TempTable ta = group.get(a);
                    TempTable tb = group.get(b);
                    int before = conflicts(ta.members) + conflicts(tb.members);
                    if (before == 0) {
                        continue;
                    }
                    for (Candidate x : ta.members) {
                        for (Candidate y : tb.members) {
                            if (!y.chose(ta.slotId) || !x.chose(tb.slotId)) {
                                continue;
                            }
                            List<Candidate> na = replace(ta.members, x, y);
                            List<Candidate> nb = replace(tb.members, y, x);
                            if (!HardConstraints.ageGapOk(na) || !HardConstraints.ageGapOk(nb)) {
                                continue;
                            }
                            int gain = before - conflicts(na) - conflicts(nb);
                            if (gain <= 0) {
                                continue;
                            }
                            double scoreSum = score(na) + score(nb);
                            if (best == null || gain > best.conflictGain
                                    || (gain == best.conflictGain && scoreSum > best.scoreSum + EPS)) {
                                best = new Swap(ta, tb, na, nb, gain, scoreSum);
                            }
                        }
                    }
                }
            }
            if (best == null) {
                break;
            }
            best.apply(this);
            iterations++;
        }

        for (TempTable t : group) {
            Candidate[] pair;
            while ((pair = firstConflict(t.members)) != null) {
                Candidate drop = pair[1]; // members는 ID 오름차순이라 뒤쪽이 ID가 큰 쪽
                // matching.md 11장 I7: 같은 사용자 신청끼리의 충돌은 BLOCKED_PAIR가 아니라 DUPLICATE_DATE.
                boolean sameUser = pair[0].userId() == drop.userId();
                left.put(drop.applicationId(), sameUser ? ReasonCode.DUPLICATE_DATE : ReasonCode.BLOCKED_PAIR);
                set(t, without(t.members, drop));
            }
        }
    }

    /** application ID 순으로 첫 충돌 쌍. 없으면 null. */
    private Candidate[] firstConflict(List<Candidate> members) {
        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                if (HardConstraints.conflicting(members.get(i), members.get(j), blocked)) {
                    return new Candidate[] {members.get(i), members.get(j)};
                }
            }
        }
        return null;
    }

    /**
     * 5. 점수 개선 (로컬 서치). 서로 다른 테이블의 두 멤버를 바꿔 두 테이블 점수 합이 오르고 필수 조건을 지키면
     * 교환한다. 테이블 쌍을 순서대로 돌며 쌍마다 가장 크게 오르는 교환을 적용하고, 한 바퀴 동안 개선이 없거나
     * 교환 횟수가 {@code maxSwapIterations}에 도달하면 끝낸다.
     */
    private void improve(List<TempTable> group) {
        int iterations = 0;
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int a = 0; a < group.size(); a++) {
                for (int b = a + 1; b < group.size(); b++) {
                    while (bestSwapBetween(group.get(a), group.get(b))) {
                        improved = true;
                        if (++iterations >= config.maxSwapIterations()) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** 두 테이블 사이에서 점수 합이 가장 크게 오르는 교환을 적용한다. 동점이면 ID 순으로 먼저 찾은 것. */
    private boolean bestSwapBetween(TempTable ta, TempTable tb) {
        double before = ta.score() + tb.score();
        List<Candidate> bestA = null;
        List<Candidate> bestB = null;
        double bestSum = before + EPS;
        for (Candidate x : ta.members) {
            for (Candidate y : tb.members) {
                if (!y.chose(ta.slotId) || !x.chose(tb.slotId)) {
                    continue;
                }
                List<Candidate> na = replace(ta.members, x, y);
                List<Candidate> nb = replace(tb.members, y, x);
                if (!HardConstraints.compatible(na, blocked) || !HardConstraints.compatible(nb, blocked)) {
                    continue;
                }
                double sum = score(na) + score(nb);
                if (sum > bestSum) {
                    bestA = na;
                    bestB = nb;
                    bestSum = sum + EPS;
                }
            }
        }
        if (bestA == null) {
            return false;
        }
        set(ta, bestA);
        set(tb, bestB);
        return true;
    }

    private record Swap(TempTable ta, TempTable tb, List<Candidate> na, List<Candidate> nb,
            int conflictGain, double scoreSum) {

        void apply(MatchingRun run) {
            run.set(ta, na);
            run.set(tb, nb);
        }
    }

    // ---------------------------------------------------------------- 5-4 점수 검증

    private void enforceMinScore(long slotId) {
        List<TempTable> slotTables = tables.stream().filter(t -> t.slotId == slotId).toList();
        if (slotTables.stream().noneMatch(this::belowMinScore)) {
            return;
        }
        // 해체 전 교환을 같은 회차의 다른 임시 테이블(다른 연령 그룹 포함)까지 넓혀 한 번 더 시도한다.
        improve(new ArrayList<>(slotTables));
        for (TempTable t : slotTables) {
            if (belowMinScore(t)) {
                dissolve(t, ReasonCode.LOW_SCORE);
            }
        }
    }

    // ---------------------------------------------------------------- 5-5 재배치

    /** 미배치자를 ID 순으로 재배치 1~4단계(PRD 7.4) 순서대로 시도한다. 한 명이라도 들어가면 true. */
    private boolean reallocate() {
        boolean changed = false;
        for (long applicationId : List.copyOf(unplaced.keySet())) {
            Candidate c = candidates.get(applicationId);
            SlotInfo origin = slots.get(originSlot.get(applicationId));
            for (int step = 1; step <= 4; step++) {
                TempTable target = bestReallocTarget(c, origin, step);
                if (target != null) {
                    ReasonCode cause = unplaced.remove(applicationId);
                    set(target, with(target.members, c));
                    recordRelocation(c, origin.id(), target.slotId, step, cause);
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    /** 들어갈 수 있는 임시 테이블 중 점수 상승이 가장 큰 곳. 동점이면 먼저 만든 테이블. */
    private TempTable bestReallocTarget(Candidate c, SlotInfo origin, int step) {
        TempTable best = null;
        double bestDelta = 0;
        for (TempTable t : tables) {
            SlotInfo slot = slots.get(t.slotId);
            if (reallocStep(origin, slot) != step || !c.chose(slot.id()) || t.size() >= TABLE_MAX) {
                continue;
            }
            List<Candidate> next = with(t.members, c);
            if (!HardConstraints.compatible(next, blocked) || !dateFree(c, slot.date())) {
                continue;
            }
            double score = score(next);
            if (score < config.minScore() - EPS) {
                continue;
            }
            double delta = score - t.score();
            if (best == null || delta > bestDelta + EPS) {
                best = t;
                bestDelta = delta;
            }
        }
        return best;
    }

    /**
     * 재배치 단계 (PRD 7.4). 1 같은 날짜·같은 지역, 2 같은 날짜·다른 지역, 3 다른 날짜·같은 지역, 4 다른 날짜·다른 지역.
     *
     * <p>1단계 "같은 회차"는 같은 날짜·같은 지역 회차 전체다 (matching.md 11장 I5).
     */
    private static int reallocStep(SlotInfo origin, SlotInfo target) {
        boolean sameDate = origin.date().equals(target.date());
        boolean sameRegion = origin.regionId() == target.regionId();
        if (sameDate) {
            return sameRegion ? 1 : 2;
        }
        return sameRegion ? 3 : 4;
    }

    /** H7: 같은 사용자의 다른 신청이 같은 날짜 테이블에 있지 않아야 한다. */
    private boolean dateFree(Candidate c, LocalDate date) {
        for (TempTable t : tables) {
            if (!slots.get(t.slotId).date().equals(date)) {
                continue;
            }
            for (Candidate m : t.members) {
                if (m.userId() == c.userId() && m.applicationId() != c.applicationId()) {
                    return false;
                }
            }
        }
        return true;
    }

    private record Relocation(long fromSlotId, long toSlotId, int step, ReasonCode reason, ReasonCode cause) {
    }

    private void recordRelocation(Candidate c, long fromSlotId, long toSlotId, int step, ReasonCode cause) {
        ReasonCode reason = ReasonCode.forReallocStep(step);
        relocations.put(c.applicationId(), new Relocation(fromSlotId, toSlotId, step, reason, cause));
        reallocatedCount++;
        log(c.applicationId(), PlacementDecision.REALLOCATED, fromSlotId, toSlotId, step, reason, cause);
    }

    // ---------------------------------------------------------------- 5-6 병합

    /**
     * 회차별로 그 회차를 선택한 미배치자를 모아 4명 이상이면 5-3으로 새 테이블을 만든다.
     * 새 테이블이 생기면 재배치를 한 번 더 돌리고, 변화가 없을 때까지 최대 {@code maxMergeRounds}회 반복한다.
     */
    private void merge() {
        for (int round = 0; round < config.maxMergeRounds(); round++) {
            boolean created = false;
            for (long slotId : slots.keySet()) {
                List<Candidate> pool = unplaced.keySet().stream()
                        .map(candidates::get)
                        .filter(c -> c.chose(slotId))
                        .toList();
                if (pool.size() < TABLE_MIN) {
                    continue;
                }
                // 병합에서 다시 남은 사람은 처음 미배치 원인을 그대로 둔다.
                Built built = buildTables(slotId, pool);
                splitGroups += built.splitGroups();
                for (TempTable t : built.tables()) {
                    if (belowMinScore(t) || t.members.stream().anyMatch(m -> !dateFree(m, slots.get(slotId).date()))) {
                        // matching.md 11장 I6: 병합 테이블도 최소 점수(5-4)·H7을 만족해야 만든다.
                        continue;
                    }
                    tables.add(t);
                    mergedTables++;
                    created = true;
                    SlotInfo target = slots.get(slotId);
                    for (Candidate m : t.members) {
                        ReasonCode cause = unplaced.remove(m.applicationId());
                        SlotInfo origin = slots.get(originSlot.get(m.applicationId()));
                        // matching.md 11장 I6: 병합으로 들어간 경우도 출발 회차 기준으로 재배치 단계(1~4)를 매긴다.
                        recordRelocation(m, origin.id(), slotId, reallocStep(origin, target), cause);
                    }
                }
            }
            if (!created) {
                return;
            }
            reallocate();
        }
    }

    // ---------------------------------------------------------------- 5-7 최종 검증 및 확정

    private void validateTables() {
        // H7: 같은 사용자의 신청이 같은 날짜 테이블에 둘 이상이면 application ID가 큰 쪽을 뺀다 (matching.md 11장 I7).
        Map<String, Long> firstByUserDate = new HashMap<>();
        List<Candidate> placed = tables.stream().flatMap(t -> t.members.stream()).sorted(BY_ID).toList();
        for (Candidate c : placed) {
            TempTable t = tableOf(c);
            String key = c.userId() + "@" + slots.get(t.slotId).date();
            if (firstByUserDate.putIfAbsent(key, c.applicationId()) != null) {
                set(t, without(t.members, c));
                unplaced.put(c.applicationId(), ReasonCode.DUPLICATE_DATE);
            }
        }

        for (TempTable t : List.copyOf(tables)) {
            if (t.size() > TABLE_MAX || !HardConstraints.allChose(t.slotId, t.members)) {
                throw new IllegalStateException("엔진 내부 오류: 테이블 인원 초과 또는 미선택 회차 배치 " + t.slotId);
            }
            if (t.size() < TABLE_MIN) {
                dissolve(t, ReasonCode.UNDER_MIN_SIZE);
            } else if (!HardConstraints.ageGapOk(t.members)) {
                dissolve(t, ReasonCode.AGE_GAP);
            } else if (conflicts(t.members) > 0) {
                dissolve(t, ReasonCode.BLOCKED_PAIR);
            } else if (belowMinScore(t)) {
                dissolve(t, ReasonCode.LOW_SCORE);
            }
        }
    }

    private List<ProposedTable> confirmTables() {
        List<TempTable> ordered = tables.stream()
                .sorted(Comparator.comparingLong((TempTable t) -> t.slotId).thenComparingInt(t -> t.seq))
                .toList();
        List<ProposedTable> proposed = new ArrayList<>();
        for (TempTable t : ordered) {
            for (Candidate m : t.members) {
                Relocation r = relocations.get(m.applicationId());
                if (r == null) {
                    log(m.applicationId(), PlacementDecision.CONFIRMED, null, t.slotId, null,
                            ReasonCode.PLACED_FIRST_CHOICE, null);
                } else {
                    log(m.applicationId(), PlacementDecision.CONFIRMED, r.fromSlotId(), t.slotId, r.step(),
                            r.reason(), r.cause());
                }
            }
            proposed.add(new ProposedTable(
                    t.slotId,
                    t.members.stream().map(Candidate::applicationId).toList(),
                    t.score(),
                    t.detail,
                    TableSummarizer.summarize(t.slotId, t.members, encounters, blocked)));
        }
        return proposed;
    }

    // ---------------------------------------------------------------- 임시 테이블

    private static final class TempTable {
        final int seq;
        final long slotId;
        /** application ID 오름차순. */
        List<Candidate> members = List.of();
        /** 2명 미만이면 null. */
        ScoreDetail detail;

        TempTable(int seq, long slotId) {
            this.seq = seq;
            this.slotId = slotId;
        }

        int size() {
            return members.size();
        }

        double score() {
            return detail == null ? 0 : detail.total();
        }
    }

    private TempTable newTable(long slotId, List<Candidate> members) {
        TempTable t = new TempTable(nextTableSeq++, slotId);
        set(t, members);
        return t;
    }

    private void set(TempTable t, List<Candidate> members) {
        t.members = members.stream().sorted(BY_ID).toList();
        t.detail = t.members.size() >= 2 ? TableScorer.score(t.members, encounters, wantAgain, config.weights()) : null;
    }

    private void dissolve(TempTable t, ReasonCode reason) {
        tables.remove(t);
        dissolvedTables++;
        t.members.forEach(m -> unplaced.put(m.applicationId(), reason));
    }

    private TempTable tableOf(Candidate c) {
        return tables.stream().filter(t -> t.members.contains(c)).findFirst().orElseThrow();
    }

    private boolean belowMinScore(TempTable t) {
        return t.score() < config.minScore() - EPS;
    }

    // ---------------------------------------------------------------- 공통

    private double score(List<Candidate> members) {
        return TableScorer.total(members, encounters, wantAgain, config.weights());
    }

    private int conflicts(List<Candidate> members) {
        return HardConstraints.conflicts(members, blocked);
    }

    private static List<Candidate> replace(List<Candidate> members, Candidate out, Candidate in) {
        List<Candidate> next = new ArrayList<>(members);
        next.remove(out);
        next.add(in);
        next.sort(BY_ID);
        return next;
    }

    private static List<Candidate> with(List<Candidate> members, Candidate in) {
        List<Candidate> next = new ArrayList<>(members);
        next.add(in);
        next.sort(BY_ID);
        return next;
    }

    private static List<Candidate> without(List<Candidate> members, Candidate out) {
        return members.stream().filter(m -> m.applicationId() != out.applicationId()).sorted(BY_ID).toList();
    }

    private void log(long applicationId, PlacementDecision decision, Long fromSlotId, Long toSlotId,
            Integer reallocStep, ReasonCode reason, ReasonCode cause) {
        logs.add(new PlacementLog(applicationId, decision, fromSlotId, toSlotId, reallocStep, reason, cause,
                reason.text()));
    }
}
