package com.gathin.tvhome;

import android.content.res.Resources;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * A festive skin set by a theme's config.json: {@code festival} (or {@code seasonal_skin}), {@code festival_until},
 * {@code greeting} and {@code accent_color}. While it is active the pills get festive icons (a diya for the
 * network, a bell for Inputs, a marigold for Settings; for {@code halloween} a jack-o'-lantern, a bat and a
 * ghost; for {@code christmas} an Advent candle, a jingle bell and a snowflake), the greeting is replaced and the focus glow takes the accent colour. It switches itself off after
 * {@code festival_until}, so nobody has to remember to undo it.
 */
final class Festival {

    static final Festival NONE = new Festival("", null, "", 0);

    /** Warm diya gold, the accent when a theme names a festival but no colour. */
    static final int DEFAULT_ACCENT = 0xFFFFB23C;

    /** The festival with its own skin (jack-o'-lantern, bat, ghost, bats on the first Home) instead of the diya's. */
    static final String HALLOWEEN = "halloween";

    /** Pumpkin orange, Halloween's accent when the theme names no colour. */
    static final int PUMPKIN = 0xFFFF7A1A;

    /** The festival with a candle, a jingle bell and a snowflake, snowfall on the first Home and a countdown. */
    static final String CHRISTMAS = "christmas";

    /** Candlelight, Christmas's accent when the theme names no colour. */
    static final int CANDLELIGHT = 0xFFFFC46B;

    /** Lower-case festival name, or "" for none. */
    final String name;
    /** Last day the skin shows, inclusive; null for no end date. */
    final LocalDate until;
    /** Text that replaces the time-of-day greeting; "" uses the festival's own default. */
    final String greeting;
    /** Focus glow colour as 0xAARRGGBB, or 0 for {@link #DEFAULT_ACCENT}. */
    final int accent;
    /**
     * The Christmas skin's snow is cleared by a wizard flying past on a broomstick instead of by the plough
     * (a theme's {@code snow_clearer} set to {@code broom}).
     */
    final boolean broom;

    private Festival(String name, LocalDate until, String greeting, int accent) {
        this(name, until, greeting, accent, false);
    }

    private Festival(String name, LocalDate until, String greeting, int accent, boolean broom) {
        this.name = name;
        this.until = until;
        this.greeting = greeting;
        this.accent = accent;
        this.broom = broom;
    }

    /** This skin with its snow cleared the way a theme's {@code snow_clearer} names: {@code broom}, or the plough. */
    Festival clearedBy(String clearer) {
        boolean flies = clearer != null && "broom".equals(clearer.trim().toLowerCase(Locale.ROOT));
        return flies == broom || name.isEmpty() ? this : new Festival(name, until, greeting, accent, flies);
    }

    /**
     * Builds the skin from raw config values. Anything malformed turns the skin off rather than leaving a
     * festival on the screen forever: an unreadable end date or an empty name gives {@link #NONE}.
     */
    static Festival parse(String name, String until, String greeting, String accent) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty() || n.length() > 40) return NONE;
        LocalDate end = null;
        if (until != null && !until.trim().isEmpty()) {
            try {
                end = LocalDate.parse(until.trim());
            } catch (DateTimeParseException e) {
                return NONE;
            }
        }
        String g = greeting == null ? "" : Cards.cleanLabel(greeting, "");
        return new Festival(n, end, g, parseColor(accent));
    }

    /** Whether the skin shows today. */
    boolean active(LocalDate today) {
        return !name.isEmpty() && (until == null || !today.isAfter(until));
    }

    /** The greeting to show while active, or "" to keep the normal time-of-day greeting. */
    String greetingText(Resources res) {
        if (!greeting.isEmpty()) return greeting;
        switch (name) {
            case "dussehra":
                return res.getString(R.string.festival_dussehra_greeting);
            case "diwali":
                return res.getString(R.string.festival_diwali_greeting);
            case HALLOWEEN:
                return res.getString(R.string.festival_halloween_greeting);
            case CHRISTMAS:
                return christmasGreeting(res, NewYear.today());
            default:
                return "";
        }
    }

    int accentColor() {
        if (accent != 0) return accent;
        return isHalloween() ? PUMPKIN : isChristmas() ? CANDLELIGHT : DEFAULT_ACCENT;
    }

    /** Whether this is the Christmas skin: a candle, a jingle bell and a snowflake. */
    boolean isChristmas() {
        return CHRISTMAS.equals(name);
    }

    /**
     * Nights left until 25 December, counted for the 60 days before it; 0 from Christmas Day to New Year's Eve
     * and for the rest of the year, -1 in January (the new year's greeting).
     */
    static int sleepsToChristmas(LocalDate today) {
        if (today.getMonthValue() == 1) return -1;
        LocalDate day = LocalDate.of(today.getYear(), 12, 25);
        if (today.isAfter(day)) return 0;
        long left = day.toEpochDay() - today.toEpochDay();
        return left <= 60 ? (int) left : 0;
    }

    /**
     * How much of the Advent candle is left: 1 until 1 December, a little less each day, 0 from Christmas Day
     * until the end of January.
     */
    static float candleLeft(LocalDate today) {
        int month = today.getMonthValue();
        if (month == 1) return 0f;
        if (month != 12) return 1f;
        return Math.max(0f, 1f - (today.getDayOfMonth() - 1) / 24f);
    }

    private static String christmasGreeting(Resources res, LocalDate today) {
        // the last two days of the year look ahead (NewYear): "2 days to 2027", then "New Year's Eve"
        int days = NewYear.daysToNewYear(today);
        if (days == 1) return res.getString(R.string.festival_new_year_eve);
        if (days > 1) return res.getString(R.string.festival_new_year_days, days, NewYear.year(today));
        int sleeps = sleepsToChristmas(today);
        if (sleeps < 0) return res.getString(R.string.festival_new_year_greeting);
        if (sleeps == 0) return res.getString(R.string.festival_christmas_greeting);
        if (sleeps == 1) return res.getString(R.string.festival_christmas_one_sleep);
        return res.getString(R.string.festival_christmas_sleeps, sleeps);
    }

    /** Whether this is the Halloween skin rather than the diya, bell and marigold. */
    boolean isHalloween() {
        return HALLOWEEN.equals(name);
    }

    /** {@code #RRGGBB} to an opaque colour, anything else to 0. */
    static int parseColor(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (!t.matches("#[0-9A-Fa-f]{6}")) return 0;
        return 0xFF000000 | Integer.parseInt(t.substring(1), 16);
    }
}
