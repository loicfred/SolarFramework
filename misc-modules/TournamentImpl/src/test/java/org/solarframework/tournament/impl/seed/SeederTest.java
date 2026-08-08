package org.solarframework.tournament.impl.seed;

import org.junit.jupiter.api.Test;
import org.solarframework.tournament.api.SeedingMethod;
import org.solarframework.tournament.impl.obj.Participant;
import org.solarframework.tournament.impl.obj.Tournament;
import org.solarframework.tournament.obj.IParticipant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SeederTest {
    private List<IParticipant> field(int n) {
        Tournament t = new Tournament("Cup");
        List<IParticipant> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new Participant(t, "P" + i, i));
        return out;
    }

    @Test
    void orderByManualSortsBySeedNumber() {
        List<IParticipant> shuffled = new ArrayList<>(field(4));
        Collections.shuffle(shuffled, new Random(1));
        List<IParticipant> ordered = Seeder.order(shuffled, SeedingMethod.MANUAL, 0, null);
        assertEquals(List.of("P1", "P2", "P3", "P4"), ordered.stream().map(IParticipant::getName).toList());
    }
    @Test
    void orderByRandomIsReproducibleForTheSameSeed() {
        List<IParticipant> field = field(6);
        List<Long> a = Seeder.order(field, SeedingMethod.RANDOM, 42, null).stream().map(IParticipant::getID).toList();
        List<Long> b = Seeder.order(field, SeedingMethod.RANDOM, 42, null).stream().map(IParticipant::getID).toList();
        assertEquals(a, b);
    }
    @Test
    void applySeedNumbersWritesOneBasedSeeds() {
        List<IParticipant> ordered = field(3);
        Seeder.applySeedNumbers(ordered);
        assertEquals(List.of(1, 2, 3), ordered.stream().map(IParticipant::getSeed).toList());
    }
    @Test
    void intoGroupsSnakesSeedsAcrossGroupsEvenly() {
        List<List<IParticipant>> groups = Seeder.intoGroups(field(8), 2, true);
        assertEquals(List.of(1, 4, 5, 8), groups.get(0).stream().map(IParticipant::getSeed).toList());
        assertEquals(List.of(2, 3, 6, 7), groups.get(1).stream().map(IParticipant::getSeed).toList());
    }
    @Test
    void intoBracketSlotsLeavesEmptySlotsForSeedsBeyondTheField() {
        List<Optional<IParticipant>> slots = Seeder.intoBracketSlots(field(5), 8);
        assertEquals(8, slots.size());
        assertEquals(1, slots.get(0).orElseThrow().getSeed());
        assertEquals(3, slots.stream().filter(Optional::isEmpty).count());
    }
}
