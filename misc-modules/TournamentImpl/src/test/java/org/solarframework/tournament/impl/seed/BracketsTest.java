package org.solarframework.tournament.impl.seed;

import org.solarframework.tournament.util.Brackets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BracketsTest {

    @Test
    void nextPowerOfTwoRoundsUpToNearestPowerOfTwo() {
        assertEquals(2, Brackets.nextPowerOfTwo(1));
        assertEquals(2, Brackets.nextPowerOfTwo(2));
        assertEquals(4, Brackets.nextPowerOfTwo(3));
        assertEquals(8, Brackets.nextPowerOfTwo(5));
        assertEquals(16, Brackets.nextPowerOfTwo(16));
    }
    @Test
    void log2ReturnsExponentOfAPowerOfTwo() {
        assertEquals(1, Brackets.log2(2));
        assertEquals(3, Brackets.log2(8));
        assertEquals(4, Brackets.log2(16));
    }
    @Test
    void seedOrderKeepsTopTwoSeedsApartUntilTheFinal() {
        assertArrayEquals(new int[]{1, 2}, Brackets.seedOrder(2));
        assertArrayEquals(new int[]{1, 4, 2, 3}, Brackets.seedOrder(4));
        assertArrayEquals(new int[]{1, 8, 4, 5, 2, 7, 3, 6}, Brackets.seedOrder(8));
    }
    @Test
    void roundNameLabelsLastRoundsByStage() {
        assertEquals("Final", Brackets.roundName(3, 3));
        assertEquals("Semifinals", Brackets.roundName(2, 3));
        assertEquals("Quarterfinals", Brackets.roundName(1, 3));
        assertEquals("Round of 16", Brackets.roundName(1, 4));
    }
}
