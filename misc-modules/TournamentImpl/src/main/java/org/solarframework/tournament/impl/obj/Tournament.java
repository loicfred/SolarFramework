package org.solarframework.tournament.impl.obj;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.db.spring.DatabaseObject;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.impl.engine.PhaseEngines;
import org.solarframework.tournament.impl.seed.Seeder;
import org.solarframework.tournament.obj.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Concrete {@link ITournament}. Reporting a single result cascades from {@link Match}: the match is
 * decided, the format's engine advances the bracket or redraws the round, the table is recounted,
 * and the phase (then the tournament) closes out if there is nothing left to play.
 *
 * <p>Persistence goes straight through {@link DatabaseObject}: its write methods no-op when no
 * database is registered, so everything still runs in memory, which is what makes the engines
 * testable and the renderers usable offline.
 */
@Entity
@DiscriminatorValue("0")
public class Tournament extends ITournament {
    private static final Logger log = LoggerFactory.getLogger(Tournament.class);

    protected Tournament() {}

    public Tournament(String name) { super(name); }

    // ---- lifecycle ------------------------------------------------------------------------------

    public static Tournament create(String name) {
        Tournament t = new Tournament(name);
        log.info("Created tournament '{}' ({})", name, t.getID());
        t.save();
        return t;
    }

    public static Tournament create(String name, PhaseType type) {
        Tournament t = create(name);
        t.addPhase(defaultPhaseName(type), type);
        t.save();
        return t;
    }

    public static Tournament create(String name, PhaseType firstPhase, PhaseType secondPhase) {
        Tournament t = create(name);
        Phase a = t.addPhase(defaultPhaseName(firstPhase), firstPhase);
        a.setAdvancePerGroup(2);
        t.addPhase(defaultPhaseName(secondPhase), secondPhase);
        t.save();
        return t;
    }

    private static String defaultPhaseName(PhaseType type) {
        return switch (type) {
            case GROUP -> "Group Stage";
            case ROUND_ROBIN -> "Round Robin";
            case SWISS -> "Swiss";
            case SINGLE_ELIMINATION, DOUBLE_ELIMINATION -> "Playoffs";
        };
    }

    public static Optional<Tournament> load(Long tournamentID) {
        return retrieveEntityServiceFor(ITournament.class).getById(tournamentID).map(t -> (Tournament) t);
    }

    /**
     * Writes the whole aggregate: tournament, entrants, rosters, phases, tables, matches, games.
     *
     * <p>One batched statement per entity type instead of one per row. A full save used to be
     * {@code 1 + entrants + roster + phases * (1 + standings + matches * (1 + games))} round trips, which
     * is several hundred for a 64-entrant run and is paid on every start, every reported score and every
     * phase advance.
     */
    @Override
    public void save() {
        Upsert();
        List<IParticipant> entrants = getParticipants();
        DatabaseObject.UpsertAll(entrants);
        DatabaseObject.UpsertAll(entrants.stream().flatMap(p -> p.getMembers().stream()).toList());
        List<IPhase> phases = getPhases();
        DatabaseObject.UpsertAll(phases);
        DatabaseObject.UpsertAll(phases.stream().flatMap(p -> p.getStandings().stream()).toList());
        List<IMatch> matches = phases.stream().flatMap(p -> p.getMatches().stream()).toList();
        DatabaseObject.UpsertAll(matches);
        DatabaseObject.UpsertAll(matches.stream().flatMap(m -> m.getGames().stream()).toList());
    }

    @Override
    public void deleteCascade(boolean hard) {
        log.info("Deleting tournament '{}' ({}) - {}", getName(), getID(), hard ? "hard" : "soft");
        // Every row of a run is an ID_RECORD_OBJ, so Delete() only stamps DeletedAt: the aggregate stops being
        // read but its matches, entrants and table stay in the table for good. Hard is what a caller offering
        // "delete it for real" needs - nothing is left to be found by an id lookup afterwards.
        java.util.function.Consumer<DatabaseObject.RECORD_OBJ<?>> drop = hard ? DatabaseObject.RECORD_OBJ::TrueDelete : DatabaseObject.RECORD_OBJ::Delete;
        for (IPhase phase : getPhases()) {
            for (IMatch m : phase.getMatches()) { m.getGames().forEach(drop); drop.accept(m); }
            phase.getStandings().forEach(drop);
            drop.accept(phase);
        }
        for (IParticipant p : getParticipants()) { p.getMembers().forEach(drop); drop.accept(p); }
        drop.accept(this);
    }

