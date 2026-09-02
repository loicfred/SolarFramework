package org.solarframework.tournament.impl.seed;

import org.solarframework.tournament.util.Seeder;
import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.SeedingMethod;
import org.solarframework.tournament.obj.Participant;
import org.solarframework.tournament.obj.Tournament;
import org.solarframework.tournament.obj.Participant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SeederTest {
    private List<Participant> field(int n) {
        Tournament t = new Tournament("Cup");
        List<Participant> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new Participant(t, "P" + i, i));
        return out;
    }

    @Test
    void orderByManualSortsBySeedNumber() {
        List<Participant> shuffled = new ArrayList<>(field(4));
        Collections.shuffle(shuffled, new Random(1));
        List<Participant> ordered = Seeder.order(shuffled, SeedingMethod.MANUAL, 0, null);
        assertEquals(List.of("P1", "P2", "P3", "P4"), ordered.stream().map(Participant::getName).toList());
    }
    @Test
    void orderByRandomIsReproducibleForTheSameSeed() {
        List<Participant> field = field(6);
        List<Long> a = Seeder.order(field, SeedingMethod.RANDOM, 42, null).stream().map(Participant::getID).toList();
        List<Long> b = Seeder.order(field, SeedingMethod.RANDOM, 42, null).stream().map(Participant::getID).toList();
        assertEquals(a, b);
    }
    @Test
    void applySeedNumbersWritesOneBasedSeeds() {
        List<Participant> ordered = field(3);
        Seeder.applySeedNumbers(ordered);
        assertEquals(List.of(1, 2, 3), ordered.stream().map(Participant::getSeed).toList());
    }
    @Test
    void intoGroupsSnakesSeedsAcrossGroupsEvenly() {
        List<List<Participant>> groups = Seeder.intoGroups(field(8), 2, true);
        assertEquals(List.of(1, 4, 5, 8), groups.getFirst().stream().map(Participant::getSeed).toList());
        assertEquals(List.of(2, 3, 6, 7), groups.get(1).stream().map(Participant::getSeed).toList());
    }
    @Test
    void intoBracketSlotsLeavesEmptySlotsForSeedsBeyondTheField() {
        List<Optional<Participant>> slots = Seeder.intoBracketSlots(field(5), 8);
        assertEquals(8, slots.size());
        assertEquals(1, slots.getFirst().orElseThrow().getSeed());
        assertEquals(3, slots.stream().filter(Optional::isEmpty).count());
    }
}
