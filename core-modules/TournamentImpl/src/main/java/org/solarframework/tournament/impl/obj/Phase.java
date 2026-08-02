package org.solarframework.tournament.impl.obj;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.solarframework.db.spring.DatabaseObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.tournament.api.IPhaseEngine;
import org.solarframework.tournament.api.PhaseStatus;
import org.solarframework.tournament.api.PhaseType;
import org.solarframework.tournament.api.TournamentException;
import org.solarframework.tournament.impl.StandingsCalculator;
import org.solarframework.tournament.impl.engine.PhaseEngines;
import org.solarframework.tournament.impl.seed.Seeder;
import org.solarframework.tournament.obj.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Concrete {@link IPhase}. Generation and progression delegate to the format's {@link IPhaseEngine}. */
@Entity
@DiscriminatorValue("0")
public class Phase extends IPhase {
    private static final Logger log = LoggerFactory.getLogger(Phase.class);

    protected Phase() {}

    public Phase(ITournament tournament, String name, PhaseType type, int orderIndex) { super(tournament, name, type, orderIndex); }

    /**
     * Writes this phase, its table and every match (with its games) - four statements rather than one per
     * row, since a 64-entrant phase is otherwise well over a hundred round trips for a single save.
     */
    @Override
    public void save() {
        Upsert();
        DatabaseObject.UpsertAll(getStandings());
        DatabaseObject.UpsertAll(getMatches());
        DatabaseObject.UpsertAll(getMatches().stream().flatMap(m -> m.getGames().stream()).toList());
    }

    /** Slots entrants into this phase and generates its matches. */
    @Override
    public void generate(List<IParticipant> entrants) {
        IPhaseEngine engine = PhaseEngines.of(getType());
        engine.generate(this, entrants);
        setStatus(PhaseStatus.RUNNING);
        save();
    }

    /** Regenerates this phase from scratch, discarding all its matches and standings. */
    @Override
    public void regenerate() {
        List<IParticipant> entrants = getParticipants();
        if (entrants.isEmpty()) throw TournamentException.of("Phase '%s' has no entrants to regenerate from", getName());
        clearForRegeneration();
        generate(entrants);
        log.info("Regenerated phase '{}'", getName());
    }

    private void clearForRegeneration() {
        for (IMatch m : getMatches()) { m.getGames().forEach(DatabaseObject::Delete); m.Delete(); }
        getStandings().forEach(DatabaseObject::Delete);
        getMatches().clear();
        getStandings().clear();
        setStatus(PhaseStatus.PENDING);
        setCurrentRound(0);
    }

    /** Recomputes this phase's table from its matches, in memory only - persisted by the next {@link #save()}. */
    List<IStanding> recompute() { return StandingsCalculator.recompute(this); }

    /** Recomputes, persists and returns this phase's table, ranked. */
    @Override
    public List<IStanding> recomputeStandings() {
        List<IStanding> rows = recompute();
        DatabaseObject.UpsertAll(rows);
        return rows.stream().sorted(Comparator.comparingInt(IStanding::getGroupIndex).thenComparingInt(IStanding::getRank)).toList();
    }

    @Override
    public List<IStanding> recomputeStandings(int groupIndex) {
        return recomputeStandings().stream().filter(s -> s.getGroupIndex() == groupIndex).toList();
    }

    /** Ranks and closes the phase once all its matches are decided, then advances unless the tournament opted out. */
    @Override
    public boolean tryComplete() {
        IPhaseEngine engine = PhaseEngines.of(getType());
        if (getStatus().isComplete() || !engine.isComplete(this)) return false;
        engine.rank(this);
        setStatus(PhaseStatus.COMPLETE);
        save();
        log.info("Phase '{}' complete", getName());

        ITournament tour = getTournament();
        if (tour != null && tour.isAutoAdvancePhases()) advanceToNext();
        return true;
    }

    /**
     * Hands this phase's qualifiers to the next one, or finishes the tournament when it was the last.
     * Split out of {@link #tryComplete()} so a consumer running with auto advance off can take the same
     * step on its own terms - the organiser pressing "next phase" rather than the last score doing it.
     *
     * @return false when there is nothing to advance: the phase is not closed yet, or the next one is drawn already.
     */
    @Override
    public boolean advanceToNext() {
        ITournament tour = getTournament();
        if (tour == null || !getStatus().isComplete()) return false;
        Optional<IPhase> next = tour.getPhase(getOrderIndex() + 1);
        if (next.isEmpty()) {
            if (!tour.getStatus().isFinished()) tour.finish();
            return true;
        }
        if (next.get().isStarted()) return false;
        List<IParticipant> qualifiers = PhaseEngines.of(getType()).getQualifiers(this);
        List<IParticipant> field = Seeder.applySeedNumbers(new ArrayList<>(qualifiers));
        ((Tournament) tour).markEliminated(this, qualifiers);
        tour.setCurrentPhaseIndex(next.get().getOrderIndex());
        next.get().generate(field);
        tour.Upsert();
        log.info("Advanced '{}' into phase '{}' with {} entrants", tour.getName(), next.get().getName(), field.size());
        return true;
    }
}
