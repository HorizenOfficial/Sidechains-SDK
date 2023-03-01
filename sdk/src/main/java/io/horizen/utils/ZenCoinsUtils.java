package io.horizen.utils;

public final class ZenCoinsUtils {
    private ZenCoinsUtils() {}

    public static long COIN = 100000000;
    public static long MAX_MONEY = 21000000 * COIN;
    public static long MC_DEFAULT_FEE_RATE = 100; // Default fee rate set in Mainchain.
                                                  // Value stored in satoshi per kilobyte.

    /**
      * This function calculates the minimum value of Backward Transfer output that Mainchain would accept.
      * It based on minimum bytes required for transaction(minimum length of MC transaction plus Backward Transfer Output
      * length) multiplied by the fee rate(it shows how much transaction would cost) and multiplied by 3(the constant
      * from Bitcoin sourcecode to make transaction reasonable).
      */
    public static long getMinDustThreshold(double feeRate) {
        final long MINIMUM_MCTX_LEN = 34;
        final long BWT_OUTPUT_LEN = 148;
        return 3 * (long)Math.floor((MINIMUM_MCTX_LEN + BWT_OUTPUT_LEN) * feeRate / 1000);
    }

    public static boolean isValidMoneyRange(long value) {
        return value >= 0 && value <= MAX_MONEY;
    }
}
