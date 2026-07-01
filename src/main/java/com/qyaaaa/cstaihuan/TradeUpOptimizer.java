package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.FloatPriceBand;
import com.qyaaaa.cstaihuan.model.Outcome;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TradeUpOptimizer {
    private static final String KEY_SEPARATOR = "||";
    private static final String[] GLOVE_KEYWORDS = new String[] {
        "glove",
        "hand wrap",
        "手套",
        "裹手",
        "驾驶",
        "运动",
        "专业",
        "摩托",
        "九头蛇",
        "狂牙",
        "血猎"
    };
    private static final Comparator<FloatPriceBand> PRICE_BAND_BY_MIN_FLOAT = (left, right) ->
        Double.compare(left.getMinFloat(), right.getMinFloat());
    private static final Comparator<BuffItem> INPUT_BY_PRICE_AND_FLOAT = (left, right) -> {
        int byPrice = Double.compare(left.getPrice(), right.getPrice());
        if (byPrice != 0) {
            return byPrice;
        }
        return Double.compare(left.getFloatValue(), right.getFloatValue());
    };
    private static final Comparator<Outcome> OUTCOME_BY_PROBABILITY_DESC = (left, right) ->
        Double.compare(right.getProbability(), left.getProbability());

    private final double saleFeeRate;
    private final Map<String, Double> outputPriceFactors = new HashMap<String, Double>();
    private final Map<String, List<FloatPriceBand>> outputPriceBands = new HashMap<String, List<FloatPriceBand>>();
    private final Map<String, CatalogSkin> catalogByName = new HashMap<String, CatalogSkin>();
    // 基础皮肤名（去掉磨损后缀）-> 各磨损档目录变体，用于按素材自身磨损档目录价计成本，而不是用整皮地板价。
    private final Map<String, List<CatalogSkin>> catalogVariantsByBaseName = new HashMap<String, List<CatalogSkin>>();
    // 皮肤名(去磨损后缀+小写) -> 权威 paint 范围[min,max]，用于归一化输入磨损与缩放产出磨损。
    private Map<String, double[]> skinRanges = new HashMap<String, double[]>();
    private final Map<String, List<OutputFamily>> outputFamiliesByCollectionRarityAndTrack = new HashMap<String, List<OutputFamily>>();
    private final Map<String, List<OutputFamily>> statTrakGoldKnifeFamiliesByCollection = new HashMap<String, List<OutputFamily>>();

    public TradeUpOptimizer(List<CatalogSkin> catalog, double saleFeeRate) {
        this(catalog, saleFeeRate, null);
    }

    public TradeUpOptimizer(List<CatalogSkin> catalog, double saleFeeRate, Map<String, Double> outputPriceFactors) {
        this(catalog, saleFeeRate, outputPriceFactors, null);
    }

    // 预处理 catalog：按基础皮肤名合并不同磨损档位，并按收藏品/品质/暗金状态建立产出池索引。
    public TradeUpOptimizer(List<CatalogSkin> catalog, double saleFeeRate, Map<String, Double> outputPriceFactors, Map<String, List<FloatPriceBand>> outputPriceBands) {
        this.saleFeeRate = saleFeeRate;
        if (outputPriceFactors != null) {
            for (Map.Entry<String, Double> entry : outputPriceFactors.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue().doubleValue() > 0.0d) {
                    this.outputPriceFactors.put(normalizeKey(entry.getKey()), entry.getValue());
                }
            }
        }
        if (outputPriceBands != null) {
            for (Map.Entry<String, List<FloatPriceBand>> entry : outputPriceBands.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                List<FloatPriceBand> bands = new ArrayList<FloatPriceBand>();
                for (FloatPriceBand band : entry.getValue()) {
                    if (isValidPriceBand(band)) {
                        bands.add(band);
                    }
                }
                if (!bands.isEmpty()) {
                    Collections.sort(bands, PRICE_BAND_BY_MIN_FLOAT);
                    this.outputPriceBands.put(normalizeKey(entry.getKey()), bands);
                }
            }
        }
        Map<String, OutputFamily> outputFamilyByIdentity = new LinkedHashMap<String, OutputFamily>();
        for (CatalogSkin skin : catalog) {
            catalogByName.put(skin.getName(), skin);
            String baseKey = normalizeKey(baseSkinName(skin.getName()));
            List<CatalogSkin> variants = catalogVariantsByBaseName.get(baseKey);
            if (variants == null) {
                variants = new ArrayList<CatalogSkin>();
                catalogVariantsByBaseName.put(baseKey, variants);
            }
            variants.add(skin);

            // 纪念品可作为汰换素材投入，但只能从纪念品包开出、不能作为汰换产物，因此仅从产物池排除。
            if (isSouvenirName(skin.getName())) {
                continue;
            }
            String familyKey = outputFamilyKey(skin);
            OutputFamily family = outputFamilyByIdentity.get(familyKey);
            if (family == null) {
                family = new OutputFamily(skin.getCollection(), skin.getRarity(), isStatTrakName(skin.getName()), skin.getCategoryKey(), baseSkinName(skin.getName()));
                outputFamilyByIdentity.put(familyKey, family);
            }
            family.addVariant(skin);
        }
        for (OutputFamily family : outputFamilyByIdentity.values()) {
            String familyGroupKey = outputFamilyGroupKey(family.collection, family.rarity, family.statTrak);
            List<OutputFamily> rows = outputFamiliesByCollectionRarityAndTrack.get(familyGroupKey);
            if (rows == null) {
                rows = new ArrayList<OutputFamily>();
                outputFamiliesByCollectionRarityAndTrack.put(familyGroupKey, rows);
            }
            rows.add(family);
            if ("gold".equals(family.rarity) && family.statTrak && !family.isGloveFamily()) {
                List<OutputFamily> knifeRows = statTrakGoldKnifeFamiliesByCollection.get(family.collection);
                if (knifeRows == null) {
                    knifeRows = new ArrayList<OutputFamily>();
                    statTrakGoldKnifeFamiliesByCollection.put(family.collection, knifeRows);
                }
                knifeRows.add(family);
            }
        }
    }

    // 用 catalog 中的标准收藏品和品质补齐库存数据，避免 BUFF 库存字段缺失导致方案漏算。
    // 注入皮肤权威 paint 范围（来自 skin_float_range），供归一化磨损计算使用；未注入则回退旧行为。
    public void setSkinRanges(Map<String, double[]> ranges) {
        this.skinRanges = ranges == null ? new HashMap<String, double[]>() : ranges;
    }

    // 皮肤的权威 paint 范围[min,max]；查不到返回 null（调用方回退）。
    private double[] skinRange(String name) {
        if (name == null) {
            return null;
        }
        return skinRanges.get(com.qyaaaa.cstaihuan.util.WearSuffix.toRangeMatchKey(name));
    }

    // 输入磨损归一化到其皮肤 paint 范围内的 [0,1]；无范围时直接用原始磨损（clamp 到 [0,1]）。
    private double normalizedInputFloat(String name, double floatValue) {
        double[] range = skinRange(name);
        double norm;
        if (range != null && range[1] > range[0]) {
            norm = (floatValue - range[0]) / (range[1] - range[0]);
        } else {
            norm = floatValue;
        }
        if (norm < 0.0d) {
            norm = 0.0d;
        }
        if (norm > 1.0d) {
            norm = 1.0d;
        }
        return norm;
    }

    public List<BuffItem> enrichInventory(List<BuffItem> items) {
        List<BuffItem> enriched = new ArrayList<BuffItem>();
        for (BuffItem item : items) {
            CatalogSkin skin = catalogByName.get(item.getName());
            // 素材按自身磨损档目录价计价；库存价是整皮 sell_min_price，会低估低磨素材的 EV 成本。
            double wearPrice = wearTierInputPrice(item);
            if (skin != null) {
                enriched.add(item.withCatalog(skin, wearPrice));
            } else if (item.getCollection() != null && item.getFilterRarity() != null) {
                enriched.add(item.withPrice(wearPrice));
            }
        }
        return enriched;
    }

    // 解析投入素材自身磨损档对应的目录价：先精确匹配带磨损后缀名称，再匹配同基础皮肤中与素材磨损档一致的变体，
    // 最后回退到库存价，确保缺少目录行时不会算崩 EV。
    private double wearTierInputPrice(BuffItem item) {
        CatalogSkin exact = catalogByName.get(item.getName());
        if (exact != null && exact.getPrice() > 0.0d) {
            return exact.getPrice();
        }
        Double floatValue = item.getFloatValue();
        if (floatValue != null) {
            CatalogSkin variant = variantForFloatTier(
                catalogVariantsByBaseName.get(normalizeKey(baseSkinName(item.getName()))), floatValue.doubleValue());
            if (variant != null && variant.getPrice() > 0.0d) {
                return variant.getPrice();
            }
        }
        return item.getPrice();
    }

    // 选择名称磨损档与该 float 的 CS 标准外观档一致的变体。逻辑与 OutputFamily.selectVariantForFloat 保持一致：
    // BUFF 单个外观档的 paintwear_range 不稳定，因此按名称档位匹配，而不是按变体 min/max float 匹配。
    private static CatalogSkin variantForFloatTier(List<CatalogSkin> variants, double floatValue) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        int targetTier = com.qyaaaa.cstaihuan.util.WearSuffix.wearTierForFloat(floatValue);
        CatalogSkin tierFallback = null;
        int bestTierDistance = Integer.MAX_VALUE;
        for (CatalogSkin variant : variants) {
            int variantTier = com.qyaaaa.cstaihuan.util.WearSuffix.wearTierOfName(variant.getName());
            if (variantTier == targetTier) {
                return variant;
            }
            if (variantTier >= 0) {
                int tierDistance = Math.abs(variantTier - targetTier);
                if (tierDistance < bestTierDistance) {
                    bestTierDistance = tierDistance;
                    tierFallback = variant;
                }
            }
        }
        return tierFallback != null ? tierFallback : variants.get(0);
    }

    public List<TradeUpPlan> findBestContracts(List<BuffItem> items, int topK, int maxItemsPerRarity, int maxCombinations) {
        return findBestContracts(items, topK, maxItemsPerRarity, maxCombinations, null, null, null, null);
    }

    // 生成候选合同：先过滤无效素材，再按投入品质和暗金状态分桶，最后枚举每个桶里的可行组合。
    public List<TradeUpPlan> findBestContracts(List<BuffItem> items, int topK, int maxItemsPerRarity, int maxCombinations, String sortBy, String rarityFilter, String trackTypeFilter, String contractTypeFilter) {
        Map<String, List<BuffItem>> byRarity = new HashMap<String, List<BuffItem>>(8);
        for (BuffItem item : items) {
            String contractRarity = contractRarity(item);
            if (!matchesRarity(contractRarity, rarityFilter) || !matchesTrack(item, trackTypeFilter) || !matchesContractType(contractRarity, contractTypeFilter)) {
                continue;
            }
            if (!item.isTradable() || contractRarity == null || item.getCollection() == null || item.getFloatValue() == null) {
                continue;
            }
            // 纪念品可以作为汰换素材投入，其产物是普通产物，因此这里不排除投入，只在产物池排除纪念品。
            if (isUnavailableStatTrakGoldInput(item, contractRarity)) {
                continue;
            }
            if (!hasValidOutcomes(item)) {
                continue;
            }
            String bucketKey = contractBucketKey(contractRarity, item);
            List<BuffItem> rows = byRarity.get(bucketKey);
            if (rows == null) {
                rows = new ArrayList<BuffItem>();
                byRarity.put(bucketKey, rows);
            }
            rows.add(item);
        }

        List<TradeUpPlan> candidates = new ArrayList<TradeUpPlan>();
        Set<String> generatedSignatures = new HashSet<String>();
        for (Map.Entry<String, List<BuffItem>> entry : byRarity.entrySet()) {
            List<BuffItem> shortlist = entry.getValue();
            Collections.sort(shortlist, INPUT_BY_PRICE_AND_FLOAT);
            int itemLimit = Math.min(shortlist.size(), maxItemsPerRarity);
            String rarity = contractRarityFromBucketKey(entry.getKey());
            enumerate(shortlist, itemLimit, rarity, contractSize(rarity), 0, new ArrayList<BuffItem>(), candidates, generatedSignatures, maxCombinations);
        }

        Collections.sort(candidates, planComparator(sortBy));
        return uniqueTopPlans(candidates, topK);
    }

    // 相同素材组合可能由重复库存行或重复产物数据触发，这里在排序后保留唯一的 topK 方案。
    private static List<TradeUpPlan> uniqueTopPlans(List<TradeUpPlan> candidates, int topK) {
        if (topK <= 0) {
            return new ArrayList<TradeUpPlan>();
        }
        List<TradeUpPlan> uniquePlans = new ArrayList<TradeUpPlan>();
        Set<String> seenSignatures = new HashSet<String>();
        for (TradeUpPlan plan : candidates) {
            String signature = planSignature(plan);
            if (seenSignatures.add(signature)) {
                uniquePlans.add(plan);
                if (uniquePlans.size() >= topK) {
                    break;
                }
            }
        }
        return uniquePlans;
    }

    private static String planSignature(TradeUpPlan plan) {
        return comboSignature(plan.getRarity(), plan.getInputs());
    }

    private static String inputSignature(BuffItem item) {
        String goodsKey = safe(item.getGoodsId());
        if (goodsKey.isEmpty()) {
            goodsKey = safe(item.getName());
        }
        return goodsKey + '@' + safe(item.getCollection());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean matchesRarity(String rarity, String rarityFilter) {
        return rarityFilter == null || rarityFilter.trim().isEmpty() || "all".equals(rarityFilter) || rarityFilter.equals(rarity);
    }

    private static boolean matchesTrack(BuffItem item, String trackTypeFilter) {
        if (trackTypeFilter == null || trackTypeFilter.trim().isEmpty() || "all".equals(trackTypeFilter)) {
            return true;
        }
        boolean statTrak = isStatTrakItem(item);
        return "stattrak".equals(trackTypeFilter) ? statTrak : !statTrak;
    }

    private static boolean matchesContractType(String rarity, String contractTypeFilter) {
        if (contractTypeFilter == null || contractTypeFilter.trim().isEmpty() || "all".equals(contractTypeFilter)) {
            return true;
        }
        boolean goldContract = "covert".equals(rarity);
        return "gold".equals(contractTypeFilter) ? goldContract : !goldContract;
    }

    // 统一所有排序入口的兜底顺序，避免主排序字段相等时推荐结果抖动。
    private static Comparator<TradeUpPlan> planComparator(final String sortBy) {
        return (left, right) -> {
            int primary = comparePrimary(left, right, sortBy);
            if (primary != 0) {
                return primary;
            }
            int byProfit = Double.compare(right.getExpectedProfit(), left.getExpectedProfit());
            if (byProfit != 0) {
                return byProfit;
            }
            int byRoi = Double.compare(right.getRoi(), left.getRoi());
            if (byRoi != 0) {
                return byRoi;
            }
            return Double.compare(left.getInputCost(), right.getInputCost());
        };
    }

    private static int comparePrimary(TradeUpPlan left, TradeUpPlan right, String sortBy) {
        if ("expectedOutputValue".equals(sortBy)) {
            // 「期望值高到低」按 期望产出/投入 的比例排序，而非绝对金额，使便宜高效的合同也能靠前。
            return Double.compare(expectedValueRatio(right), expectedValueRatio(left));
        }
        if ("roi".equals(sortBy)) {
            return Double.compare(right.getRoi(), left.getRoi());
        }
        if ("inputCost".equals(sortBy)) {
            return Double.compare(left.getInputCost(), right.getInputCost());
        }
        if ("rarityRank".equals(sortBy)) {
            return Integer.valueOf(rarityRank(right.getRarity())).compareTo(Integer.valueOf(rarityRank(left.getRarity())));
        }
        return Double.compare(right.getExpectedProfit(), left.getExpectedProfit());
    }

    // 期望产出相对投入的比例（期望产出/投入）。投入为 0 时返回 0，避免除零。
    private static double expectedValueRatio(TradeUpPlan plan) {
        double cost = plan.getInputCost();
        return cost > 0.0d ? plan.getExpectedOutputValue() / cost : 0.0d;
    }

    private static int rarityRank(String rarity) {
        if ("consumer".equals(rarity)) {
            return 1;
        }
        if ("industrial".equals(rarity)) {
            return 2;
        }
        if ("mil-spec".equals(rarity)) {
            return 3;
        }
        if ("restricted".equals(rarity)) {
            return 4;
        }
        if ("classified".equals(rarity)) {
            return 5;
        }
        if ("covert".equals(rarity)) {
            return 6;
        }
        if ("gold".equals(rarity)) {
            return 7;
        }
        return 0;
    }

    // 深度优先枚举合同槽位：常规 10 件，隐秘到金色 5 件，并用 maxCombinations 控制爆炸式组合数。
    private void enumerate(List<BuffItem> shortlist, int itemLimit, String rarity, int contractSize, int start, List<BuffItem> current, List<TradeUpPlan> candidates, Set<String> generatedSignatures, int maxCombinations) {
        if (candidates.size() >= maxCombinations) {
            return;
        }
        int remainingSlots = contractSize - current.size();
        if (itemLimit - start < remainingSlots) {
            return;
        }
        if (current.size() == contractSize) {
            String signature = comboSignature(rarity, current);
            if (generatedSignatures.add(signature)) {
                candidates.add(evaluateContract(rarity, current));
            }
            return;
        }
        String previousInputSignature = null;
        for (int i = start; i <= itemLimit - remainingSlots; i++) {
            BuffItem item = shortlist.get(i);
            String currentInputSignature = inputSignature(item);
            if (currentInputSignature.equals(previousInputSignature)) {
                continue;
            }
            previousInputSignature = currentInputSignature;
            current.add(item);
            enumerate(shortlist, itemLimit, rarity, contractSize, i + 1, current, candidates, generatedSignatures, maxCombinations);
            current.remove(current.size() - 1);
            if (candidates.size() >= maxCombinations) {
                return;
            }
        }
    }

    // 组合签名只关心投入素材集合，不关心枚举顺序，用来去掉同一合同的排列重复。
    private static String comboSignature(String rarity, List<BuffItem> combo) {
        List<String> inputKeys = new ArrayList<String>();
        for (BuffItem item : combo) {
            inputKeys.add(inputSignature(item));
        }
        Collections.sort(inputKeys);
        StringBuilder signature = new StringBuilder(safe(rarity));
        for (String inputKey : inputKeys) {
            signature.append('|').append(inputKey);
        }
        return signature.toString();
    }

    // 素材必须能在 catalog 中找到下一档产物池，否则即使数量够也不能形成有效合同。
    private boolean hasValidOutcomes(BuffItem item) {
        String targetRarity = Rarity.next(contractRarity(item));
        return targetRarity != null && !validOutcomeFamilies(contractCollectionKey(item, targetRarity), targetRarity, isStatTrakItem(item)).isEmpty();
    }

    // 暗金红皮合金只允许进入暗金刀产物池；若某箱只有暗金手套下级，视为无法汰换。
    private boolean isUnavailableStatTrakGoldInput(BuffItem item, String contractRarity) {
        if (!"covert".equals(contractRarity) || !isStatTrakItem(item)) {
            return false;
        }
        String collection = contractCollectionKey(item, "gold");
        return statTrakGoldKnifeFamilies(collection).isEmpty();
    }

    private String contractRarity(BuffItem item) {
        if (item == null) {
            return null;
        }
        return item.getFilterRarity() != null ? item.getFilterRarity() : item.getRarity();
    }

    // 核心 EV 计算：按投入收藏品占比拆概率，按平均磨损映射产出磨损，再按对应价格档计算税后期望。
    private TradeUpPlan evaluateContract(String rarity, List<BuffItem> combo) {
        double totalCost = 0.0d;
        double totalFloat = 0.0d;
        // CS 汰换用「归一化平均」：每个输入磨损先按其自身皮肤 paint 范围归一到 [0,1] 再平均，
        // 而非原始平均。缺皮肤范围时回退用原始磨损（等价于假设 [0,1]）。
        double totalNormalized = 0.0d;
        Map<String, Integer> collectionCounts = new HashMap<String, Integer>(mapCapacity(combo.size()));
        String targetRarity = Rarity.next(rarity);
        for (BuffItem item : combo) {
            totalCost += item.getPrice();
            double floatValue = item.getFloatValue();
            totalFloat += floatValue;
            totalNormalized += normalizedInputFloat(item.getName(), floatValue);
            String collectionKey = contractCollectionKey(item, targetRarity);
            Integer count = collectionCounts.get(collectionKey);
            collectionCounts.put(collectionKey, count == null ? 1 : count + 1);
        }
        double averageFloat = totalFloat / combo.size();
        double averageNormalized = totalNormalized / combo.size();
        double expectedValue = 0.0d;
        List<Outcome> outcomes = new ArrayList<Outcome>();
        boolean statTrakContract = isStatTrakItem(combo.get(0));

        for (Map.Entry<String, Integer> entry : collectionCounts.entrySet()) {
            List<OutputFamily> families = validOutcomeFamilies(entry.getKey(), targetRarity, statTrakContract);
            if (families.isEmpty()) {
                continue;
            }
            double probability = (double) entry.getValue().intValue() / (double) combo.size() / (double) families.size();
            for (OutputFamily family : families) {
                // 用产出皮肤的权威 paint 范围（skin_float_range）；查不到回退到 catalog 各档并集。
                double[] outRange = skinRange(family.name);
                double outMin = outRange != null ? outRange[0] : family.minFloat;
                double outMax = outRange != null ? outRange[1] : family.maxFloat;
                double estimatedFloat = estimateOutputFloat(averageNormalized, outMin, outMax);
                CatalogSkin pricedSkin = family.selectVariantForFloat(estimatedFloat);
                if (pricedSkin == null) {
                    continue;
                }
                double estimatedSalePrice = adjustedOutputPrice(pricedSkin, estimatedFloat) * (1.0d - saleFeeRate);
                expectedValue += probability * estimatedSalePrice;
                // 磨损档名按估算磨损直接判定（exterior 是绝对磨损的固定函数），不受 catalog 档位是否齐全影响。
                CatalogSkin displaySkin = withDisplayWear(pricedSkin, estimatedFloat);
                outcomes.add(new Outcome(displaySkin, probability, estimatedFloat, estimatedSalePrice));
            }
        }

        Collections.sort(outcomes, OUTCOME_BY_PROBABILITY_DESC);

        double profit = expectedValue - totalCost;
        double roi = totalCost == 0.0d ? 0.0d : profit / totalCost;
        return new TradeUpPlan(rarity, round2(totalCost), round2(expectedValue), round2(profit), round4(roi), averageFloat, new ArrayList<BuffItem>(combo), outcomes);
    }

    // 产出池按收藏品/目标品质/暗金状态锁定；暗金金色合同再剔除手套，只保留刀。
    private List<OutputFamily> validOutcomeFamilies(String collection, String targetRarity, boolean statTrakContract) {
        List<OutputFamily> families = outputFamiliesByCollectionRarityAndTrack.get(outputFamilyGroupKey(collection, targetRarity, statTrakContract));
        if (families == null || families.isEmpty()) {
            return Collections.emptyList();
        }
        // 普通(非 StatTrak)隐秘合同产出非 StatTrak 的刀和手套（即 families 整体）；
        // StatTrak 隐秘合同只产出 StatTrak 刀——手套没有 StatTrak 版本，故从 StatTrak 金色池中排除。
        if (!"gold".equals(targetRarity) || !statTrakContract) {
            return families;
        }
        return statTrakGoldKnifeFamilies(collection);
    }

    private List<OutputFamily> statTrakGoldKnifeFamilies(String collection) {
        List<OutputFamily> families = statTrakGoldKnifeFamiliesByCollection.get(collection);
        if (families == null || families.isEmpty()) {
            return Collections.emptyList();
        }
        return families;
    }

    private double adjustedOutputPrice(CatalogSkin skin, double estimatedFloat) {
        double basePrice = outputPriceForFloat(skin, estimatedFloat);
        double factor = outputPriceFactor(skin);
        return basePrice * factor;
    }

    private double outputPriceForFloat(CatalogSkin skin, double estimatedFloat) {
        FloatPriceBand band = outputPriceBand(skin, estimatedFloat);
        return band == null ? skin.getPrice() : band.getPrice();
    }

    // 价格档优先按 goods_id 精确匹配，再回退到完整名称和基础皮肤名，支持二西莫夫这类细分磨损价格。
    private FloatPriceBand outputPriceBand(CatalogSkin skin, double estimatedFloat) {
        if (skin == null || outputPriceBands.isEmpty()) {
            return null;
        }
        FloatPriceBand byGoodsId = selectPriceBand(outputPriceBands.get(normalizeKey(skin.getGoodsId())), estimatedFloat);
        if (byGoodsId != null) {
            return byGoodsId;
        }
        FloatPriceBand byName = selectPriceBand(outputPriceBands.get(normalizeKey(skin.getName())), estimatedFloat);
        if (byName != null) {
            return byName;
        }
        return selectPriceBand(outputPriceBands.get(normalizeKey(baseSkinName(skin.getName()))), estimatedFloat);
    }

    private static FloatPriceBand selectPriceBand(List<FloatPriceBand> bands, double estimatedFloat) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        for (FloatPriceBand band : bands) {
            boolean upperInclusive = isLastBand(bands, band);
            if (estimatedFloat >= band.getMinFloat() && (estimatedFloat < band.getMaxFloat() || (upperInclusive && estimatedFloat <= band.getMaxFloat()))) {
                return band;
            }
        }
        return null;
    }

    private static boolean isLastBand(List<FloatPriceBand> bands, FloatPriceBand band) {
        return !bands.isEmpty() && bands.get(bands.size() - 1) == band;
    }

    private double outputPriceFactor(CatalogSkin skin) {
        if (skin == null || outputPriceFactors.isEmpty()) {
            return 1.0d;
        }
        Double byGoodsId = outputPriceFactors.get(normalizeKey(skin.getGoodsId()));
        if (byGoodsId != null) {
            return byGoodsId.doubleValue();
        }
        Double byName = outputPriceFactors.get(normalizeKey(skin.getName()));
        if (byName != null) {
            return byName.doubleValue();
        }
        Double byBaseName = outputPriceFactors.get(normalizeKey(baseSkinName(skin.getName())));
        return byBaseName == null ? 1.0d : byBaseName.doubleValue();
    }

    private static boolean isValidPriceBand(FloatPriceBand band) {
        return band != null
            && band.getPrice() > 0.0d
            && band.getMaxFloat() > band.getMinFloat()
            && band.getMinFloat() >= 0.0d
            && band.getMaxFloat() <= 1.0d;
    }

    // 产出磨损使用 CS 汰换线性公式，并限制在该皮肤自身 Min/Max Float 区间内。
    // 复制产出皮肤，仅把磨损后缀改成与估算磨损匹配的档位（价格等其它字段不变）。
    private static CatalogSkin withDisplayWear(CatalogSkin skin, double estimatedFloat) {
        CatalogSkin copy = new CatalogSkin();
        copy.setName(com.qyaaaa.cstaihuan.util.WearSuffix.withZhWearForFloat(skin.getName(), estimatedFloat));
        copy.setGoodsId(skin.getGoodsId());
        copy.setCollection(skin.getCollection());
        copy.setRarity(skin.getRarity());
        copy.setCategoryKey(skin.getCategoryKey());
        copy.setQualityLabel(skin.getQualityLabel());
        copy.setMinFloat(skin.getMinFloat());
        copy.setMaxFloat(skin.getMaxFloat());
        copy.setPrice(skin.getPrice());
        copy.setImageUrl(skin.getImageUrl());
        return copy;
    }

    private double estimateOutputFloat(double averageInputFloat, double minFloat, double maxFloat) {
        double value = minFloat + averageInputFloat * (maxFloat - minFloat);
        if (value < minFloat) {
            value = minFloat;
        }
        if (value > maxFloat) {
            value = maxFloat;
        }
        // 不做四舍五入，保留全精度磨损，和 BUFF 一致（前端展示完整值）。
        return value;
    }

    private static String outputFamilyGroupKey(String collection, String rarity, boolean statTrak) {
        return new StringBuilder(32)
            .append(collection)
            .append(KEY_SEPARATOR)
            .append(rarity)
            .append(KEY_SEPARATOR)
            .append(statTrak)
            .toString();
    }

    private static String outputFamilyKey(CatalogSkin skin) {
        // 用强匹配键做皮肤名分量：去空格/武器别名/StatTrak标记，避免同一皮肤因命名差异(如「USP 消音版」
        // vs「USP消音版」)被拆成两个产物族、产出重复产物。StatTrak 与普通仍由下面的 statTrak 标志区分。
        return new StringBuilder(outputFamilyGroupKey(skin.getCollection(), skin.getRarity(), isStatTrakName(skin.getName())))
            .append(KEY_SEPARATOR)
            .append(com.qyaaaa.cstaihuan.util.WearSuffix.toRangeMatchKey(skin.getName()))
            .toString();
    }

    private static String contractBucketKey(String rarity, BuffItem item) {
        return new StringBuilder(16)
            .append(rarity)
            .append(KEY_SEPARATOR)
            .append(isStatTrakItem(item))
            .toString();
    }

    private static String contractRarityFromBucketKey(String bucketKey) {
        int separator = bucketKey.indexOf(KEY_SEPARATOR);
        return separator < 0 ? bucketKey : bucketKey.substring(0, separator);
    }

    private static int contractSize(String rarity) {
        return "covert".equals(rarity) ? 5 : 10;
    }

    // 金色产物按箱子确定刀/手套池，普通汰换仍按收藏品确定下一档武器池。
    private static String contractCollectionKey(BuffItem item, String targetRarity) {
        if (item == null) {
            return null;
        }
        if ("gold".equals(targetRarity)) {
            String weaponcase = nestedString(item.getRaw(), "tags", "weaponcase", "localized_name");
            if (weaponcase != null && !weaponcase.trim().isEmpty()) {
                return weaponcase.trim();
            }
        }
        return item.getCollection();
    }

    @SuppressWarnings("unchecked")
    private static String nestedString(Map<String, Object> raw, String first, String second, String third) {
        if (raw == null) {
            return null;
        }
        Object firstValue = raw.get(first);
        if (!(firstValue instanceof Map)) {
            return null;
        }
        Object secondValue = ((Map<String, Object>) firstValue).get(second);
        if (!(secondValue instanceof Map)) {
            return null;
        }
        Object value = ((Map<String, Object>) secondValue).get(third);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean isStatTrakItem(BuffItem item) {
        return item != null && isStatTrakName(item.getName());
    }

    private static boolean isStatTrakName(String name) {
        return name != null && name.toLowerCase().contains("stattrak");
    }

    // 纪念品按名称识别（BUFF 中文名含“纪念品”，英文 market_hash_name 含 souvenir）。
    private static boolean isSouvenirName(String name) {
        if (name == null) {
            return false;
        }
        return name.contains("纪念品") || name.toLowerCase().contains("souvenir");
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    // 去掉中英文磨损后缀，把同一皮肤的五个磨损档合并成一个产物 family。
    private static String baseSkinName(String name) {
        return com.qyaaaa.cstaihuan.util.WearSuffix.stripWearSuffix(name);
    }

    private static int mapCapacity(int expectedSize) {
        return Math.max(4, (int) (expectedSize / 0.75f) + 1);
    }

    private static final class OutputFamily {
        private final String collection;
        private final String rarity;
        private final boolean statTrak;
        private final String categoryKey;
        private final String name;
        private final boolean gloveFamily;
        private final List<CatalogSkin> variants = new ArrayList<CatalogSkin>();
        private double minFloat = Double.MAX_VALUE;
        private double maxFloat = 0.0d;

        private OutputFamily(String collection, String rarity, boolean statTrak, String categoryKey, String name) {
            this.collection = collection;
            this.rarity = rarity;
            this.statTrak = statTrak;
            this.categoryKey = categoryKey;
            this.name = name;
            this.gloveFamily = isGloveFamily(categoryKey, name);
        }

        private void addVariant(CatalogSkin skin) {
            variants.add(skin);
            minFloat = Math.min(minFloat, skin.getMinFloat());
            maxFloat = Math.max(maxFloat, skin.getMaxFloat());
        }

        // 根据计算出的精确磨损选择对应价格档位；没有完全落入区间时用最近档位兜底。
        private CatalogSkin selectVariantForFloat(double floatValue) {
            if (variants.isEmpty()) {
                return null;
            }
            // 根据计算出的磨损值所属 CS 标准外观档，匹配各变体名称后缀。BUFF 单外观档 paintwear_range
            // 有时会给出整皮范围，因此这里不能信任变体 min/max。
            int targetTier = com.qyaaaa.cstaihuan.util.WearSuffix.wearTierForFloat(floatValue);
            CatalogSkin tierFallback = null;
            int bestTierDistance = Integer.MAX_VALUE;
            CatalogSkin rangeFallback = variants.get(0);
            double bestRangeDistance = Double.MAX_VALUE;
            for (CatalogSkin variant : variants) {
                int variantTier = com.qyaaaa.cstaihuan.util.WearSuffix.wearTierOfName(variant.getName());
                if (variantTier == targetTier) {
                    return variant;
                }
                if (variantTier >= 0) {
                    int tierDistance = Math.abs(variantTier - targetTier);
                    if (tierDistance < bestTierDistance) {
                        bestTierDistance = tierDistance;
                        tierFallback = variant;
                    }
                }
                double rangeDistance = Math.min(Math.abs(floatValue - variant.getMinFloat()), Math.abs(floatValue - variant.getMaxFloat()));
                if (rangeDistance < bestRangeDistance) {
                    bestRangeDistance = rangeDistance;
                    rangeFallback = variant;
                }
            }
            // 优先使用名称档位最接近的变体；只有所有变体都没有可识别磨损后缀时，才回退到范围最近的变体。
            return tierFallback != null ? tierFallback : rangeFallback;
        }

        // BUFF/CS 数据里手套分类和名称可能中英文混杂，所以同时检查分类 key 和基础名称。
        private boolean isGloveFamily() {
            return gloveFamily;
        }

        private static boolean isGloveFamily(String categoryKey, String name) {
            String text = normalizeKey(categoryKey) + ' ' + normalizeKey(name);
            for (String keyword : GLOVE_KEYWORDS) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private static double round6(double value) {
        return Math.round(value * 1000000.0d) / 1000000.0d;
    }
}
