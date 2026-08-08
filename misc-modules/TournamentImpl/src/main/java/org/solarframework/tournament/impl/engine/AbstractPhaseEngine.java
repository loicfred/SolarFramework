package org.solarframework.tournament.impl.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.tournament.api.BracketSide;
import org.solarframework.tournament.api.IPhaseEngine;
import org.solarframework.tournament.api.MatchState;
import org.solarframework.tournament.impl.StandingsCalculator;
import org.solarframework.tournament.impl.obj.Match;
import org.solarframework.tournament.impl.obj.Standing;
import org.solarframework.tournament.obj.IMatch;
import org.solarframework.tournament.obj.IParticipant;
import org.solarframework.tournament.obj.IPhase;
import org.solarframework.tournament.obj.IStanding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Shared plumbing: match creation, progression links, bye resolution and numbering. */
public abstract class AbstractPhaseEngine implements IPhaseEngine {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // ---- construction ---------------------------------------------------------------------------

    protected IMatch newMatch(IPhase phase, BracketSide side, int round, int position) {
        IMatch m = new Match(phase, side, round, position);
        phase.getMatches().add(m);
        return m;
    }

    /** Winner of {@code from} goes to slot {@code slot} of {@code to}. */
    protected void linkWinner(IMatch from, IMatch to, int slot) {
        from.setNextMatchID(to.getID());
        from.setNextMatchSlot(slot);
        if (slot == 1) to.setSourceMatchID1(from.getID()); else to.setSourceMatchID2(from.getID());
    }

    /** Loser of {@code from} drops into slot {@code slot} of {@code to} - double elimination and third place. */
    protected void linkLoser(IMatch from, IMatch to, int slot) {
        from.setNextLoserMatchID(to.getID());
        from.setNextLoserMatchSlot(slot);
    }

    /** Fills in the "Winner of M7" / "Loser of M7" placeholders once numbering is done. */
    protected void labelSlots(IPhase phase) {
        for (IMatch m : phase.getMatches()) {
            if (m.getNextMatchID() != null) phase.getMatch(m.getNextMatchID()).ifPresent(n -> setSlotLabel(n, m.getNextMatchSlot(), "Winner of M" + m.getMatchNumber()));
            if (m.getNextLoserMatchID() != null) phase.getMatch(m.getNextLoserMatchID()).ifPresent(n -> setSlotLabel(n, m.getNextLoserMatchSlot(), "Loser of M" + m.getMatchNumber()));
        }
    }

    private void setSlotLabel(IMatch m, Integer slot, String label) {
        if (slot == null) return;
        if (slot == 1) m.setSlot1Label(label); else m.setSlot2Label(label);
    }

    /** Sequential, stable match numbers used in labels and on the rendered bracket. */
    protected void numberMatches(IPhase phase) {
        List<IMatch> ms = new ArrayList<>(phase.getMatches());
        ms.sort(Comparator.comparingInt((IMatch m) -> switch (m.getBracketSide()) {
            case GROUP, SWISS, WINNERS -> 0;
            case LOSERS -> 1;
            case THIRD_PLACE -> 2;
            case GRAND_FINAL -> 3;
            case GRAND_FINAL_RESET -> 4;
        }).thenComparingInt(IMatch::getRound).thenComparingInt(IMatch::getPosition));
        for (int i = 0; i < ms.size(); i++) ms.get(i).setMatchNumber(i + 1);
    }

    // ---- progression ----------------------------------------------------------------------------

    /**
     * Pushes a decided match's winner (and loser, where linked) into the matches downstream.
     * @return the downstream matches that changed
     */
    protected List<IMatch> propagate(IPhase phase, IMatch m) {
        List<IMatch> changed = new ArrayList<>();
        if (m.getWinnerID() != null && m.getNextMatchID() != null && m.getNextMatchSlot() != null) {
            phase.getMatch(m.getNextMatchID()).ifPresent(n -> { n.setParticipant(m.getNextMatchSlot(), m.getWinnerID()); changed.add(n); });
        }
        if (m.getLoserID() != null && m.getNextLoserMatchID() != null && m.getNextLoserMatchSlot() != null) {
            phase.getMatch(m.getNextLoserMatchID()).ifPresent(n -> { n.setParticipant(m.getNextLoserMatchSlot(), m.getLoserID()); changed.add(n); });
        }
        return changed;
    }

