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

    // Chinese exterior suffixes indexed by tier 0..4 (Factory New .. Battle-Scarred).
    private static final String[] ZH_WEAR_SUFFIXES = new String[] {
        " (崭新出厂)", " (略有磨损)", " (久经沙场)", " (破损不堪)", " (战痕累累)"
    };

    /** The Chinese exterior suffix for an absolute float's standard wear tier (0.3823 -> " (破损不堪)"). */
    public static String zhWearSuffixForFloat(double floatValue) {
        return ZH_WEAR_SUFFIXES[wearTierForFloat(floatValue)];
    }

    /**
     * Replaces a skin name's wear suffix with the one matching the given float. In CS the exterior
     * is a fixed function of the absolute float, so this yields the correct tier even when the
     * catalog only has one wear variant of the skin.
     */
    public static String withZhWearForFloat(String name, double floatValue) {
        return stripWearSuffix(name) + zhWearSuffixForFloat(floatValue);
    }

    /** Standard CS float range [min,max] of the wear tier encoded in the name; full [0,1] if none. */
    public static double[] standardWearRange(String name) {
        int tier = wearTierOfName(name);
        if (tier < 0) {
            return new double[] {0.0d, 1.0d};
        }
        double min = tier == 0 ? 0.0d : TIER_UPPER_BOUNDS[tier - 1];
        double max = tier < TIER_UPPER_BOUNDS.length ? TIER_UPPER_BOUNDS[tier] : 1.0d;
        return new double[] {min, max};
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

    /**
     * Stronger cross-source match key for aligning BUFF/catalog skin names with the ByMykel
     * (skin_float_range) roster: drops wear + StatTrak/★/Souvenir markers (incl. their parens),
     * normalizes weapon-name aliases (CZ75自动型→CZ75, M4A1消音型→消音版), removes ALL whitespace,
     * lower-cases. Used for paint-range lookups where naming differs across sources.
     */
    public static String toRangeMatchKey(String name) {
        if (name == null) {
            return "";
        }
        String base = stripWearSuffix(name);
        base = base
            .replace("（StatTrak™）", "")
            .replace("（纪念品）", "")
            .replace("(StatTrak™)", "")
            .replace("(纪念品)", "")
            .replace("StatTrak™", "")
            .replace("StatTrak", "")
            .replace("★", "")
            .replace("™", "")
            .replace("纪念品", "")
            .replace("Souvenir", "")
            .replace("自动型", "")
            .replace("消音型", "消音版");
        return base.replaceAll("\\s+", "").toLowerCase();
    }
}
