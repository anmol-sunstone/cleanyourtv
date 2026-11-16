package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class ChristmasTreeTest {

    @Test
    public void theTreeIsDressedALittleMoreAsChristmasComesNearer() {
        assertEquals(ChristmasTree.FEWEST, ChristmasTree.decorations(LocalDate.of(2026, 7, 1)));
        assertEquals(ChristmasTree.FEWEST, ChristmasTree.decorations(LocalDate.of(2026, 10, 26)));
        assertEquals(11, ChristmasTree.decorations(LocalDate.of(2026, 11, 16)));
        assertEquals(18, ChristmasTree.decorations(LocalDate.of(2026, 12, 1)));
        assertEquals(30, ChristmasTree.decorations(LocalDate.of(2026, 12, 24)));
        int before = 0;
        for (LocalDate day = LocalDate.of(2026, 10, 1); day.isBefore(LocalDate.of(2026, 12, 26)); day = day.plusDays(1)) {
            int now = ChristmasTree.decorations(day);
            assertTrue(day + ": never fewer than the day before", now >= before);
            assertTrue(day + ": at most one more a day", now - before <= 1 || before == 0);
            before = now;
        }
    }

    @Test
    public void theStarIsLitAndTheTreeFullFromChristmasDayThroughJanuary() {
        assertFalse(ChristmasTree.starLit(LocalDate.of(2026, 12, 24)));
        assertTrue(ChristmasTree.starLit(LocalDate.of(2026, 12, 25)));
        assertTrue(ChristmasTree.starLit(LocalDate.of(2026, 12, 31)));
        assertTrue(ChristmasTree.starLit(LocalDate.of(2027, 1, 6)));
        assertFalse(ChristmasTree.starLit(LocalDate.of(2027, 2, 1)));
        assertEquals(ChristmasTree.DECORATIONS, ChristmasTree.decorations(LocalDate.of(2026, 12, 25)));
        assertEquals(ChristmasTree.DECORATIONS, ChristmasTree.decorations(LocalDate.of(2027, 1, 6)));
    }
}
