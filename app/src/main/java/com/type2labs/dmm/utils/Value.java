package com.type2labs.dmm.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Mudkipz on 17/03/2017.
 */

public class Value {

    private final static String pattern = "([+-]?[\\d\\.]+)([a-zA-Z])([+-]?[\\d\\.]+)";

    private DecimalFormat df;

    private float value;
    private double scale;
    private char unit;

    public Value() {
        df = new DecimalFormat("##.###");
        df.setRoundingMode(RoundingMode.HALF_UP);
    }

    public Boolean getDaRegex(String message) {
        Pattern r = Pattern.compile(pattern);
        Matcher matcher = r.matcher(message);

        if (matcher.find()) {
            this.scale = Double.parseDouble(matcher.group(3));
            this.unit = matcher.group(2).charAt(0);
            this.value = Float.parseFloat(matcher.group(1));
            return true;
        } else {
            return false;
        }
    }

    public double getScale() {
        return scale;
    }

    public char getUnits() {
        return unit;
    }

    public float getValue() {
        return value;
    }

    public String toString() {
        return df.format(this.value) + this.unit;
    }
}