    /**
     * Settles everything that follows mechanically from the current fill state: a match whose only
     * possible opponent will never arrive becomes a bye, one that can never be filled at all is
     * cancelled. Runs to a fixpoint because a bye can create another bye downstream.
     */
    protected List<IMatch> resolveByes(IPhase phase) {
        List<IMatch> touched = new ArrayList<>();
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 64) {
            changed = false;
            for (IMatch m : new ArrayList<>(phase.getMatches())) {
                MatchState st = m.getState();
                if (st.isDecided() || st == MatchState.CANCELLED) continue;
                boolean has1 = m.getParticipantID1() != null, has2 = m.getParticipantID2() != null;
                if (has1 && has2) continue;
                boolean live1 = has1 || slotCanFill(phase, m, 1);
                boolean live2 = has2 || slotCanFill(phase, m, 2);
                if (!live1 && !live2) {
                    m.setState(MatchState.CANCELLED);
                    log.debug("Cancelled unreachable match {} in phase {}", m.getMatchNumber(), phase.getID());
                } else if ((has1 && !live2) || (has2 && !live1)) {
                    m.awardBye();
                    log.debug("Bye awarded in match {} to {}", m.getMatchNumber(), m.getWinnerID());
                } else continue;
                touched.add(m);
                touched.addAll(propagate(phase, m));
                changed = true;
            }
        }
        return touched;
    }

    /**
     * Replays every decided match's feed in bracket order, then settles the byes that follow from it.
     *
     * <p>Unlike {@link #propagate}, this <b>only fills an empty slot</b>. A slot that already holds
     * somebody is left exactly as it is, decided downstream or not: a match reported before the one
     * feeding it is the very case this exists for, and overwriting its entrant would leave a result
     * pointing at somebody who was not in it.
     */
    @Override
    public List<IMatch> repair(IPhase phase) {
        List<IMatch> changed = new ArrayList<>();
        List<IMatch> ordered = new ArrayList<>(phase.getMatches());
        ordered.sort(Comparator.comparingInt(IMatch::getRound).thenComparingInt(IMatch::getPosition));
        for (IMatch m : ordered) {
            if (!m.getState().isDecided()) continue;
            if (m.getWinnerID() != null && m.getNextMatchID() != null && m.getNextMatchSlot() != null)
                phase.getMatch(m.getNextMatchID()).filter(n -> n.getParticipantID(m.getNextMatchSlot()) == null)
                        .ifPresent(n -> { n.setParticipant(m.getNextMatchSlot(), m.getWinnerID()); changed.add(n); });
            if (m.getLoserID() != null && m.getNextLoserMatchID() != null && m.getNextLoserMatchSlot() != null)
                phase.getMatch(m.getNextLoserMatchID()).filter(n -> n.getParticipantID(m.getNextLoserMatchSlot()) == null)
                        .ifPresent(n -> { n.setParticipant(m.getNextLoserMatchSlot(), m.getLoserID()); changed.add(n); });
        }
        for (IMatch m : resolveByes(phase)) if (!changed.contains(m)) changed.add(m);
        if (!changed.isEmpty()) log.info("Repaired phase {}: {} match(es) relinked", phase.getID(), changed.size());
        return changed;
    }

    /** Whether any upstream match could still deliver an entrant into this slot. */
    private boolean slotCanFill(IPhase phase, IMatch m, int slot) {
        for (IMatch x : phase.getMatches()) {
            if (x == m) continue;
            if (Objects.equals(x.getNextMatchID(), m.getID()) && x.getNextMatchSlot() != null && x.getNextMatchSlot() == slot
                    && x.getState() != MatchState.CANCELLED) return true;
            // A bye produces no loser, so a loser-feed from one is already dead.
            if (Objects.equals(x.getNextLoserMatchID(), m.getID()) && x.getNextLoserMatchSlot() != null && x.getNextLoserMatchSlot() == slot
                    && x.getState() != MatchState.CANCELLED && x.getState() != MatchState.BYE) return true;
        }
        return false;
    }

    // ---- standings ------------------------------------------------------------------------------

    /** Creates one standings row per entrant, all in group 0. */
    protected void seedStandings(IPhase phase, List<IParticipant> entrants) {
        for (int i = 0; i < entrants.size(); i++) phase.getStandings().add(new Standing(phase, entrants.get(i).getID(), 0, i + 1));
    }

    @Override
    public List<IStanding> rank(IPhase phase) { return StandingsCalculator.recompute(phase); }

    @Override
    public boolean isComplete(IPhase phase) { return phase.allMatchesDecided(); }

    @Override
    public List<IParticipant> getQualifiers(IPhase phase) {
        List<IStanding> ranked = rank(phase).stream().sorted(Comparator.comparingInt(IStanding::getRank)).toList();
        int limit = Math.min(ranked.size(), phase.getEffectiveAdvanceTotal());
        List<IParticipant> out = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            IStanding s = ranked.get(i);
            boolean in = i < limit;
            s.setQualified(in);
            s.setEliminated(!in);
            if (in) s.getParticipant().ifPresent(out::add);
        }
        return out;
    }
}
