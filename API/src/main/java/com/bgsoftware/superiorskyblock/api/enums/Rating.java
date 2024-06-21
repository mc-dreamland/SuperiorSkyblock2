package com.bgsoftware.superiorskyblock.api.enums;

import java.util.Locale;

/**
 * Used to determine what rating has a player given to an island.
 */
public enum Rating {

    UNKNOWN,
    ZERO_STARS,
    ONE_STAR,
    TWO_STARS,
    THREE_STARS,
    FOUR_STARS,
    FIVE_STARS;

    private static String VALUES_STRING;

    /**
     * Get a string of all the rating names.
     */
    public static String getValuesString() {
        if (VALUES_STRING == null) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Rating rating : Rating.values())
                stringBuilder.append(", ").append(rating.toString().toLowerCase(Locale.ENGLISH));
            VALUES_STRING = stringBuilder.substring(2);
        }
        return VALUES_STRING;
    }

    /**
     * Get a rating by it's value.
     */
    public static Rating valueOf(int value) {
        return values()[value + 1];
    }

    /**
     * Get the integer value of the rating.
     */
    public int getValue() {
        return ordinal() - 1;
    }

}
