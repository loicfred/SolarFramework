package org.solarframework.tournament.impl;

import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.*;
import org.solarframework.tournament.impl.obj.Tournament;
import org.solarframework.tournament.obj.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end flow through the {@link Tournament} entity - no database registered, everything in memory. */
class TournamentTest {

    private Tournament open(String name, PhaseType type, int entrants) {
        Tournament t = Tournament.create(name, type);
        t.openRegistration();
        for (int i = 1; i <= entrants; i++) t.register("P" + i);
        t.closeRegistration();
        return t;
    }

    /** Always plays the better (lower) seed to win - makes every format's outcome deterministic. */
    private void playToCompletion(Tournament t) {
        int guard = 0;
        while (t.getStatus() != TournamentStatus.COMPLETE && guard++ < 200) {
            List<IMatch> playable = t.getPlayableMatches();
            if (playable.isEmpty()) break;
            for (IMatch m : playable) {
                IParticipant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
                boolean p1Wins = p1.getSeed() < p2.getSeed();
                m.reportResult(p1Wins ? 1 : 0, p1Wins ? 0 : 1);
            }
        }
    }

    @Test
    void singleEliminationFullFlowEndsWithASeededWinner() {
        Tournament t = open("Cup", PhaseType.SINGLE_ELIMINATION, 6);
        t.start();
        playToCompletion(t);
        assertEquals(TournamentStatus.COMPLETE, t.getStatus());
        assertEquals("P1", t.getWinner().orElseThrow().getName());
    }
    @Test
    void doubleEliminationFullFlowEndsWithASeededWinner() {
        Tournament t = open("Double Elim Cup", PhaseType.DOUBLE_ELIMINATION, 6);
        t.start();
        playToCompletion(t);
        assertEquals(TournamentStatus.COMPLETE, t.getStatus());
        assertEquals("P1", t.getWinner().orElseThrow().getName());
    }
    @Test
    void swissFullFlowCompletesAllRounds() {
        Tournament t = open("Swiss Open", PhaseType.SWISS, 8);
        t.start();
        playToCompletion(t);
        assertEquals(TournamentStatus.COMPLETE, t.getStatus());
        assertEquals("P1", t.getWinner().orElseThrow().getName());
    }
    @Test
    void singlePhaseGroupFlowRanksEveryEntrant() {
        Tournament t = open("League", PhaseType.ROUND_ROBIN, 4);
        t.start();
        playToCompletion(t);
        List<IParticipant> ranking = t.getFinalRanking();
        assertEquals(4, ranking.size());
        assertEquals("P1", ranking.getFirst().getName());
    }

    @Test
    void thirdPlaceMatchIsPlayedWhenEnabled() {
        Tournament t = open("Cup", PhaseType.SINGLE_ELIMINATION, 4);
        t.getPhases().getFirst().setThirdPlaceMatch(true);
        t.start();
        playToCompletion(t);
        List<IMatch> thirdPlace = t.getPhases().getFirst().getMatches(BracketSide.THIRD_PLACE);
        assertEquals(1, thirdPlace.size());
        assertTrue(thirdPlace.getFirst().getState().isDecided());
        assertNotNull(t.getThirdPlaceID());
    }
    @Test
    void thirdPlaceMatchIsSkippedWhenDisabled() {
        Tournament t = open("Cup", PhaseType.SINGLE_ELIMINATION, 4);
        t.getPhases().getFirst().setThirdPlaceMatch(false);
        t.start();
        playToCompletion(t);
        assertTrue(t.getPhases().getFirst().getMatches(BracketSide.THIRD_PLACE).isEmpty());
    }

