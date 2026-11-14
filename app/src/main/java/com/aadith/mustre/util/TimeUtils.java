package com.aadith.mustre.util;

import java.util.Locale;

public class TimeUtils {

    /**
     * Formats duration in milliseconds to "mm:ss" or "h:mm:ss".
     */
    public static String formatTime(int milliseconds) {
        int ms = Math.max(0, milliseconds);
        int totalSeconds = ms / 1000;
        int seconds = totalSeconds % 60;
        int totalMinutes = totalSeconds / 60;
        int minutes = totalMinutes % 60;
        int hours = totalMinutes / 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%d:%02d", totalMinutes, seconds);
        }
    }
}
