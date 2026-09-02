package org.solarframework.tournament.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.solarframework.tournament.api.SeedingMethod;
import org.solarframework.tournament.obj.Participant;
import org.solarframework.tournament.obj.Phase;
import org.solarframework.tournament.obj.Standing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Turns a set of entrants into an ordered field, and spreads that field across groups. */
public final class Seeder {
    private static final Logger log = LoggerFactory.getLogger(Seeder.class);
    private Seeder() {}

    /**
     * Orders entrants strongest first. RANDOM is driven by the tournament's stored seed so the
     * same draw can be reproduced; STANDINGS falls back to MANUAL when no previous phase exists.
     */
    public static List<Participant> order(List<Participant> entrants, SeedingMethod method, long randomSeed, Phase previousPhase) {
        List<Participant> out = new ArrayList<>(entrants);
        switch (method == null ? SeedingMethod.MANUAL : method) {
            case ORDER -> out.sort(Comparator.comparing(p -> p.getRegisteredAt() == null ? Instant.EPOCH : p.getRegisteredAt()));
            case RANDOM -> Collections.shuffle(out, new Random(randomSeed));
            case RATING -> out.sort(Comparator.comparingDouble((Participant p) -> p.getRating() == null ? Double.NEGATIVE_INFINITY : p.getRating()).reversed()
                    .thenComparingInt(Participant::getEffectiveSeed));
            case SNAKE, MANUAL -> sortBySeed(out);
            case STANDINGS -> {
                if (previousPhase == null) {
                    log.debug("STANDINGS seeding requested with no previous phase - falling back to seed order");
                    sortBySeed(out);
                } else {
                    List<Long> ranked = previousPhase.getQualified().stream().map(Standing::getParticipantID).toList();
                    out.sort(Comparator.comparingInt(p -> { int i = ranked.indexOf(p.getID()); return i < 0 ? Integer.MAX_VALUE : i; }));
                }
            }
        }
        return out;
    }

    private static void sortBySeed(List<Participant> out) { out.sort(Comparator.comparingInt(Participant::getEffectiveSeed).thenComparing(Participant::getName)); }

    /** Writes 1..n back onto the entrants so the drawn order is persisted. */
    public static List<Participant> applySeedNumbers(List<Participant> ordered) {
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setSeed(i + 1);
        return ordered;
    }

    /**
     * Snake distribution: seeds 1..n are dealt across groups left-to-right then right-to-left,
     * so no group collects all the top seeds. Returns one list per group.
     */
    public static List<List<Participant>> intoGroups(List<Participant> ordered, int groupCount, boolean snake) {
        int groups = Math.max(1, groupCount);
        List<List<Participant>> out = new ArrayList<>();
        for (int i = 0; i < groups; i++) out.add(new ArrayList<>());
        for (int i = 0; i < ordered.size(); i++) {
            int row = i / groups, col = i % groups;
            int g = snake && row % 2 == 1 ? groups - 1 - col : col;
            out.get(g).add(ordered.get(i));
        }
        return out;
    }

    /**
     * Places the field into a bracket of {@code size} slots following {@link Brackets#seedOrder}.
     * Slots beyond the field size come back empty - those become byes.
     */
    public static List<Optional<Participant>> intoBracketSlots(List<Participant> ordered, int size) {
        int[] order = Brackets.seedOrder(size);
        List<Optional<Participant>> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int seed = order[i];
            slots.add(seed <= ordered.size() ? Optional.of(ordered.get(seed - 1)) : Optional.empty());
        }
        return slots;
    }
}
