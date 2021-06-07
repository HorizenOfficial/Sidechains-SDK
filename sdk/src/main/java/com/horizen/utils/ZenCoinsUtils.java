package com.horizen.utils;

public final class ZenCoinsUtils {
    private ZenCoinsUtils() {}

    public static long COIN = 100000000;
    public static long MAX_MONEY = 21000000 * COIN;

    public static boolean isValidMoneyRange(long value) {
        return value >= 0 && value <= MAX_MONEY;
    }
}
