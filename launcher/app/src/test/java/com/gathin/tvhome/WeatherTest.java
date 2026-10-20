package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherTest {

    @Test
    public void wmoCodesGroupIntoWhatTheWidgetDraws() {
        assertEquals(Weather.Sky.CLEAR, Weather.sky(0));
        assertEquals(Weather.Sky.PARTLY, Weather.sky(1));
        assertEquals(Weather.Sky.PARTLY, Weather.sky(2));
        assertEquals(Weather.Sky.CLOUDY, Weather.sky(3));
        assertEquals(Weather.Sky.FOG, Weather.sky(45));
        assertEquals(Weather.Sky.FOG, Weather.sky(48));
        assertEquals(Weather.Sky.RAIN, Weather.sky(51));
        assertEquals(Weather.Sky.RAIN, Weather.sky(63));
        assertEquals(Weather.Sky.RAIN, Weather.sky(81));
        assertEquals(Weather.Sky.SNOW, Weather.sky(73));
        assertEquals(Weather.Sky.SNOW, Weather.sky(86));
        assertEquals(Weather.Sky.THUNDER, Weather.sky(95));
        assertEquals(Weather.Sky.THUNDER, Weather.sky(99));
    }
}
