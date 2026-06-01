package com.qyaaaa.cstaihuan.util;

/**
 * Helpers for stripping wear (exterior) suffixes and normalizing skin names so the same
 * skin matches across data sources (BUFF catalog, the bundled float-range snapshot, etc.).
 */
public final class WearSuffix {
    private static final String[] WEAR_SUFFIXES = new String[] {
        " (Factory New)",
        " (Minimal Wear)",
        " (Field-Tested)",
        " (Well-Worn)",
        " (Battle-Scarred)",
        " (崭新出厂)",
        " (略有磨损)",
        " (久经沙场)",
        " (破损不堪)",
        " (战痕累累)"
    };

    // Standard CS exterior tiers, indexed 0..4 (Factory New .. Battle-Scarred).
    public static final int TIER_FACTORY_NEW = 0;
    public static final int TIER_BATTLE_SCARRED = 4;
    // Upper bounds (exclusive) for tiers 0..3; tier 4 covers the rest up to 1.0.
    private static final double[] TIER_UPPER_BOUNDS = new double[] {0.07d, 0.15d, 0.38d, 0.45d};

    private WearSuffix() {
    }

    /** Maps an absolute float value to the standard CS exterior tier (0=Factory New .. 4=Battle-Scarred). */
    public static int wearTierForFloat(double floatValue) {
        for (int i = 0; i < TIER_UPPER_BOUNDS.length; i++) {
            if (floatValue < TIER_UPPER_BOUNDS[i]) {
                return i;
            }
        }
        return TIER_BATTLE_SCARRED;
    }

    /** Returns the exterior tier encoded in a skin name's wear suffix, or -1 if it has none. */
    public static int wearTierOfName(String name) {
        if (name == null) {
            return -1;
        }
        String trimmed = name.trim();
        for (int i = 0; i < WEAR_SUFFIXES.length; i++) {
            if (trimmed.endsWith(WEAR_SUFFIXES[i])) {
                return i % 5;
            }
        }
        return -1;
    }

    /** Removes a trailing exterior suffix (e.g. " (Field-Tested)" / " (久经沙场)"). */
    public static String stripWearSuffix(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        for (String suffix : WEAR_SUFFIXES) {
            if (trimmed.endsWith(suffix)) {
                return trimmed.substring(0, trimmed.length() - suffix.length()).trim();
            }
        }
        return trimmed;
    }

    /** Lower-cases and trims; the canonical form for map keys. */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * Builds a cross-source match key for a skin: drop the wear suffix, drop StatTrak/★/Souvenir
     * markers (wear range is identical across those variants), collapse whitespace, lower-case.
     */
    public static String toMatchKey(String name) {
        if (name == null) {
            return "";
        }
        String base = stripWearSuffix(name);
        base = base
            .replace("StatTrak™", "")
            .replace("StatTrak", "")
            .replace("（★）", "")
            .replace("(★)", "")
            .replace("★", "")
            .replace("纪念品", "")
            .replace("Souvenir", "");
        base = base.replaceAll("\\s+", " ").trim();
        return normalize(base);
    }
}
