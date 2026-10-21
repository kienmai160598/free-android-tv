package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Xiaomi's source numbers, as its SourceManager reads them from the port labels (seen on MiTV-ASTP0). */
public class XiaomiInputsTest {

    @Test
    public void hdmiPortsAreTwentyThreeOnwards() {
        assertEquals(23, XiaomiInputs.source("HDMI1"));
        assertEquals(24, XiaomiInputs.source("HDMI2"));
        assertEquals(25, XiaomiInputs.source("HDMI 3"));
    }

    @Test
    public void avIsTwo() {
        assertEquals(2, XiaomiInputs.source("AV1"));
        assertEquals(2, XiaomiInputs.source("AV"));
    }

    @Test
    public void otherLabelsHaveNoSource() {
        assertEquals(-1, XiaomiInputs.source("Playback_1"));
        assertEquals(-1, XiaomiInputs.source("ADTV"));
        assertEquals(-1, XiaomiInputs.source("HDMI"));
        assertEquals(-1, XiaomiInputs.source(null));
    }
}
