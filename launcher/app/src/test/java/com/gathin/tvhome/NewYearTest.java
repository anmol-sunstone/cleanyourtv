package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class NewYearTest {

    private static LocalDateTime at(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    @Test
    public void christmasStaysWholeUntilThe29th() {
        assertEquals(NewYear.CHRISTMAS, NewYear.phase(LocalDate.of(2026, 11, 16)));
        assertEquals(NewYear.CHRISTMAS, NewYear.phase(LocalDate.of(2026, 12, 25)));
        assertEquals(NewYear.CHRISTMAS, NewYear.phase(LocalDate.of(2026, 12, 29)));
        assertEquals(NewYear.EVE, NewYear.phase(LocalDate.of(2026, 12, 30)));
        assertEquals(NewYear.EVE, NewYear.phase(LocalDate.of(2026, 12, 31)));
        assertEquals(NewYear.NEW_YEAR, NewYear.phase(LocalDate.of(2027, 1, 1)));
        assertEquals(NewYear.NEW_YEAR, NewYear.phase(LocalDate.of(2027, 1, 6)));
        assertEquals(0f, NewYear.gap(at(2026, 12, 29, 23, 59, 59)), 0f);
        assertEquals(0, NewYear.daysToNewYear(LocalDate.of(2026, 12, 29)));
    }

    @Test
    public void theGreetingCountsTwoDaysThenTheEve() {
        assertEquals(2, NewYear.daysToNewYear(LocalDate.of(2026, 12, 30)));
        assertEquals(1, NewYear.daysToNewYear(LocalDate.of(2026, 12, 31)));
        assertEquals(0, NewYear.daysToNewYear(LocalDate.of(2027, 1, 1)));
        assertEquals(2027, NewYear.year(LocalDate.of(2026, 12, 30)));
        assertEquals(2027, NewYear.year(LocalDate.of(2027, 1, 1)));
    }

    @Test
    public void theLastTenSecondsAreCountedDown() {
        assertEquals(0, NewYear.countdown(at(2026, 12, 31, 23, 59, 49)));
        assertEquals(10, NewYear.countdown(at(2026, 12, 31, 23, 59, 50)));
        assertEquals(1, NewYear.countdown(at(2026, 12, 31, 23, 59, 59)));
        assertEquals(0, NewYear.countdown(at(2027, 1, 1, 0, 0, 0)));
        assertEquals(0, NewYear.countdown(at(2026, 12, 30, 23, 59, 55)));
    }

    @Test
    public void theFinaleIsTheFirstMinuteOfTheYear() {
        assertEquals(-1f, NewYear.finale(at(2026, 12, 31, 23, 59, 59)), 0f);
        assertEquals(0f, NewYear.finale(at(2027, 1, 1, 0, 0, 0)), 0.001f);
        assertEquals(59f, NewYear.finale(at(2027, 1, 1, 0, 0, 59)), 0.001f);
        assertEquals(-1f, NewYear.finale(at(2027, 1, 1, 0, 1, 0)), 0f);
        assertEquals(-1f, NewYear.finale(at(2027, 1, 2, 0, 0, 30)), 0f);
        assertTrue(NewYear.gap(at(2027, 1, 1, 0, 0, 30)) < 1f);
        assertEquals(1, NewYear.volley(at(2027, 1, 1, 0, 0, 30)));
    }

    @Test
    public void fireworksComeMoreOftenAsTheEveningOfThe31stGoesOn() {
        assertEquals(420f, NewYear.gap(at(2026, 12, 30, 20, 0, 0)), 0f);
        assertEquals(300f, NewYear.gap(at(2026, 12, 31, 9, 0, 0)), 0f);
        float before = NewYear.gap(at(2026, 12, 31, 18, 0, 0));
        for (int minute = 1; minute < 360; minute++) {
            float now = NewYear.gap(at(2026, 12, 31, 18 + minute / 60, minute % 60, 0));
            assertTrue("minute " + minute, now <= before);
            before = now;
        }
        assertTrue(NewYear.gap(at(2026, 12, 31, 23, 59, 0)) <= 25f);
        assertEquals(240f, NewYear.gap(at(2027, 1, 3, 20, 0, 0)), 0f);
        assertEquals(3, NewYear.volley(at(2027, 1, 3, 20, 0, 0)));
        assertEquals(1, NewYear.volley(at(2026, 12, 31, 20, 0, 0)));
    }

    @Test
    public void presentsLieUnderTheTreeFromChristmasDayToNewYearsEve() {
        assertFalse(NewYear.presents(LocalDate.of(2026, 12, 24)));
        assertTrue(NewYear.presents(LocalDate.of(2026, 12, 25)));
        assertTrue(NewYear.presents(LocalDate.of(2026, 12, 31)));
        assertFalse(NewYear.presents(LocalDate.of(2027, 1, 1)));
    }

    @Test
    public void newYearPicturesShowOnlyInJanuaryWhileTheSkinIsOn() {
        File sleigh = new File("01-sleigh-night.jpg"), fire = new File("02-fireplace.jpg");
        File rockets = new File("ny-01-fireworks.jpg"), street = new File("NY-02-street.JPG");
        List<File> all = Arrays.asList(sleigh, fire, rockets, street);
        assertEquals(Arrays.asList(sleigh, fire), NewYear.pictures(all, true, LocalDate.of(2026, 12, 31)));
        assertEquals(Arrays.asList(rockets, street), NewYear.pictures(all, true, LocalDate.of(2027, 1, 1)));
        assertEquals(Arrays.asList(sleigh, fire), NewYear.pictures(all, false, LocalDate.of(2027, 1, 7)));
        assertEquals(Arrays.asList(sleigh, fire), NewYear.pictures(all, false, LocalDate.of(2027, 7, 1)));
    }

    @Test
    public void aThemeWithoutNewYearPicturesKeepsItsOwn() {
        List<File> plain = Arrays.asList(new File("01-sleigh-night.jpg"), new File("02-fireplace.jpg"));
        assertSame(plain, NewYear.pictures(plain, true, LocalDate.of(2027, 1, 1)));
        List<File> only = Collections.singletonList(new File("ny-01-fireworks.jpg"));
        assertSame(only, NewYear.pictures(only, true, LocalDate.of(2026, 12, 25)));
        assertTrue(NewYear.pictures(Collections.<File>emptyList(), true, LocalDate.of(2027, 1, 1)).isEmpty());
    }

    @Test
    public void aRealBuildGoesByTheTvsOwnClock() {
        assertEquals(0L, NewYear.SHIFT_SECONDS);
    }
}