    @Override
    public void openRegistration() {
        if (getStatus().isLive() || getStatus().isFinished()) throw TournamentException.of("Cannot reopen registration on a %s tournament", getStatus());
        setStatus(TournamentStatus.REGISTRATION_OPEN);
        if (getRegistrationOpensAt() == null) setRegistrationOpensAt(Instant.now());
        Upsert();
        log.info("Registration open for '{}'", getName());
    }

    @Override
    public void closeRegistration() {
        setStatus(isCheckInRequired() ? TournamentStatus.CHECK_IN : TournamentStatus.REGISTRATION_CLOSED);
        if (getRegistrationClosesAt() == null) setRegistrationClosesAt(Instant.now());
        Upsert();
        log.info("Registration closed for '{}' with {} entrants", getName(), getParticipants().size());
    }

    @Override
    public void start() {
        if (getStatus().isLive()) throw TournamentException.of("Tournament '%s' is already running", getName());
        if (getStatus().isFinished()) throw TournamentException.of("Tournament '%s' is %s", getName(), getStatus());
        List<IParticipant> field = seed();
        if (field.size() < getMinParticipants()) throw TournamentException.of("'%s' needs %d entrants to start, has %d", getName(), getMinParticipants(), field.size());
        requireCompleteRosters(field);
        if (getPhases().isEmpty()) {
            log.info("'{}' has no phase defined - adding a single elimination bracket", getName());
            addPhase("Playoffs", PhaseType.SINGLE_ELIMINATION);
        }
        for (IParticipant p : field) if (p.getStatus() != ParticipantStatus.ACTIVE) p.setStatus(ParticipantStatus.ACTIVE);
        setStatus(TournamentStatus.RUNNING);
        setCurrentPhaseIndex(0);
        if (getStartsAt() == null) setStartsAt(Instant.now());
        getPhases().getFirst().generate(field);
        save();
        log.info("Started '{}' with {} entrants over {} phase(s)", getName(), field.size(), getPhases().size());
    }

    /**
     * A team may sign up with an empty or half-filled roster and complete it later, but it cannot play
     * short: the field is checked once, at the start, against the entrants that would actually take the
     * floor. Set {@code requireCompleteRosters} to false to let short teams play anyway.
     */
    private void requireCompleteRosters(List<IParticipant> field) {
        if (!isRequireCompleteRosters() || getTeamSize() <= 1) return;
        List<IParticipant> shortTeams = field.stream().filter(p -> !p.isRosterComplete()).toList();
        if (shortTeams.isEmpty()) return;
        String detail = shortTeams.stream().map(p -> "'" + p.getName() + "' (" + p.getRosterSize() + "/" + getTeamSize() + ")").collect(Collectors.joining(", "));
        throw TournamentException.of("'%s' cannot start with %d incomplete roster(s): %s", getName(), shortTeams.size(), detail);
    }

    /**
     * Writes every entrant's {@code finalRank} from the phases as they stand, and <em>only</em> that - none of
     * the status, timestamp or podium settling {@link #finish()} does around it. Split out so a run whose
     * placings predate a change to how a format ranks can be restamped without being finished a second time,
     * which would re-announce it and pay it out again.
     *
     * @return how many entrants actually moved; 0 means the stored placings were already right
     */
    @Override
    public int recomputeFinalRanks() {
        LinkedHashMap<IParticipant, String> placings = rankingWithTieKeys();
        List<IParticipant> ranking = new ArrayList<>(placings.keySet());
        List<IParticipant> moved = new ArrayList<>();
        // A phase that ranked two entrants equal (both semifinal losers of a bracket with no third place match)
        // places them equal here too, and the tie then skips the ranks it consumed: 1, 2, 3, 3, 5.
        for (int i = 0, rank = 0; i < ranking.size(); i++) {
            String key = placings.get(ranking.get(i));
            if (key == null || i == 0 || !key.equals(placings.get(ranking.get(i - 1)))) rank = i + 1;
            if (ranking.get(i).getFinalRank() == rank) continue;
            ranking.get(i).setFinalRank(rank);
            moved.add(ranking.get(i));
        }
        // The placements are read back by callers and after a restart, so they have to be written, not just held
        // in memory - and the podium is derived from them. UpsertAll takes its table off the head of the list.
        if (!moved.isEmpty()) DatabaseObject.UpsertAll(moved);
        return moved.size();
    }