    @Test
    void twoPhaseFlowDistributesQualifiersIntoNextPhaseMatches() {
        Tournament t = Tournament.create("Cup", PhaseType.GROUP, PhaseType.SINGLE_ELIMINATION);
        IPhase group = t.getPhases().get(0);
        group.setGroupCount(1);
        group.setAdvancePerGroup(2);
        t.openRegistration();
        for (int i = 1; i <= 6; i++) t.register("P" + i);
        t.closeRegistration();
        t.start();

        int guard = 0;
        while (group.getStatus() != PhaseStatus.COMPLETE && guard++ < 100) {
            for (IMatch m : t.getPlayableMatches().stream().filter(m -> m.getPhaseID().equals(group.getID())).toList()) {
                IParticipant p1 = m.getParticipant1().orElseThrow(), p2 = m.getParticipant2().orElseThrow();
                boolean p1Wins = p1.getSeed() < p2.getSeed();
                m.reportResult(p1Wins ? 1 : 0, p1Wins ? 0 : 1);
            }
        }
        assertEquals(PhaseStatus.COMPLETE, group.getStatus());

        IPhase bracket = t.getPhases().get(1);
        assertEquals(PhaseStatus.RUNNING, bracket.getStatus());
        assertEquals(2, bracket.getParticipants().size());
        List<Long> qualified = group.recomputeStandings().stream().filter(IStanding::isQualified).map(IStanding::getParticipantID).toList();
        assertEquals(new HashSet<>(qualified), bracket.getParticipants().stream().map(IParticipant::getID).collect(java.util.stream.Collectors.toSet()));

        playToCompletion(t);
        assertEquals(TournamentStatus.COMPLETE, t.getStatus());
        assertFalse(group.recomputeStandings().isEmpty()); // earlier phase's table is still queryable after the tournament moved on
    }

    @Test
    void reportGameAccumulatesBestOfSeriesAndClinchesEarly() {
        Tournament t = Tournament.create("Duel", PhaseType.SINGLE_ELIMINATION);
        t.getPhases().getFirst().setBestOf(3);
        t.openRegistration();
        t.register("A");
        t.register("B");
        t.closeRegistration();
        t.start();
        IMatch m = t.getPlayableMatches().getFirst();
        m.reportGame(11, 5);
        assertFalse(m.getState().isDecided());
        m.reportGame(11, 7);
        assertTrue(m.getState().isDecided());
        assertEquals(2, m.getGames().size()); // clinched 2-0, third game never needed
        assertEquals(TournamentStatus.COMPLETE, t.getStatus());
    }

    @Test
    void aTeamCanRegisterShortAndCompleteItsRosterBeforeTheStart() {
        Tournament t = Tournament.create("2v2", PhaseType.SINGLE_ELIMINATION);
        t.setTeamSize(2);
        t.openRegistration();
        IParticipant red = t.registerTeam("Red", List.of("Alice"));   // one player short
        IParticipant blue = t.register("Blue");                        // no roster at all
        t.closeRegistration();

        assertEquals(1, red.getRosterSize());
        assertEquals(1, red.getMissingMemberCount());
        assertEquals(2, blue.getMissingMemberCount());
        assertEquals(List.of(red, blue), t.getIncompleteTeams());
        assertFalse(t.hasCompleteRosters());
        TournamentException e = assertThrows(TournamentException.class, t::start);
        assertTrue(e.getMessage().contains("Red"), e.getMessage());

        red.addMember("Bob");
        blue.addMember("Cara");
        blue.addMember("Dan");

        assertTrue(t.hasCompleteRosters());
        t.start();
        assertEquals(TournamentStatus.RUNNING, t.getStatus());
    }

    @Test
    void shortTeamsMayPlayWhenCompleteRostersAreNotRequired() {
        Tournament t = Tournament.create("2v2", PhaseType.SINGLE_ELIMINATION);
        t.setTeamSize(2);
        t.setRequireCompleteRosters(false);
        t.openRegistration();
        t.registerTeam("Red", List.of("Alice"));
        t.registerTeam("Blue", List.of("Cara", "Dan"));
        t.closeRegistration();

        t.start();
        assertEquals(TournamentStatus.RUNNING, t.getStatus());
        assertEquals(1, t.getIncompleteTeams().size());
    }

