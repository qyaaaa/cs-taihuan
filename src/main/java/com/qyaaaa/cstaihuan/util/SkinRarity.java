package com.qyaaaa.cstaihuan.util;

/**
 * Normalizes a skin's source rarity (e.g. ByMykel/CSGO-API values like "Covert",
 * "Extraordinary", "Mil-Spec Grade") into the trade-up rarity scheme used across the
 * app: consumer / industrial / mil-spec / restricted / classified / covert / gold.
 *
 * Knives and gloves are always treated as {@code gold} based on the weapon name, since
 * their source rarity is inconsistent ("Covert"/"Extraordinary") yet they are the暗金
 * (gold) tier for trade-up purposes.
 */
public final class SkinRarity {
    private SkinRarity() {
    }

    public static String normalize(String rarity, String weapon) {
        String r = rarity == null ? "" : rarity.trim().toLowerCase();
        if (r.contains("extraordinary") || isKnifeOrGlove(weapon)) {
            return "gold";
        }
        if (r.contains("covert")) {
            return "covert";
        }
        if (r.contains("classified")) {
            return "classified";
        }
        if (r.contains("restricted")) {
            return "restricted";
        }
        if (r.contains("mil-spec") || r.contains("mil spec")) {
            return "mil-spec";
        }
        if (r.contains("industrial")) {
            return "industrial";
        }
        if (r.contains("consumer")) {
            return "consumer";
        }
        return r;
    }

    public static boolean isKnifeOrGlove(String weapon) {
        String w = weapon == null ? "" : weapon.trim().toLowerCase();
        return w.contains("knife") || w.contains("glove") || w.contains("hand wrap")
            || w.contains("karambit") || w.contains("bayonet") || w.contains("daggers");
    }
}
