package org.solarframework.discord.utils;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PagerTest {

    private ActionRow row(int page, boolean hasNext) {
        return Pager.row(page, "Page " + page, hasNext, p -> Button.primary("pager/" + p, "x"));
    }

    @Test
    void lastPageRoundsUpTheRemainder() {
        assertEquals(3, Pager.lastPage(10, 30));
        assertEquals(4, Pager.lastPage(10, 31));
        assertEquals(1, Pager.lastPage(10, 3));
    }
    @Test
    void lastPageIsOneWhenThereIsNothingToShow() {
        assertEquals(1, Pager.lastPage(10, 0));
    }
    @Test
    void lastPageKeepsUnknownTotalUnknown() {
        assertEquals(Pager.UNKNOWN_TOTAL, Pager.lastPage(10, Pager.UNKNOWN_TOTAL));
    }

    @Test
    void withPageAppendsThePageLast() {
        assertArrayEquals(new Object[]{"a", "b", 4}, Pager.withPage(new Object[]{"a", "b"}, 4));
        assertArrayEquals(new Object[]{2}, Pager.withPage(new Object[0], 2));
    }
    @Test
    void withPageLeavesTheCallerMetadataAlone() {
        Object[] M = {"a"};
        Pager.withPage(M, 7);
        assertArrayEquals(new Object[]{"a"}, M);
    }
    @Test
    void pageOfReadsTheLastEntry() {
        assertEquals(7, Pager.pageOf(new String[]{"a", "b", "7"}));
        assertEquals(1, Pager.pageOf(new String[]{"1"}));
    }

    @Test
    void rowCarriesTheNeighbouringPages() {
        List<Button> B = row(3, true).getButtons();
        assertEquals("pager/2", B.getFirst().getCustomId());
        assertEquals("pager/3", B.get(1).getCustomId());
        assertEquals("pager/4", B.get(2).getCustomId());
        assertEquals(Pager.PREV, B.getFirst().getLabel());
        assertEquals("Page 3", B.get(1).getLabel());
        assertEquals(Pager.NEXT, B.get(2).getLabel());
    }
    @Test
    void rowDisablesTheArrowsAtTheBounds() {
        assertTrue(row(1, true).getButtons().getFirst().isDisabled());
        assertFalse(row(2, true).getButtons().getFirst().isDisabled());
        assertTrue(row(2, false).getButtons().get(2).isDisabled());
        assertFalse(row(2, true).getButtons().get(2).isDisabled());
    }
    @Test
    void rowAlwaysDisablesTheCurrentPage() {
        assertTrue(row(2, true).getButtons().get(1).isDisabled());
    }
    @Test
    void pageOfReadsBackWhatRowEncoded() {
        Button next = Pager.row(3, "Page 3", true, p -> Button.primary("pager/meta/" + p, "x")).getButtons().get(2);
        assertEquals(4, Pager.pageOf(next.getCustomId().split("/")));
    }
}
