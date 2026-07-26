package org.solarframework.tournament.impl.obj;

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
public class Phase extends IPhase {
    private static final Logger log = LoggerFactory.getLogger(Phase.class);

    protected Phase() {}

    public Phase(ITournament tournament, String name, PhaseType type, int orderIndex) { super(tournament, name, type, orderIndex); }

    /** Writes this phase, its table and every match (with its games). */
    @Override
    public void save() {
        Upsert();
        getStandings().forEach(DatabaseObject::Upsert);
        for (IMatch m : getMatches()) m.save();
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
        rows.forEach(DatabaseObject::Upsert);
        return rows.stream().sorted(Comparator.comparingInt(IStanding::getGroupIndex).thenComparingInt(IStanding::getRank)).toList();
    }

    @Override
    public List<IStanding> recomputeStandings(int groupIndex) {
        return recomputeStandings().stream().filter(s -> s.getGroupIndex() == groupIndex).toList();
    }

    /** Advances the phase if all its matches are decided, handing qualifiers to the next one. */
    @Override
    public boolean tryComplete() {
        IPhaseEngine engine = PhaseEngines.of(getType());
        if (getStatus().isComplete() || !engine.isComplete(this)) return false;
        engine.rank(this);
        List<IParticipant> qualifiers = engine.getQualifiers(this);
        setStatus(PhaseStatus.COMPLETE);
        save();
        log.info("Phase '{}' complete - {} qualifier(s)", getName(), qualifiers.size());

        ITournament tour = getTournament();
        if (tour == null) return true;
        Optional<IPhase> next = tour.getPhase(getOrderIndex() + 1);
        if (next.isPresent()) {
            tour.setCurrentPhaseIndex(next.get().getOrderIndex());
            List<IParticipant> field = Seeder.applySeedNumbers(new ArrayList<>(qualifiers));
            ((Tournament) tour).markEliminated(this, qualifiers);
            next.get().generate(field);
            tour.Upsert();
            log.info("Advanced '{}' into phase '{}' with {} entrants", tour.getName(), next.get().getName(), field.size());
        } else tour.finish();
        return true;
    }
}
