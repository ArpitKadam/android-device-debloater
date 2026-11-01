package com.gathin.tvhome;

/** Fork: the English greeting above Home's clock, a phrase per span of hours instead of four day parts. */
final class Greetings {

    /** First hour of each span; the last span runs to midnight. */
    private static final int[] FROM = {0, 1, 4, 7, 9, 12, 13, 16, 18, 20, 22};
    private static final String[] TEXT = {
            "Burning the midnight oil",
            "In the dead of night",
            "At first light",
            "Rise and shine",
            "Good morning",
            "High noon",
            "Good afternoon",
            "Golden hour",
            "Good evening",
            "Settle in",
            "Good night",
    };

    private Greetings() {
    }

    static String forHour(int hour) {
        int i = FROM.length - 1;
        while (i > 0 && hour < FROM[i]) i--;
        return TEXT[i];
    }
}
