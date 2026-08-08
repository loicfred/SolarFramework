package org.solarframework.tournament.impl;

import org.solarframework.tournament.api.*;
import org.solarframework.tournament.api.dto.BracketTheme;
import org.solarframework.tournament.impl.obj.Tournament;
import org.solarframework.tournament.impl.render.BracketRenderer;
import org.solarframework.tournament.obj.*;

import java.io.File;
import java.util.List;

/** Runnable demo: a 2v2 double elimination tournament from registration to champion. */
public class Main {

    static void main(String[] args) throws Exception {
        new BracketRenderer();     // self-registers into TournamentRegistry.SolarBrackets

        Tournament t = Tournament.create("Weekend 2v2 Cup", PhaseType.DOUBLE_ELIMINATION);
        t.setTeamSize(2);

        t.openRegistration();
        t.registerTeam("Red Dragons", List.of("Alice", "Bob"));
        t.registerTeam("Blue Sharks", List.of("Cara", "Dan"));
        t.registerTeam("Green Wolves", List.of("Eve", "Finn"));
        t.registerTeam("Yellow Hawks", List.of("Gus", "Hana"));
        t.registerTeam("Team 1", List.of("Alice", "Bob"));
        t.registerTeam("Team 2", List.of("Alice", "Bob"));
        t.closeRegistration();

        IPhase bracket = t.getPhases().getFirst();
        t.start();

        while (t.getStatus() != TournamentStatus.COMPLETE) {
            List<IMatch> playable = t.getPlayableMatches();
            if (playable.isEmpty()) break;
            for (IMatch m : playable) {
                IParticipant a = m.getParticipant1().orElseThrow();
                IParticipant b = m.getParticipant2().orElseThrow();
                System.out.println("M" + m.getMatchNumber() + " (" + m.getBracketSide() + " R" + m.getRound() + "): " + a.getName() + " vs " + b.getName());
                m.reportResult(2, 1); // team 1 always wins in this demo
            }
        }

        System.out.println();
        System.out.println("Champion: " + t.getWinner().map(IParticipant::getDisplayName).orElse("none"));
        t.getPodium().forEach(p -> System.out.println(p.getFinalRank() + ". " + p.getDisplayName()));

        IBracketRenderer renderer = TournamentRegistry.SolarBrackets;
        File png = renderer.writePng(bracket, BracketTheme.dark(), new File("bracket.png"));
        File html = renderer.writeHtml(bracket, BracketTheme.dark(), new File("bracket.html"));
        System.out.println("Bracket written to " + png.getAbsolutePath());
        System.out.println("Interactive bracket written to " + html.getAbsolutePath());
    }
}
