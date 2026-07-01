package com.qyaaaa.cstaihuan.util;

/**
 * 将皮肤来源稀有度（例如 ByMykel/CSGO-API 里的 "Covert"、"Extraordinary"、
 * "Mil-Spec Grade"）归一到项目汰换档位：consumer / industrial / mil-spec /
 * restricted / classified / covert / gold。
 *
 * 刀和手套按武器名一律视为 {@code gold}，因为来源稀有度可能是 "Covert" 或
 * "Extraordinary"，但在汰换体系里都属于暗金（gold）档。
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
