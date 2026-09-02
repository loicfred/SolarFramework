package org.solarframework.tournament.impl;

import org.solarframework.tournament.api.*;
import org.solarframework.tournament.api.dto.BracketTheme;
import org.solarframework.tournament.obj.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;
import java.util.List;

import static org.solarframework.tournament.api.TournamentRegistry.SolarBrackets;

/**
 * The same demo as {@link Tournament_PlainMain} on the Spring path: nothing is built by hand, because scanning
 * {@code org.solarframework.tournament.spring} lets {@code TournamentConfig} build the renderer and fill
 * {@link TournamentRegistry}. Run it and compare the two files it writes with the plain run's.
 */
@SpringBootApplication
@ComponentScan("org.solarframework.tournament")
public class Tournament_Main {

    static void main(String[] args) throws Exception {
        SpringApplication.run(Tournament_Main.class, args);

        Tournament t = Tournament.create("Weekend 2v2 Cup", PhaseType.DOUBLE_ELIMINATION);
        t.setTeamSize(2);

        t.openRegistration();
        t.registerTeam("Red Dragons", List.of("Alice", "Bob"));
        t.registerTeam("Blue Sharks", List.of("Cara", "Dan"));
        t.registerTeam("Green Wolves", List.of("Eve", "Finn"));
        t.registerTeam("Yellow Hawks", List.of("Gus", "Hana"));
        t.closeRegistration();

        Phase bracket = t.getPhases().getFirst();
        t.start();

        while (t.getStatus() != TournamentStatus.COMPLETE) {
            List<Match> playable = t.getPlayableMatches();
            if (playable.isEmpty()) break;
            for (Match m : playable) m.reportResult(2, 1); // team 1 always wins in this demo
        }

        System.out.println("Champion: " + t.getWinner().map(Participant::getDisplayName).orElse("none"));
        File png = SolarBrackets.writePng(bracket, BracketTheme.dark(), new File("bracket.png"));
        File html = SolarBrackets.writeHtml(bracket, BracketTheme.dark(), new File("bracket.html"));
        System.out.println("Bracket written to " + png.getAbsolutePath());
        System.out.println("Interactive bracket written to " + html.getAbsolutePath());
    }
}
