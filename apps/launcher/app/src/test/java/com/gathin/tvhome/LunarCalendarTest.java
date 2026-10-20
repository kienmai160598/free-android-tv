package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class LunarCalendarTest {

    private static void assertLunar(LocalDate solar, int day, int month, int year) {
        LunarCalendar.Date d = LunarCalendar.toLunar(solar);
        assertEquals(solar + " day", day, d.day);
        assertEquals(solar + " month", month, d.month);
        assertEquals(solar + " year", year, d.year);
    }

    @Test
    public void tetFallsOnTheFirstOfTheFirstMonth() {
        assertLunar(LocalDate.of(2024, 2, 10), 1, 1, 2024);
        assertLunar(LocalDate.of(2025, 1, 29), 1, 1, 2025);
        assertLunar(LocalDate.of(2026, 2, 17), 1, 1, 2026);
        assertLunar(LocalDate.of(2027, 2, 6), 1, 1, 2027);
    }

    @Test
    public void dayBeforeTetIsTheLastDayOfTheOldYear() {
        LunarCalendar.Date d = LunarCalendar.toLunar(LocalDate.of(2026, 2, 16));
        assertEquals(12, d.month);
        assertEquals(2025, d.year);
    }

    @Test
    public void midAutumnIsTheFifteenthOfTheEighthMonth() {
        assertLunar(LocalDate.of(2025, 10, 6), 15, 8, 2025);
        assertLunar(LocalDate.of(2026, 9, 25), 15, 8, 2026);
    }

    @Test
    public void leapSixthMonthOf2025() {
        // 2025 has a leap 6th month (6 nhuận) starting 25 July.
        LunarCalendar.Date d = LunarCalendar.toLunar(LocalDate.of(2025, 7, 25));
        assertEquals(1, d.day);
        assertEquals(6, d.month);
        assertTrue(d.leap);
        assertFalse(LunarCalendar.toLunar(LocalDate.of(2025, 6, 25)).leap);
    }

    @Test
    public void lunarToSolarRoundTrips() {
        for (LocalDate d = LocalDate.of(2024, 1, 1); d.isBefore(LocalDate.of(2028, 1, 1)); d = d.plusDays(1)) {
            LunarCalendar.Date l = LunarCalendar.toLunar(d);
            assertEquals(d, LunarCalendar.toSolar(l.day, l.month, l.year, l.leap));
        }
    }

    @Test
    public void noLeapMonthInAPlainYear() {
        assertNull(LunarCalendar.toSolar(1, 6, 2026, true));
    }

    @Test
    public void canChi() {
        assertEquals("Bính Ngọ", LunarCalendar.canChi(2026));
        assertEquals("Ất Tỵ", LunarCalendar.canChi(2025));
        assertEquals("Đinh Mùi", LunarCalendar.canChi(2027));
    }

    @Test
    public void nextHolidayCountsDownFromToday() {
        LunarCalendar.Upcoming u = LunarCalendar.nextHoliday(LocalDate.of(2026, 10, 21));
        assertEquals(LunarCalendar.Holiday.CHRISTMAS, u.holiday);
        assertEquals(65, u.days);
        LunarCalendar.Upcoming tet = LunarCalendar.nextHoliday(LocalDate.of(2027, 1, 2));
        assertEquals(LunarCalendar.Holiday.TET, tet.holiday);
        assertEquals(35, tet.days);
        assertEquals(0, LunarCalendar.nextHoliday(LocalDate.of(2026, 9, 2)).days);
    }
}
