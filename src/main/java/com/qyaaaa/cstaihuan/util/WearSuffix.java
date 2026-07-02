package com.qyaaaa.cstaihuan.util;

/**
 * 用于去掉磨损外观后缀并规范化饰品名，确保同一皮肤能跨 BUFF 目录、
 * 内置磨损范围快照等不同数据源稳定匹配。
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

    // CS 标准磨损外观档位，索引 0..4（崭新出厂到战痕累累）。
    public static final int TIER_FACTORY_NEW = 0;
    public static final int TIER_BATTLE_SCARRED = 4;
    // 档位 0..3 的开区间上界；档位 4 覆盖剩余范围直到 1.0。
    private static final double[] TIER_UPPER_BOUNDS = new double[] {0.07d, 0.15d, 0.38d, 0.45d};

    private WearSuffix() {
    }

    /** 将绝对磨损值映射到 CS 标准外观档位（0=崭新出厂，4=战痕累累）。 */
    public static int wearTierForFloat(double floatValue) {
        for (int i = 0; i < TIER_UPPER_BOUNDS.length; i++) {
            if (floatValue < TIER_UPPER_BOUNDS[i]) {
                return i;
            }
        }
        return TIER_BATTLE_SCARRED;
    }

    /** 从饰品名的磨损后缀解析外观档位；没有后缀时返回 -1。 */
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

    // 中文磨损外观后缀，按档位 0..4（崭新出厂到战痕累累）索引。
    private static final String[] ZH_WEAR_SUFFIXES = new String[] {
        " (崭新出厂)", " (略有磨损)", " (久经沙场)", " (破损不堪)", " (战痕累累)"
    };

    /** 根据绝对磨损所属标准档位返回中文外观后缀（如 0.3823 -> " (破损不堪)"）。 */
    public static String zhWearSuffixForFloat(double floatValue) {
        return ZH_WEAR_SUFFIXES[wearTierForFloat(floatValue)];
    }

    /**
     * 用给定磨损对应的中文外观后缀替换饰品名后缀。CS 外观档位由绝对磨损唯一决定，
     * 因此即使目录里只有某皮肤的一个外观档，也能得到正确档位名。
     */
    public static String withZhWearForFloat(String name, double floatValue) {
        return stripWearSuffix(name) + zhWearSuffixForFloat(floatValue);
    }

    /** 返回饰品名后缀对应的 CS 标准磨损档范围 [min,max]；没有后缀时返回完整 [0,1]。 */
    public static double[] standardWearRange(String name) {
        int tier = wearTierOfName(name);
        if (tier < 0) {
            return new double[] {0.0d, 1.0d};
        }
        double min = tier == 0 ? 0.0d : TIER_UPPER_BOUNDS[tier - 1];
        double max = tier < TIER_UPPER_BOUNDS.length ? TIER_UPPER_BOUNDS[tier] : 1.0d;
        return new double[] {min, max};
    }

    // BUFF 各磨损档的固定子区间切点（实测归纳自 goods/info 的 paintwear_choices）：
    // 崭新 {0.01,0.02,0.03,0.04}、略磨 {0.08,0.09,0.10,0.11}、久经 {0.18,0.21,0.24,0.27}、
    // 破损 {0.39,0.40,0.41,0.42}、战痕 {0.50,0.63,0.76,0.90}。
    // 特殊皮肤（自身 min/max float 受限，如头骨粉碎者久经=0.25-0.38）由“皮肤实际范围裁剪切点”自然得出，
    // 与 BUFF 动态返回的 choices 完全一致。
    private static final double[][] TIER_CUT_POINTS = new double[][] {
        {0.01d, 0.02d, 0.03d, 0.04d},
        {0.08d, 0.09d, 0.10d, 0.11d},
        {0.18d, 0.21d, 0.24d, 0.27d},
        {0.39d, 0.40d, 0.41d, 0.42d},
        {0.50d, 0.63d, 0.76d, 0.90d},
    };

    /**
     * 复刻 BUFF 的 paintwear_choices：按名字的磨损档取固定切点，再用皮肤实际 float 范围裁剪。
     * 返回该档的子区间列表 [[lo,hi]...]，最后一段即“底价段”。skinMin/skinMax 传 null 表示无限制。
     */
    public static double[][] buffPaintwearSegments(String name, Double skinMin, Double skinMax) {
        int tier = wearTierOfName(name);
        double[] range = standardWearRange(name);
        double lo = Math.max(range[0], skinMin == null ? 0.0d : skinMin.doubleValue());
        double hi = Math.min(range[1], skinMax == null ? 1.0d : skinMax.doubleValue());
        if (hi <= lo) {
            return new double[][] {{range[0], range[1]}};
        }
        java.util.List<double[]> segments = new java.util.ArrayList<double[]>();
        double cursor = lo;
        if (tier >= 0 && tier < TIER_CUT_POINTS.length) {
            for (double cut : TIER_CUT_POINTS[tier]) {
                if (cut > lo && cut < hi) {
                    segments.add(new double[] {cursor, cut});
                    cursor = cut;
                }
            }
        }
        segments.add(new double[] {cursor, hi});
        return segments.toArray(new double[segments.size()][]);
    }

    /** 去掉尾部磨损外观后缀（例如 " (Field-Tested)" / " (久经沙场)"）。 */
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

    /** 去首尾空白并转小写，作为 Map 键的基础规范形式。 */
    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    /**
     * 构造跨数据源匹配键：去掉磨损后缀、StatTrak/★/纪念品标记（这些版本磨损范围一致），
     * 合并空白并转小写。
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
     * 更强的跨数据源匹配键，用于对齐 BUFF/目录饰品名与 ByMykel 的 skin_float_range 名单：
     * 去掉磨损、StatTrak/★/纪念品标记及其括号，统一部分武器别名（如 CZ75自动型→CZ75、
     * M4A1消音型→消音版），移除全部空白并转小写。用于处理数据源命名不一致时的磨损范围查找。
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
