package com.qyaaaa.cstaihuan;

public final class Rarity {
    private static final String[] ORDER = new String[] {
        "consumer",
        "industrial",
        "mil-spec",
        "restricted",
        "classified",
        "covert",
        "gold"
    };

    private Rarity() {
    }

    public static String next(String rarity) {
        if (rarity == null) {
            return null;
        }
        String lowered = rarity.trim().toLowerCase();
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(lowered)) {
                return i + 1 < ORDER.length ? ORDER[i + 1] : null;
            }
        }
        return null;
    }

    public static String previous(String rarity) {
        if (rarity == null) {
            return null;
        }
        String lowered = rarity.trim().toLowerCase();
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(lowered)) {
                return i - 1 >= 0 ? ORDER[i - 1] : null;
            }
        }
        return null;
    }
}
