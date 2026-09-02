package org.solarframework.tournament.util;

/** Pure bracket arithmetic - no entities involved. */
public final class Brackets {
    private Brackets() {}

    /** Smallest power of two greater than or equal to {@code n} (minimum 2). */
    public static int nextPowerOfTwo(int n) {
        int size = 2;
        while (size < n) size <<= 1;
        return size;
    }

    public static int log2(int powerOfTwo) { return Integer.numberOfTrailingZeros(powerOfTwo); }

    /**
     * Standard bracket seeding order: slot {@code i} of round one holds seed {@code order[i]},
     * so seed 1 and seed 2 can only meet in the final.
     * <p>Size 8 gives {@code [1,8,4,5,2,7,3,6]} - matches 1v8, 4v5, 2v7, 3v6.
     */
    public static int[] seedOrder(int size) {
        int[] order = {1};
        while (order.length < size) {
            int n = order.length * 2;
            int[] next = new int[n];
            for (int i = 0; i < order.length; i++) {
                next[2 * i] = order[i];
                next[2 * i + 1] = n + 1 - order[i];
            }
            order = next;
        }
        return order;
    }

    /** Human name for a winners-bracket round counting back from the final. */
    public static String roundName(int round, int totalRounds) {
        int fromEnd = totalRounds - round;
        return switch (fromEnd) {
            case 0 -> "Final";
            case 1 -> "Semifinals";
            case 2 -> "Quarterfinals";
            default -> "Round of " + (1 << (fromEnd + 1));
        };
    }
}