    @Test
    void aTeamStillWaitingItsTurnDoesNotBlockTheStart() {
        Tournament t = Tournament.create("2v2", PhaseType.SINGLE_ELIMINATION);
        t.setTeamSize(2);
        t.setMaxParticipants(2);
        t.openRegistration();
        t.registerTeam("Red", List.of("Alice", "Bob"));
        t.registerTeam("Blue", List.of("Cara", "Dan"));
        t.registerTeam("Green", List.of("Eve"));  // waitlisted and short - not taking the floor
        t.closeRegistration();

        assertTrue(t.getIncompleteTeams().isEmpty());
        t.start();
        assertEquals(TournamentStatus.RUNNING, t.getStatus());
    }

    @Test
    void registeringIntoAFullFieldQueuesOnTheWaitingList() {
        Tournament t = Tournament.create("Capped", PhaseType.SINGLE_ELIMINATION);
        t.setMaxParticipants(2);
        t.openRegistration();
        t.register("A");
        t.register("B");
        IParticipant c = t.register("C");
        IParticipant d = t.register("D");

        assertTrue(t.isFull());
        assertEquals(2, t.getEntrants().size());
        assertEquals(List.of(c, d), t.getWaitlist());
        assertEquals(ParticipantStatus.WAITLISTED, c.getStatus());
        assertEquals(1, t.getWaitlistPosition(c));
        assertEquals(2, t.getWaitlistPosition(d));
        assertEquals(0, t.getWaitlistPosition(t.getParticipantByName("A").orElseThrow()));
        assertEquals(0, t.getFreeSlots());
    }

    @Test
    void unregisteringPullsTheFirstWaitingEntrantIntoTheFreedSlot() {
        Tournament t = Tournament.create("Capped", PhaseType.SINGLE_ELIMINATION);
        t.setMaxParticipants(2);
        t.openRegistration();
        IParticipant a = t.register("A");
        t.register("B");
        IParticipant c = t.register("C");
        IParticipant d = t.register("D");

        t.unregister(a);

        assertEquals(ParticipantStatus.REGISTERED, c.getStatus());
        assertEquals(List.of(d), t.getWaitlist());          // only one slot freed, so only one promotion
        assertEquals(2, t.getEntrants().size());
        assertTrue(t.isFull());

        t.closeRegistration();
        t.start();
        assertEquals(2, t.getPhases().getFirst().getParticipantCount()); // the waiting entrant is not seeded into the bracket
        assertFalse(t.getActiveParticipants().contains(d));
    }

    @Test
    void raisingTheCapPromotesEveryWaitingEntrantThatNowFits() {
        Tournament t = Tournament.create("Capped", PhaseType.SINGLE_ELIMINATION);
        t.setMaxParticipants(1);
        t.openRegistration();
        t.register("A");
        t.register("B");
        t.register("C");
        assertEquals(2, t.getWaitlist().size());

        t.setMaxParticipants(3);
        assertEquals(2, t.promoteFromWaitlist().size());
        assertTrue(t.getWaitlist().isEmpty());
        assertEquals(3, t.getEntrants().size());
    }

    @Test
    void aFullFieldStillRejectsEntrantsWhenTheWaitingListIsOff() {
        Tournament t = Tournament.create("Capped", PhaseType.SINGLE_ELIMINATION);
        t.setMaxParticipants(1);
        t.setWaitlistEnabled(false);
        t.openRegistration();
        t.register("A");
        assertThrows(TournamentException.class, () -> t.register("B"));
        assertTrue(t.getWaitlist().isEmpty());
    }

    @Test
    void nobodyIsPromotedIntoARunningTournamentUnlessLateRegistrationIsAllowed() {
        Tournament t = Tournament.create("Capped", PhaseType.SINGLE_ELIMINATION);
        t.setMaxParticipants(2);
        t.openRegistration();
        IParticipant a = t.register("A");
        t.register("B");
        IParticipant c = t.register("C");
        t.closeRegistration();
        t.start();

        t.unregister(a); // live, so this is a withdrawal - the bracket already exists
        assertEquals(ParticipantStatus.WAITLISTED, c.getStatus());
        assertTrue(t.promoteFromWaitlist().isEmpty());
    }

