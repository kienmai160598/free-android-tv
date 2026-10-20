package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Vietnamese lunar calendar, after Hồ Ngọc Đức's algorithm (astronomical new moons and solar terms
 * reckoned at UTC+7). The Chinese calendar reckons at UTC+8 and differs from it in some years, so this
 * must not be swapped for a generic Chinese-calendar library. Also the year's can chi and the next
 * holiday, for the calendar widget.
 */
final class LunarCalendar {

    /** Vietnam's time zone, in hours: the calendar's new moons fall on the day they fall in Hanoi. */
    static final double TIME_ZONE = 7.0;

    private static final String[] CAN = {"Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý"};
    private static final String[] CHI = {"Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi"};

    /** A lunar date: day, month, year, and whether the month is the leap (nhuận) one. */
    static final class Date {
        final int day, month, year;
        final boolean leap;

        Date(int day, int month, int year, boolean leap) {
            this.day = day;
            this.month = month;
            this.year = year;
            this.leap = leap;
        }
    }

    /** The holidays the widget counts down to: fixed on the solar or on the lunar calendar. */
    enum Holiday {
        NEW_YEAR(false, 1, 1), TET(true, 1, 1), HUNG_KINGS(true, 10, 3), REUNIFICATION(false, 30, 4),
        LABOUR(false, 1, 5), MID_AUTUMN(true, 15, 8), NATIONAL_DAY(false, 2, 9), CHRISTMAS(false, 25, 12);

        final boolean lunar;
        final int day, month;

        Holiday(boolean lunar, int day, int month) {
            this.lunar = lunar;
            this.day = day;
            this.month = month;
        }

        /** Its solar date in the year it falls on or after from (lunar ones by their lunar year). */
        LocalDate onOrAfter(LocalDate from) {
            for (int year = from.getYear() - 1; year <= from.getYear() + 1; year++) {
                LocalDate d = lunar ? toSolar(day, month, year, false) : LocalDate.of(year, month, day);
                if (d != null && !d.isBefore(from)) return d;
            }
            return null;
        }
    }

    /** The next holiday on or after today, and how many days away it is. */
    static final class Upcoming {
        final Holiday holiday;
        final long days;

        Upcoming(Holiday holiday, long days) {
            this.holiday = holiday;
            this.days = days;
        }
    }

    private LunarCalendar() {
    }