    @Override
    public void finish() {
        recomputeFinalRanks();
        setStatus(TournamentStatus.COMPLETE);
        setEndsAt(Instant.now());
        save();
        log.info("'{}' complete - winner: {}", getName(), getWinner().map(IParticipant::getDisplayName).orElse("none"));
    }

    @Override
    public void cancel(String reason) {
        setStatus(TournamentStatus.CANCELLED);
        setEndsAt(Instant.now());
        save();
        log.info("'{}' cancelled: {}", getName(), reason);
    }

    /**
     * Rebuilds everything about a run that is <em>derived</em> from its matches, for a run whose state
     * drifted: a phase imported match by match rather than generated (so it never got a table), a bracket
     * whose later match was reported before the one feeding it (so the winner was never pushed forward),
     * a phase that played itself out but was never closed.
     *
     * <p><b>Nothing here discards a reported result.</b> Every step only ever fills something in:
     * standings rows are created, never dropped; a downstream slot is filled only when empty; a phase is
     * regenerated only when it holds no decided match at all, so there is nothing to lose. A phase that is
     * structurally broken <em>and</em> already has results is reported instead of touched — that call
     * belongs to the organiser, not to a repair button.
     *
     * <p>It also stops one step short of the end: a phase that is finished gets ranked and closed
     * ({@link Phase#tryComplete()}), but the tournament itself is never finished. {@link #finish()} settles
     * the podium and is what consumers hang their payouts and announcements off, so it stays an explicit
     * action — repair only reports that the run is ready for it.
     *
     * @return one line per thing fixed or refused, ready to show as-is; empty when the run was already sound
     */
    @Override
    public List<String> repair() {
        List<String> report = new ArrayList<>();
        for (IPhase phase : getPhases()) {
            if (!phase.getStatus().hasStarted()) continue;
            String tag = "Phase " + (phase.getOrderIndex() + 1) + " '" + phase.getName() + "': ";

            // A standings row is only ever created by the engine in generate(); a phase built any other way has
            // none, and StandingsCalculator re-ranks the rows it finds rather than creating them - so a phase
            // with no table stays empty through every recount until the rows exist. The field is read off the
            // matches, not off getParticipants(), which is itself derived from the very standings that are missing.
            List<Long> known = new ArrayList<>(phase.getStandings().stream().map(IStanding::getParticipantID).toList());
            List<IStanding> added = new ArrayList<>();
            for (IMatch m : phase.getMatches())
                for (Long pid : List.of(m.getParticipantID1() == null ? -1L : m.getParticipantID1(), m.getParticipantID2() == null ? -1L : m.getParticipantID2())) {
                    if (pid < 0 || known.contains(pid) || getParticipant(pid).isEmpty()) continue;
                    known.add(pid);
                    added.add(new Standing(phase, pid, m.getGroupIndex() == null ? 0 : m.getGroupIndex(), known.size()));
                }
            if (!added.isEmpty()) { phase.getStandings().addAll(added); report.add(tag + "created " + added.size() + " missing standings row(s)"); }

            // A phase that never drew its matches: safe to rebuild only while there is no result in it.
            if (phase.getMatches().isEmpty()) {
                if (phase.getParticipants().isEmpty()) report.add(tag + "no matches and no entrants to rebuild from - not touched");
                else { phase.regenerate(); report.add(tag + "no matches at all - rebuilt the phase from its " + phase.getParticipants().size() + " entrant(s)"); }
                continue;
            }

            int relinked = PhaseEngines.of(phase.getType()).repair(phase).size();
            if (relinked > 0) report.add(tag + "filled " + relinked + " match slot(s) the results had already decided");

            // Dangling progression links are the one structural break that cannot be inferred back: the target
            // match is simply not there, and which pairing it was is not recoverable from what is left.
            long dangling = phase.getMatches().stream().filter(m -> (m.getNextMatchID() != null && phase.getMatch(m.getNextMatchID()).isEmpty())
                    || (m.getNextLoserMatchID() != null && phase.getMatch(m.getNextLoserMatchID()).isEmpty())).count();
            if (dangling > 0) report.add(tag + dangling + " match(es) point at a match that is missing from this phase - it has results, so the bracket was left alone. Rebuild the phase by hand if the pairings are wrong");

            phase.recomputeStandings();
            if (phase.tryComplete()) report.add(tag + "every match is decided - ranked and closed the phase");
        }

        // Deliberately not finish(): the podium, the payouts and the announcements ride on it.
        if (getStatus() == TournamentStatus.RUNNING && !getPhases().isEmpty() && getPhases().stream().allMatch(p -> p.getStatus().isComplete()))
            report.add("Every phase is complete - the run is ready to be completed, which is left to you as it settles the podium.");

        save();
        log.info("Repaired '{}': {}", getName(), report.isEmpty() ? "nothing to fix" : String.join(" | ", report));
        return report;
    }

