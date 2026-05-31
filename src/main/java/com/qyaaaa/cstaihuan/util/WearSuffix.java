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

    private WearSuffix() {
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