    static Upcoming nextHoliday(LocalDate today) {
        List<Upcoming> all = upcoming(today);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every holiday's next date from today, soonest first. */
    static List<Upcoming> upcoming(LocalDate today) {
        List<Upcoming> all = new ArrayList<>();
        for (Holiday h : Holiday.values()) {
            LocalDate d = h.onOrAfter(today);
            if (d != null) all.add(new Upcoming(h, ChronoUnit.DAYS.between(today, d)));
        }
        Collections.sort(all, (a, b) -> Long.compare(a.days, b.days));
        return all;
    }

    /** The lunar year's name, e.g. "Bính Ngọ" for 2026. */
    static String canChi(int lunarYear) {
        return CAN[(lunarYear + 6) % 10] + " " + CHI[(lunarYear + 8) % 12];
    }

    static Date toLunar(LocalDate solar) {
        int dayNumber = jdFromDate(solar.getDayOfMonth(), solar.getMonthValue(), solar.getYear());
        int k = (int) Math.floor((dayNumber - 2415021.076998695) / 29.530588853);
        int monthStart = newMoonDay(k + 1);
        if (monthStart > dayNumber) monthStart = newMoonDay(k);
        int yy = solar.getYear();
        int a11 = lunarMonth11(yy), b11 = a11;
        int lunarYear;
        if (a11 >= monthStart) {
            lunarYear = yy;
            a11 = lunarMonth11(yy - 1);
        } else {
            lunarYear = yy + 1;
            b11 = lunarMonth11(yy + 1);
        }
        int lunarDay = dayNumber - monthStart + 1;
        int diff = (monthStart - a11) / 29;
        boolean leap = false;
        int lunarMonth = diff + 11;
        if (b11 - a11 > 365) {
            int leapMonthDiff = leapMonthOffset(a11);
            if (diff >= leapMonthDiff) {
                lunarMonth = diff + 10;
                if (diff == leapMonthDiff) leap = true;
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12;
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1;
        return new Date(lunarDay, lunarMonth, lunarYear, leap);
    }

    /** The solar date of a lunar one, or null when that leap month does not exist that year. */
    static LocalDate toSolar(int lunarDay, int lunarMonth, int lunarYear, boolean leap) {
        int a11, b11;
        if (lunarMonth < 11) {
            a11 = lunarMonth11(lunarYear - 1);
            b11 = lunarMonth11(lunarYear);
        } else {
            a11 = lunarMonth11(lunarYear);
            b11 = lunarMonth11(lunarYear + 1);
        }
        int k = (int) Math.floor(0.5 + (a11 - 2415021.076998695) / 29.530588853);
        int off = lunarMonth - 11;
        if (off < 0) off += 12;
        if (b11 - a11 > 365) {
            int leapOff = leapMonthOffset(a11);
            int leapMonth = leapOff - 2;
            if (leapMonth < 0) leapMonth += 12;
            if (leap && lunarMonth != leapMonth) return null;
            if (leap || off >= leapOff) off += 1;
        } else if (leap) {
            return null;
        }
        int monthStart = newMoonDay(k + off);
        return fromJd(monthStart + lunarDay - 1);
    }

    // ---- astronomy -------------------------------------------------------------------------------

    static int jdFromDate(int dd, int mm, int yy) {
        int a = (14 - mm) / 12;
        int y = yy + 4800 - a;
        int m = mm + 12 * a - 3;
        int jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
        return jd;
    }

    private static LocalDate fromJd(int jd) {
        int a, b, c;
        if (jd > 2299160) {
            a = jd + 32044;
            b = (4 * a + 3) / 146097;
            c = a - (b * 146097) / 4;
        } else {
            b = 0;
            c = jd + 32082;
        }
        int d = (4 * c + 3) / 1461;
        int e = c - (1461 * d) / 4;
        int m = (5 * e + 2) / 153;
        int day = e - (153 * m + 2) / 5 + 1;
        int month = m + 3 - 12 * (m / 10);
        int year = b * 100 + d - 4800 + m / 10;
        return LocalDate.of(year, month, day);
    }

    /** The day (Julian day number) of the k-th new moon since 1900-01-01. */
    private static int newMoonDay(int k) {
        double t = k / 1236.85, t2 = t * t, t3 = t2 * t, dr = Math.PI / 180;
        double jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3;
        jd1 += 0.00033 * Math.sin((166.56 + 132.87 * t - 0.009173 * t2) * dr);
        double m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3;
        double mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3;
        double f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3;
        double c1 = (0.1734 - 0.000393 * t) * Math.sin(m * dr) + 0.0021 * Math.sin(2 * dr * m);
        c1 = c1 - 0.4068 * Math.sin(mpr * dr) + 0.0161 * Math.sin(dr * 2 * mpr);
        c1 = c1 - 0.0004 * Math.sin(dr * 3 * mpr);
        c1 = c1 + 0.0104 * Math.sin(dr * 2 * f) - 0.0051 * Math.sin(dr * (m + mpr));
        c1 = c1 - 0.0074 * Math.sin(dr * (m - mpr)) + 0.0004 * Math.sin(dr * (2 * f + m));
        c1 = c1 - 0.0004 * Math.sin(dr * (2 * f - m)) - 0.0006 * Math.sin(dr * (2 * f + mpr));
        c1 = c1 + 0.0010 * Math.sin(dr * (2 * f - mpr)) + 0.0005 * Math.sin(dr * (2 * mpr + m));
        double deltaT = t < -11
                ? 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
                : -0.000278 + 0.000265 * t + 0.000262 * t2;
        return (int) Math.floor(jd1 + c1 - deltaT + 0.5 + TIME_ZONE / 24);
    }

    /** The sun's longitude on that day, as one of 12 thirty-degree sectors. */
    private static int sunLongitude(int jdn) {
        double t = (jdn - 2451545.5 - TIME_ZONE / 24) / 36525, t2 = t * t, dr = Math.PI / 180;
        double m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2;
        double l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2;
        double dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * Math.sin(dr * m);
        dl = dl + (0.019993 - 0.000101 * t) * Math.sin(dr * 2 * m) + 0.000290 * Math.sin(dr * 3 * m);
        double l = (l0 + dl) * dr;
        l = l - Math.PI * 2 * Math.floor(l / (Math.PI * 2));
        return (int) Math.floor(l / Math.PI * 6);
    }

    /** The start of the 11th lunar month (the one holding the winter solstice) of that solar year. */
    private static int lunarMonth11(int yy) {
        double off = jdFromDate(31, 12, yy) - 2415021;
        int k = (int) Math.floor(off / 29.530588853);
        int nm = newMoonDay(k);
        if (sunLongitude(nm) >= 9) nm = newMoonDay(k - 1);
        return nm;
    }

    /** Which month after the 11th is the leap one, in a 13-month year starting at a11. */
    private static int leapMonthOffset(int a11) {
        int k = (int) Math.floor((a11 - 2415021.076998695) / 29.530588853 + 0.5);
        int last, i = 1;
        int arc = sunLongitude(newMoonDay(k + i));
        do {
            last = arc;
            i++;
            arc = sunLongitude(newMoonDay(k + i));
        } while (arc != last && i < 14);
        return i - 1;
    }
}
