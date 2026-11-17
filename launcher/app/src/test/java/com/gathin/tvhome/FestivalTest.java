package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class FestivalTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Test
    public void activeThroughTheLastDayInclusive() {
        Festival f = Festival.parse("Dussehra", "2026-10-21", "", "");
        assertTrue(f.active(DAY));
        assertTrue(f.active(DAY.plusDays(1)));
        assertFalse(f.active(DAY.plusDays(2)));
    }

    @Test
    public void noEndDateStaysOn() {
        assertTrue(Festival.parse("diwali", "", "", "").active(DAY.plusYears(1)));
    }

    @Test
    public void malformedValuesTurnTheSkinOff() {
        assertSame(Festival.NONE, Festival.parse("", "2026-10-21", "", ""));
        assertSame(Festival.NONE, Festival.parse("dussehra", "21/10/2026", "", ""));
        assertFalse(Festival.NONE.active(DAY));
    }

    @Test
    public void customGreetingIsSanitizedAndOverridesTheFestivalDefault() {
        assertEquals("Shubho Bijoya",
                Festival.parse("dussehra", "", "  Shubho\u202E Bijoya ", "").greetingText(null));
    }

    @Test
    public void unknownFestivalKeepsTheTimeOfDayGreeting() {
        assertEquals("", Festival.parse("onam", "", "", "").greetingText(null));
    }

    @Test
    public void accentFallsBackToDiyaGold() {
        assertEquals(Festival.DEFAULT_ACCENT, Festival.parse("dussehra", "", "", "").accentColor());
        assertEquals(0xFF102030, Festival.parse("dussehra", "", "", "#102030").accentColor());
        assertEquals(Festival.DEFAULT_ACCENT, Festival.parse("dussehra", "", "", "gold").accentColor());
    }

    @Test
    public void halloweenHasItsOwnSkinAndPumpkinAccent() {
        Festival f = Festival.parse("Halloween", "2026-11-01", "", "");
        assertTrue(f.isHalloween());
        assertTrue(f.active(LocalDate.of(2026, 10, 31)));
        assertTrue(f.active(LocalDate.of(2026, 11, 1)));
        assertFalse(f.active(LocalDate.of(2026, 11, 2)));
        assertEquals(Festival.PUMPKIN, f.accentColor());
        assertEquals(0xFFAA5500, Festival.parse("halloween", "", "", "#AA5500").accentColor());
    }

    @Test
    public void otherFestivalsKeepTheDiyaSkin() {
        assertFalse(Festival.parse("dussehra", "", "", "").isHalloween());
        assertFalse(Festival.parse("onam", "", "", "").isHalloween());
        assertFalse(Festival.NONE.isHalloween());
    }

    @Test
    public void christmasCountsTheSleepsThenGreetsTheNewYear() {
        assertEquals(39, Festival.sleepsToChristmas(LocalDate.of(2026, 11, 16)));
        assertEquals(1, Festival.sleepsToChristmas(LocalDate.of(2026, 12, 24)));
        assertEquals(0, Festival.sleepsToChristmas(LocalDate.of(2026, 12, 25)));
        assertEquals(0, Festival.sleepsToChristmas(LocalDate.of(2026, 12, 31)));
        assertEquals(-1, Festival.sleepsToChristmas(LocalDate.of(2027, 1, 6)));
        assertEquals(0, Festival.sleepsToChristmas(LocalDate.of(2026, 7, 1)));
    }

    @Test
    public void theAdventCandleBurnsDownThroughDecember() {
        assertEquals(1f, Festival.candleLeft(LocalDate.of(2026, 11, 16)), 0f);
        assertEquals(1f, Festival.candleLeft(LocalDate.of(2026, 12, 1)), 0f);
        assertEquals(0.5f, Festival.candleLeft(LocalDate.of(2026, 12, 13)), 0f);
        assertEquals(0f, Festival.candleLeft(LocalDate.of(2026, 12, 25)), 0f);
        assertEquals(0f, Festival.candleLeft(LocalDate.of(2027, 1, 3)), 0f);
    }

    @Test
    public void christmasHasItsOwnSkinAndCandlelightAccent() {
        Festival f = Festival.parse("Christmas", "2027-01-06", "", "");
        assertTrue(f.isChristmas());
        assertFalse(f.isHalloween());
        assertEquals(Festival.CANDLELIGHT, f.accentColor());
    }

    @Test
    public void snowClearerNamesTheBroomOrSantaAndAnythingElseIsThePlough() {
        Festival f = Festival.parse("christmas", "", "", "");
        assertFalse(f.broom || f.santa);
        assertSame(f, f.clearedBy("plough"));
        assertSame(f, f.clearedBy(null));
        assertTrue(f.clearedBy(" Santa ").santa);
        assertFalse(f.clearedBy("santa").broom);
        assertTrue(f.clearedBy("broom").broom);
        assertFalse(f.clearedBy("broom").santa);
        assertFalse(f.clearedBy("santa").clearedBy("sleigh").santa);
        assertSame(Festival.NONE, Festival.NONE.clearedBy("santa"));
    }
}
