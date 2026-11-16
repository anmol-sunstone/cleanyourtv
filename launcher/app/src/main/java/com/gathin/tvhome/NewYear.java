package com.gathin.tvhome;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How the Christmas skin turns into the new year, by the TV's own date. Christmas stays whole until 29 December.
 * On the 30th and 31st it is still Christmas with two additions: the greeting counts down to the new year and a
 * firework goes up now and then, more often as the evening of the 31st goes on. The last ten seconds of the year
 * are counted down on screen, the first minute of the new one is a finale of fireworks, and from then until the
 * skin ends the pictures are the theme's New Year ones ({@link #pictures}), the candle is a sparkler and the
 * presents are gone from under the tree. The clock screensaver's snow scene goes by the same dates.
 */
final class NewYear {

    /**
     * Seconds added to the TV's clock for everything the skin does by date. 0 in a real build; a test build sets
     * it to look at New Year's Eve on a TV in November.
     */
    static final long SHIFT_SECONDS = 0L;

    static final int CHRISTMAS = 0, EVE = 1, NEW_YEAR = 2;

    /** How long the finale lasts after midnight, seconds. */
    static final int FINALE_SECONDS = 60;
    /** A theme's New Year pictures are the files in wallpapers/ whose names start with this. */
    static final String PICTURE_PREFIX = "ny-";

    private NewYear() {
    }

    /** The date and time the skin goes by. */
    static LocalDateTime now() {
        return LocalDateTime.now().plusSeconds(SHIFT_SECONDS);
    }

    static LocalDate today() {
        return now().toLocalDate();
    }

    /** {@link #EVE} on 30 and 31 December, {@link #NEW_YEAR} in January, {@link #CHRISTMAS} otherwise. */
    static int phase(LocalDate day) {
        if (day.getMonthValue() == 1) return NEW_YEAR;
        return day.getMonthValue() == 12 && day.getDayOfMonth() >= 30 ? EVE : CHRISTMAS;
    }

    /** Days until 1 January on 30 December (2) and on New Year's Eve (1); 0 on any other day. */
    static int daysToNewYear(LocalDate day) {
        return phase(day) == EVE ? 32 - day.getDayOfMonth() : 0;
    }

    /** The year being counted down to or just begun. */
    static int year(LocalDate day) {
        return day.getMonthValue() == 12 ? day.getYear() + 1 : day.getYear();
    }

    /** The number to show in the last ten seconds of the year, 10 down to 1; 0 at any other time. */
    static int countdown(LocalDateTime t) {
        if (t.getMonthValue() != 12 || t.getDayOfMonth() != 31 || t.getHour() != 23 || t.getMinute() != 59) return 0;
        return t.getSecond() >= 50 ? 60 - t.getSecond() : 0;
    }

    /** Seconds into the finale during the first minute of the year; -1 at any other time. */
    static float finale(LocalDateTime t) {
        if (t.getMonthValue() != 1 || t.getDayOfMonth() != 1 || t.getHour() != 0 || t.getMinute() != 0) return -1f;
        return t.getSecond() + t.getNano() / 1e9f;
    }

    /**
     * The usual wait between fireworks, seconds; 0 while there are none. On 30 December one every seven minutes;
     * on the 31st one every five minutes until six in the evening, then steadily more often, down to one every
     * twenty seconds in the last minutes of the year; in the finale about three a second; afterwards a small
     * volley every four minutes.
     */
    static float gap(LocalDateTime t) {
        int phase = phase(t.toLocalDate());
        if (phase == CHRISTMAS) return 0f;
        if (phase == NEW_YEAR) return finale(t) >= 0f ? 0.35f : 240f;
        if (t.getDayOfMonth() == 30) return 420f;
        float evening = t.getHour() + t.getMinute() / 60f - 18f;
        return evening <= 0f ? 300f : Math.max(20f, 300f - evening * 47f);
    }

    /** How many rockets go up together: one on the 30th and 31st and in the finale, up to three afterwards. */
    static int volley(LocalDateTime t) {
        return phase(t.toLocalDate()) == NEW_YEAR && finale(t) < 0f ? 3 : 1;
    }

    /** Presents lie under the tree from Christmas Day to New Year's Eve. */
    static boolean presents(LocalDate day) {
        return day.getMonthValue() == 12 && day.getDayOfMonth() >= 25;
    }

    /**
     * The pictures to rotate on this day. A theme's New Year pictures ({@link #PICTURE_PREFIX}) are shown, alone,
     * only while the Christmas skin is on in January; on every other day they are left out. A collection that
     * would end up empty is returned whole, so a theme with no New Year pictures keeps its own all January.
     */
    static List<File> pictures(List<File> all, boolean christmasSkinOn, LocalDate day) {
        boolean newYear = christmasSkinOn && phase(day) == NEW_YEAR;
        List<File> kept = new ArrayList<>();
        for (File f : all) {
            if (f.getName().toLowerCase(Locale.ROOT).startsWith(PICTURE_PREFIX) == newYear) kept.add(f);
        }
        return kept.isEmpty() ? all : kept;
    }
}
