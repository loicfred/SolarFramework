package org.solarframework.tournament.impl.engine;

import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.SeedingMethod;
import org.solarframework.tournament.api.TournamentException;
import org.solarframework.tournament.impl.StandingsCalculator;
import org.solarframework.tournament.impl.obj.Standing;
import org.solarframework.tournament.impl.seed.Seeder;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Round robin, optionally split into several groups. Fixtures come from the circle method: fix the
 * first entrant, rotate the rest, and every round is a valid full pairing. An odd group gets a
 * rotating bye.
 *
 * <p>Serves {@link PhaseType#ROUND_ROBIN} (a single group) and {@link PhaseType#GROUP} (many).
 */
public class GroupEngine extends AbstractPhaseEngine {

    private final PhaseType type;

    public GroupEngine(PhaseType type) { this.type = type; }
    public GroupEngine() { this(PhaseType.GROUP); }

    @Override
    public PhaseType type() { return type; }

    @Override
    public void generate(IPhase phase, List<IParticipant> entrants) {
        if (entrants.size() < 2) throw TournamentException.of("A group phase needs at least 2 entrants, got %d", entrants.size());
        int groups = type == PhaseType.ROUND_ROBIN ? 1 : resolveGroupCount(phase, entrants.size());
        phase.setGroupCount(groups);
        phase.setParticipantCount(entrants.size());
        boolean snake = phase.getSeedingMethod() != SeedingMethod.ORDER;
        List<List<IParticipant>> split = Seeder.intoGroups(entrants, groups, snake);

        int maxRounds = 0;
        for (int g = 0; g < split.size(); g++) {
            List<IParticipant> members = split.get(g);
            for (int i = 0; i < members.size(); i++) phase.getStandings().add(new Standing(phase, members.get(i).getID(), g, i + 1));
            maxRounds = Math.max(maxRounds, scheduleGroup(phase, g, members));
        }
        phase.setTotalRounds(maxRounds);
        phase.setCurrentRound(1);
        numberMatches(phase);
        resolveByes(phase);
        StandingsCalculator.recompute(phase);
        log.info("Generated {} phase '{}': {} entrants across {} group(s), {} rounds, {} matches",
                type, phase.getName(), entrants.size(), groups, maxRounds, phase.getMatches().size());
    }

    /** Explicit group count wins; otherwise derive it from the requested group size. */
    private int resolveGroupCount(IPhase phase, int entrants) {
        if (phase.getGroupCount() > 1) return Math.min(phase.getGroupCount(), entrants);
        if (phase.getGroupSize() > 1) return Math.max(1, (int) Math.ceil((double) entrants / phase.getGroupSize()));
        return 1;
    }

    /**
     * Circle method. With an odd field a null placeholder is added; whoever is drawn against it
     * sits that round out, which spreads the bye evenly.
     * @return number of rounds scheduled
     */
    private int scheduleGroup(IPhase phase, int groupIndex, List<IParticipant> members) {
        List<IParticipant> arr = new ArrayList<>(members);
        if (arr.size() % 2 == 1) arr.add(null);
        int n = arr.size(), rounds = n - 1, half = n / 2;
        int legs = phase.isDoubleRoundRobin() ? 2 : 1;
        int position = 0;
        for (int leg = 0; leg < legs; leg++) {
            List<IParticipant> rot = new ArrayList<>(arr);
            for (int r = 0; r < rounds; r++) {
                for (int i = 0; i < half; i++) {
                    IParticipant a = rot.get(i), b = rot.get(n - 1 - i);
                    if (a == null || b == null) continue;
                    IMatch m = newMatch(phase, BracketSide.GROUP, leg * rounds + r + 1, position++);
                    m.setGroupIndex(groupIndex);
                    // Second leg swaps sides so home/away is balanced.
                    m.setParticipantID1(leg == 0 ? a.getID() : b.getID());
                    m.setParticipantID2(leg == 0 ? b.getID() : a.getID());
                }
                rot.add(1, rot.removeLast());
            }
        }
        return rounds * legs;
    }

    @Override
    public List<IMatch> onMatchDecided(IPhase phase, IMatch match) {
        StandingsCalculator.recompute(phase);
        phase.setCurrentRound(phase.getPendingMatches().stream().mapToInt(IMatch::getRound).min().orElse(phase.getTotalRounds()));
        return List.of();
    }

    /**
     * Qualifiers are taken group-position by group-position: every group winner first, then every
     * runner-up, and so on, which is what seeds a playoff bracket sensibly.
     */
    @Override
    public List<IParticipant> getQualifiers(IPhase phase) {
        List<IStanding> ranked = rank(phase);
        int perGroup = phase.getAdvancePerGroup();
        int total = phase.getEffectiveAdvanceTotal();
        List<IStanding> picked = new ArrayList<>();
        for (int i = 1; i <= Math.max(1, perGroup); i++) {
            int pos = i;
            picked.addAll(ranked.stream().filter(s -> s.getRank() == pos).sorted(orderAcrossGroups()).toList());
        }
        List<IStanding> qualified = picked.size() > total ? picked.subList(0, total) : picked;
        for (IStanding s : ranked) { s.setQualified(qualified.contains(s)); s.setEliminated(!qualified.contains(s)); }
        return qualified.stream().map(IStanding::getParticipant).flatMap(java.util.Optional::stream).toList();
    }

    /** Compares equally placed entrants from different groups - best record goes through highest. */
    private Comparator<IStanding> orderAcrossGroups() {
        return Comparator.comparingDouble(IStanding::getPoints).reversed()
                .thenComparing(Comparator.comparingInt(IStanding::getGameDiff).reversed())
                .thenComparing(Comparator.comparingInt(IStanding::getScoreDiff).reversed())
                .thenComparingInt(IStanding::getGroupIndex);
    }
}
