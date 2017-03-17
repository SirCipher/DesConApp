package com.type2labs.dmm.utils;

import android.util.Log;

/**
 * Created by Mudkipz on 17/03/2017.
 */

public class ValueUtils {

    /*
        Accepts an argument of ' <VALUE> Units'. Eg <12.2> Volts
        Returns the value of the received string. Returning what the value
        of what was between the limiter < and delimiter >
        Dies quietly if unable to extract the value
     */
    public static String getValue(String reading) {
        String value = "";
        try {
            value = reading.substring(reading.indexOf("<") + 1);

            return value.substring(0, value.indexOf(">"));

        } catch (StringIndexOutOfBoundsException e) {
            Log.d("ValueUtils", "Value not found");
        }
        return value;
    }

    /*
        Accepts an argument of ' <VALUE> Units'. Eg <12.2> Volts
        Returns the units of the received string. Returning what the units
        of what was between the limiter < and delimiter >
        Dies quietly if unable to extract the value
     */
    public static String getUnits(String reading) {
        String units = "";
        try {
            units = reading.substring(reading.lastIndexOf(" ") + 1);
        } catch (StringIndexOutOfBoundsException e) {
            Log.d("ValueUtils", "Units not found");
        }
        return units;
    }
}
