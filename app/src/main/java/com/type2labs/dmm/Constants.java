package com.type2labs.dmm;

/**
 * Created by Thomas M. Klapwijk on 26/04/2017.
 */

public class Constants {

    public static final int MODE_VOLTAGE = 0;
    public static final int MODE_CURRENT = 1;
    public static final int MODE_RESISTANCE = 2;
    public static final int MODE_LIGHT = 3;
    public static final int MODE_CONTINUITY = 4;
    public static final int MODE_TRANSISTOR = 5;
    public static final int MODE_DIODE = 6;
    public static final int MODE_CAPACITOR = 7;
    public static final int MODE_INDUCTOR = 8;
    public static final int MODE_RMS = 9;
    public static final int MODE_FREQUENCY = 10;
    public static final int MODE_SIGGEN = 11;
    public static final int[] MODES = {MODE_VOLTAGE, MODE_CURRENT, MODE_RESISTANCE, MODE_LIGHT, MODE_CONTINUITY, MODE_TRANSISTOR,
            MODE_DIODE, MODE_CAPACITOR, MODE_INDUCTOR, MODE_RMS, MODE_FREQUENCY, MODE_SIGGEN};

    static final String[] MODES_STRING = {"Voltage", "Current", "Resistance", "Light", "Continuity", "Transistor",
            "Diode", "Capacitance", "Inductance", "RMS", "Frequency", "Sig-Gen"};
}