    // ---- registration ---------------------------------------------------------------------------

    @Override
    public Participant register(String name) { return register(name, 0); }

    /** Takes a slot in the field, or a place in the queue behind it when the field is already full. */
    @Override
    public Participant register(String name, int seed) {
        boolean full = isFull();
        if (!isRegistrationWindowOpen()) throw TournamentException.of("Registration is closed for '%s' (%s)", getName(), getStatus());
        if (full && !isWaitlistEnabled()) throw TournamentException.of("Registration is closed for '%s' (%s, field full)", getName(), getStatus());
        if (getParticipantByName(name).isPresent()) throw TournamentException.of("'%s' is already registered in '%s'", name, getName());
        Participant p = new Participant(this, name, seed);
        if (full) p.setStatus(ParticipantStatus.WAITLISTED);
        getParticipants().add(p);
        p.Upsert();
        if (full) log.info("'{}' joined the waiting list of '{}' at position {}", name, getName(), getWaitlistPosition(p));
        else log.info("Registered '{}' in '{}' ({} entrants)", name, getName(), getEntrants().size());
        return p;
    }

    /**
     * Moves waiting entrants into the field while there is room, in join order. Runs on every
     * unregistration, and can be called directly after raising the cap or clearing an entrant out
     * some other way. A started tournament has its bracket built already, so nobody is pulled in
     * once play is under way unless late registration is explicitly allowed.
     */
    @Override
    public List<IParticipant> promoteFromWaitlist() {
        if (getStatus().isFinished() || (getStatus().isLive() && !isAllowLateRegistration())) return List.of();
        List<IParticipant> promoted = new ArrayList<>();
        for (IParticipant p : getWaitlist()) {
            if (isFull()) break;
            p.setStatus(p.isCheckedIn() ? ParticipantStatus.CHECKED_IN : ParticipantStatus.REGISTERED);
            p.Upsert();
            promoted.add(p);
            log.info("Promoted '{}' off the waiting list of '{}' ({} entrants, {} still waiting)", p.getName(), getName(), getEntrants().size(), getWaitlist().size());
        }
        return promoted;
    }

    /** Registers a team entrant and rosters its members in one call. */
    @Override
    public Participant registerTeam(String teamName, List<String> memberNames) {
        Participant p = register(teamName);
        if (memberNames != null) for (String m : memberNames) p.addMember(m);
        p.getMembers().forEach(DatabaseObject::Upsert);
        if (!p.isRosterComplete()) log.warn("Team '{}' rostered {} of the required {} players", teamName, p.getMembers().size(), getTeamSize());
        return p;
    }

    /** Pre-start only, so the entrant never played: they and their roster are hard-deleted rather than kept as a soft-deleted record. */
    @Override
    public void unregister(IParticipant p) {
        if (getStatus().isLive()) { withdraw(p); return; }
        getParticipants().remove(p);
        p.getMembers().forEach(IParticipantMember::TrueDelete);
        p.TrueDelete();
        log.info("Unregistered '{}' from '{}'", p.getName(), getName());
        promoteFromWaitlist();
    }

    /** Removes an entrant mid-play, awarding walkovers on their remaining matches. */
    @Override
    public void withdraw(IParticipant p) {
        p.withdraw();
        forfeitRemaining(p);
        log.info("'{}' withdrew from '{}'", p.getName(), getName());
    }

    @Override
    public void disqualify(IParticipant p, String reason) {
        p.setStatus(ParticipantStatus.DISQUALIFIED);
        forfeitRemaining(p);
        log.info("'{}' disqualified from '{}': {}", p.getName(), getName(), reason);
    }

    /** Every unplayed match involving the entrant is awarded to their opponent. */
    private void forfeitRemaining(IParticipant p) {
        for (IPhase phase : getPhases()) {
            for (IMatch m : new ArrayList<>(phase.getMatches())) {
                if (m.getState().isDecided() || !m.hasParticipant(p.getID())) continue;
                Long opponent = m.getOpponentID(p.getID()).orElse(null);
                if (opponent == null) { m.setState(MatchState.CANCELLED); m.Upsert(); continue; }
                m.awardWalkover(opponent);
                ((Match) m).afterResult();
            }
        }
        p.Upsert();
        save();
    }