    @Test
    void resetMatchRevertsADecidedResultBackToPlayable() {
        Tournament t = Tournament.create("Duel", PhaseType.SINGLE_ELIMINATION);
        t.openRegistration();
        t.register("A");
        IParticipant b = t.register("B");
        t.closeRegistration();
        t.start();
        IMatch m = t.getPlayableMatches().getFirst();

        m.reportResult(2, 1);
        assertEquals(TournamentStatus.COMPLETE, t.getStatus());

        m.reset();
        assertEquals(MatchState.READY, m.getState());
        assertEquals(0, m.getScore1());
        assertEquals(0, m.getScore2());
        assertNull(m.getWinnerID());
        assertEquals(TournamentStatus.RUNNING, t.getStatus());

        m.reportResult(0, 3);
        assertEquals(TournamentStatus.COMPLETE, t.getStatus());
        assertEquals(b.getID(), t.getWinnerID());
    }

    @Test
    void totalPointsModeDecidesByAggregateScoreNotGamesWon() {
        Tournament t = Tournament.create("Duel", PhaseType.SINGLE_ELIMINATION);
        IPhase phase = t.getPhases().getFirst();
        phase.setBestOf(3);
        phase.setMatchDecisionMode(MatchDecisionMode.TOTAL_POINTS);
        t.openRegistration();
        t.register("A");
        t.register("B");
        t.closeRegistration();
        t.start();
        IMatch m = t.getPlayableMatches().getFirst();
        Long p1 = m.getParticipantID1(), p2 = m.getParticipantID2();

        m.reportGame(10, 5); // p1 leads 1-0 on games
        m.reportGame(10, 5); // p1 would have clinched 2-0 under GAMES_WON, but must not here
        assertFalse(m.getState().isDecided(), "TOTAL_POINTS must not stop early on a games-won clinch");
        m.reportGame(0, 30); // p2 catches up on aggregate: totals become p1=20, p2=40

        assertTrue(m.getState().isDecided());
        assertEquals(3, m.getGames().size());
        assertEquals(p2, m.getWinnerID(), "p2 scored more total points despite losing the first two games by count");
    }

    @Test
    void teamGameScoreIsTheSumOfItsMembersReportedPoints() {
        Tournament t = Tournament.create("2v2 Cup", PhaseType.SINGLE_ELIMINATION);
        t.setTeamSize(2);
        t.openRegistration();
        t.registerTeam("Red", List.of("Alice", "Bob"));
        t.registerTeam("Blue", List.of("Cara", "Dan"));
        t.closeRegistration();
        t.start();
        IMatch m = t.getPlayableMatches().getFirst();
        IParticipant side1 = m.getParticipant1().orElseThrow();
        IParticipant side2 = m.getParticipant2().orElseThrow();

        IMatchGame g = m.reportGame(Map.of(
                side1.getMembers().get(0), 5, side1.getMembers().get(1), 4,
                side2.getMembers().get(0), 2, side2.getMembers().get(1), 1));

        assertEquals(9, g.getScore1()); // side1's game score is the sum of its own members' points
        assertEquals(3, g.getScore2());
        assertEquals(side1.getID(), g.getWinnerID());
        assertEquals(TournamentStatus.COMPLETE, t.getStatus()); // bestOf 1 by default
    }

    @Test
    void participantRecordIsDerivedLiveFromDecidedMatches() {
        Tournament t = open("Cup", PhaseType.SINGLE_ELIMINATION, 4);
        IParticipant p1 = t.getParticipants().stream().filter(p -> p.getName().equals("P1")).findFirst().orElseThrow();
        assertEquals(0, p1.getMatchesWon());

        t.start();
        playToCompletion(t);

        assertEquals(TournamentStatus.COMPLETE, t.getStatus());
        assertEquals(2, p1.getMatchesWon()); // top seed wins the semifinal and the final
        assertEquals(0, p1.getMatchesLost());
        assertTrue(p1.getGamesWon() > 0);
    }
}
