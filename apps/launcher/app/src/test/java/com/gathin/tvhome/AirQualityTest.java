package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AirQualityTest {

    @Test
    public void bandsFollowTheEpaBreakpoints() {
        assertEquals(AirQuality.Level.GOOD, AirQuality.level(0));
        assertEquals(AirQuality.Level.GOOD, AirQuality.level(50));
        assertEquals(AirQuality.Level.MODERATE, AirQuality.level(51));
        assertEquals(AirQuality.Level.MODERATE, AirQuality.level(100));
        assertEquals(AirQuality.Level.SENSITIVE, AirQuality.level(101));
        assertEquals(AirQuality.Level.UNHEALTHY, AirQuality.level(200));
        assertEquals(AirQuality.Level.VERY_UNHEALTHY, AirQuality.level(201));
        assertEquals(AirQuality.Level.VERY_UNHEALTHY, AirQuality.level(300));
        assertEquals(AirQuality.Level.HAZARDOUS, AirQuality.level(301));
        assertEquals(AirQuality.Level.HAZARDOUS, AirQuality.level(999));
    }
}