    // ---- seeding & generation -------------------------------------------------------------------

    /** Applies this tournament's seeding method, assigning 1..n seed numbers. */
    @Override
    public List<IParticipant> seed() {
        List<IParticipant> field = getParticipants().stream()
                .filter(p -> p.getStatus().isPlayable())
                .filter(p -> !isCheckInRequired() || p.isCheckedIn())
                .toList();
        List<IParticipant> ordered = Seeder.applySeedNumbers(Seeder.order(field, getSeedingMethod(), getRandomSeed(), null));
        ordered.forEach(DatabaseObject::Upsert);
        log.debug("Seeded {} entrants for '{}' using {}", ordered.size(), getName(), getSeedingMethod());
        return ordered;
    }

    /** Appends a phase, inheriting this tournament's ruleset as its defaults. */
    @Override
    public Phase addPhase(String name, PhaseType type) {
        Phase phase = new Phase(this, name, type, getPhases().size());
        getPhases().add(phase);
        return phase;
    }

    // ---- queries --------------------------------------------------------------------------------

    /** Matches that can be played right now across the whole tournament. */
    @Override
    public List<IMatch> getPlayableMatches() {
        return getPhases().stream().filter(p -> p.getStatus().hasStarted()).flatMap(p -> p.getPlayableMatches().stream()).toList();
    }

    @Override
    public List<IMatch> getMatchesFor(IParticipant p) {
        return getMatches().stream().filter(m -> m.hasParticipant(p.getID())).toList();
    }

    @Override
    public Optional<IMatch> getNextMatchFor(IParticipant p) {
        return getMatchesFor(p).stream().filter(m -> !m.getState().isDecided() && m.getState() != MatchState.CANCELLED)
                .min(Comparator.comparingInt(IMatch::getRound).thenComparingInt(IMatch::getPosition));
    }

    /** Final placement of every entrant, best first. */
    @Override
    public List<IParticipant> getFinalRanking() {
        if (getStatus() == TournamentStatus.COMPLETE) {
            List<IParticipant> ranked = new ArrayList<>(getParticipants().stream().filter(p -> p.getFinalRank() > 0).toList());
            ranked.sort(Comparator.comparingInt(IParticipant::getFinalRank));
            if (!ranked.isEmpty()) return ranked;
        }
        return computeFinalRanking();
    }

    private List<IParticipant> computeFinalRanking() { return new ArrayList<>(rankingWithTieKeys().keySet()); }

    /**
     * Ranks the last phase that has been played, then appends everyone eliminated earlier in
     * reverse phase order, so entrants knocked out in the group stage still get a placement.
     *
     * <p>Each entrant is mapped to the phase and table rank it was placed by: two entrants carrying the
     * same key were ranked equal by that phase and share a placing. A null key is an entrant no phase
     * ranked at all, which ties with nobody.
     */
    private LinkedHashMap<IParticipant, String> rankingWithTieKeys() {
        LinkedHashMap<IParticipant, String> out = new LinkedHashMap<>();
        List<IPhase> phases = new ArrayList<>(getPhases());
        Collections.reverse(phases);
        for (IPhase phase : phases) {
            if (!phase.getStatus().hasStarted()) continue;
            for (IStanding s : PhaseEngines.of(phase.getType()).rank(phase).stream().sorted(Comparator.comparingInt(IStanding::getRank)).toList()) {
                s.getParticipant().filter(p -> !out.containsKey(p)).ifPresent(p -> out.put(p, phase.getID() + ":" + s.getGroupIndex() + ":" + s.getRank()));
            }
        }
        getParticipants().stream().filter(p -> !out.containsKey(p)).forEach(p -> out.put(p, null));
        return out;
    }

    // ---- called back into from Phase / Match ------------------------------------------------------

    /** Marks every entrant not among {@code qualifiers} as eliminated in {@code phase}. */
    void markEliminated(IPhase phase, List<IParticipant> qualifiers) {
        for (IParticipant p : getParticipants()) {
            if (!p.getStatus().isPlayable() || qualifiers.contains(p)) continue;
            p.eliminate(phase.getID(), phase.getCurrentRound());
            p.Upsert();
        }
    }
}
