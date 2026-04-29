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
    private final double saleFeeRate;
    private final Map<String, Double> outputPriceFactors = new HashMap<String, Double>();
    private final Map<String, List<FloatPriceBand>> outputPriceBands = new HashMap<String, List<FloatPriceBand>>();
    private final Map<String, CatalogSkin> catalogByName = new HashMap<String, CatalogSkin>();
    private final Map<String, List<OutputFamily>> outputFamiliesByCollectionRarityAndTrack = new HashMap<String, List<OutputFamily>>();

    public TradeUpOptimizer(List<CatalogSkin> catalog, double saleFeeRate) {
        this(catalog, saleFeeRate, null);
    }

    public TradeUpOptimizer(List<CatalogSkin> catalog, double saleFeeRate, Map<String, Double> outputPriceFactors) {
        this(catalog, saleFeeRate, outputPriceFactors, null);
    }

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
                    Collections.sort(bands, new Comparator<FloatPriceBand>() {
                        public int compare(FloatPriceBand left, FloatPriceBand right) {
                            return Double.compare(left.getMinFloat(), right.getMinFloat());
                        }
                    });
                    this.outputPriceBands.put(normalizeKey(entry.getKey()), bands);
                }
            }
        }
        Map<String, OutputFamily> outputFamilyByIdentity = new LinkedHashMap<String, OutputFamily>();
        for (CatalogSkin skin : catalog) {
            catalogByName.put(skin.getName(), skin);

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
        }
    }

    public List<BuffItem> enrichInventory(List<BuffItem> items) {
        List<BuffItem> enriched = new ArrayList<BuffItem>();
        for (BuffItem item : items) {
            CatalogSkin skin = catalogByName.get(item.getName());
            if (skin != null) {
                enriched.add(item.withCatalog(skin));
            } else if (item.getCollection() != null && item.getFilterRarity() != null) {
                enriched.add(item);
            }
        }
        return enriched;
    }

    public List<TradeUpPlan> findBestContracts(List<BuffItem> items, int topK, int maxItemsPerRarity, int maxCombinations) {
        return findBestContracts(items, topK, maxItemsPerRarity, maxCombinations, null, null, null, null);
    }

    public List<TradeUpPlan> findBestContracts(List<BuffItem> items, int topK, int maxItemsPerRarity, int maxCombinations, String sortBy, String rarityFilter, String trackTypeFilter, String contractTypeFilter) {
        Map<String, List<BuffItem>> byRarity = new HashMap<String, List<BuffItem>>();
        for (BuffItem item : items) {
            String contractRarity = contractRarity(item);
            if (!matchesRarity(contractRarity, rarityFilter) || !matchesTrack(item, trackTypeFilter) || !matchesContractType(contractRarity, contractTypeFilter)) {
                continue;
            }
            if (!item.isTradable() || contractRarity == null || item.getCollection() == null || item.getFloatValue() == null) {
                continue;
            }
            if (isUnavailableStatTrakGoldInput(item, contractRarity)) {
                continue;
            }
            if (!hasValidOutcomes(item)) {
                continue;
            }
            List<BuffItem> rows = byRarity.get(contractBucketKey(contractRarity, item));
            if (rows == null) {
                rows = new ArrayList<BuffItem>();
                byRarity.put(contractBucketKey(contractRarity, item), rows);
            }
            rows.add(item);
        }

        List<TradeUpPlan> candidates = new ArrayList<TradeUpPlan>();
        Set<String> generatedSignatures = new HashSet<String>();
        for (Map.Entry<String, List<BuffItem>> entry : byRarity.entrySet()) {
            List<BuffItem> shortlist = new ArrayList<BuffItem>(entry.getValue());
            Collections.sort(shortlist, new Comparator<BuffItem>() {
                public int compare(BuffItem left, BuffItem right) {
                    int byPrice = Double.compare(left.getPrice(), right.getPrice());
                    if (byPrice != 0) {
                        return byPrice;
                    }
                    return Double.compare(left.getFloatValue(), right.getFloatValue());
                }
            });
            if (shortlist.size() > maxItemsPerRarity) {
                shortlist = new ArrayList<BuffItem>(shortlist.subList(0, maxItemsPerRarity));
            }
            String rarity = contractRarityFromBucketKey(entry.getKey());
            enumerate(shortlist, rarity, contractSize(rarity), 0, new ArrayList<BuffItem>(), candidates, generatedSignatures, maxCombinations);
        }

        Collections.sort(candidates, planComparator(sortBy));
        return uniqueTopPlans(candidates, topK);
    }

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

    private static Comparator<TradeUpPlan> planComparator(final String sortBy) {
        return new Comparator<TradeUpPlan>() {
            public int compare(TradeUpPlan left, TradeUpPlan right) {
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
            }
        };
    }

    private static int comparePrimary(TradeUpPlan left, TradeUpPlan right, String sortBy) {
        if ("expectedOutputValue".equals(sortBy)) {
            return Double.compare(right.getExpectedOutputValue(), left.getExpectedOutputValue());
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

    private void enumerate(List<BuffItem> shortlist, String rarity, int contractSize, int start, List<BuffItem> current, List<TradeUpPlan> candidates, Set<String> generatedSignatures, int maxCombinations) {
        if (candidates.size() >= maxCombinations) {
            return;
        }
        if (current.size() == contractSize) {
            String signature = comboSignature(rarity, current);
            if (generatedSignatures.add(signature)) {
                candidates.add(evaluateContract(rarity, current));
            }
            return;
        }
        for (int i = start; i <= shortlist.size() - (contractSize - current.size()); i++) {
            current.add(shortlist.get(i));
            enumerate(shortlist, rarity, contractSize, i + 1, current, candidates, generatedSignatures, maxCombinations);
            current.remove(current.size() - 1);
            if (candidates.size() >= maxCombinations) {
                return;
            }
        }
    }

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

    private boolean hasValidOutcomes(BuffItem item) {
        String targetRarity = Rarity.next(contractRarity(item));
        return targetRarity != null && !validOutcomeFamilies(contractCollectionKey(item, targetRarity), targetRarity, isStatTrakItem(item)).isEmpty();
    }

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

    private TradeUpPlan evaluateContract(String rarity, List<BuffItem> combo) {
        double totalCost = 0.0d;
        double totalFloat = 0.0d;
        Map<String, Integer> collectionCounts = new HashMap<String, Integer>();
        String targetRarity = Rarity.next(rarity);
        for (BuffItem item : combo) {
            totalCost += item.getPrice();
            totalFloat += item.getFloatValue();
            String collectionKey = contractCollectionKey(item, targetRarity);
            Integer count = collectionCounts.get(collectionKey);
            collectionCounts.put(collectionKey, count == null ? 1 : count + 1);
        }
        double averageFloat = totalFloat / combo.size();
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
                double estimatedFloat = estimateOutputFloat(averageFloat, family.minFloat, family.maxFloat);
                CatalogSkin pricedSkin = family.selectVariantForFloat(estimatedFloat);
                if (pricedSkin == null) {
                    continue;
                }
                double estimatedSalePrice = adjustedOutputPrice(pricedSkin, estimatedFloat) * (1.0d - saleFeeRate);
                expectedValue += probability * estimatedSalePrice;
                outcomes.add(new Outcome(pricedSkin, probability, estimatedFloat, estimatedSalePrice));
            }
        }

        Collections.sort(outcomes, new Comparator<Outcome>() {
            public int compare(Outcome left, Outcome right) {
                return Double.compare(right.getProbability(), left.getProbability());
            }
        });

        double profit = expectedValue - totalCost;
        double roi = totalCost == 0.0d ? 0.0d : profit / totalCost;
        return new TradeUpPlan(rarity, round2(totalCost), round2(expectedValue), round2(profit), round4(roi), round6(averageFloat), new ArrayList<BuffItem>(combo), outcomes);
    }

    private List<OutputFamily> validOutcomeFamilies(String collection, String targetRarity, boolean statTrakContract) {
        List<OutputFamily> families = outputFamiliesByCollectionRarityAndTrack.get(outputFamilyGroupKey(collection, targetRarity, statTrakContract));
        if (families == null || families.isEmpty()) {
            return Collections.emptyList();
        }
        if (!"gold".equals(targetRarity) || !statTrakContract) {
            return families;
        }
        return statTrakGoldKnifeFamilies(collection);
    }

    private List<OutputFamily> statTrakGoldKnifeFamilies(String collection) {
        List<OutputFamily> families = outputFamiliesByCollectionRarityAndTrack.get(outputFamilyGroupKey(collection, "gold", true));
        if (families == null || families.isEmpty()) {
            return Collections.emptyList();
        }
        List<OutputFamily> filtered = new ArrayList<OutputFamily>();
        for (OutputFamily family : families) {
            if (!family.isGloveFamily()) {
                filtered.add(family);
            }
        }
        return filtered;
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

    private double estimateOutputFloat(double averageInputFloat, double minFloat, double maxFloat) {
        double value = minFloat + averageInputFloat * (maxFloat - minFloat);
        if (value < minFloat) {
            value = minFloat;
        }
        if (value > maxFloat) {
            value = maxFloat;
        }
        return round6(value);
    }

    private static String outputFamilyGroupKey(String collection, String rarity, boolean statTrak) {
        return collection + "||" + rarity + "||" + statTrak;
    }

    private static String outputFamilyKey(CatalogSkin skin) {
        return outputFamilyGroupKey(skin.getCollection(), skin.getRarity(), isStatTrakName(skin.getName())) + "||" + baseSkinName(skin.getName());
    }

    private static String contractBucketKey(String rarity, BuffItem item) {
        return rarity + "||" + isStatTrakItem(item);
    }

    private static String contractRarityFromBucketKey(String bucketKey) {
        int separator = bucketKey.indexOf("||");
        return separator < 0 ? bucketKey : bucketKey.substring(0, separator);
    }

    private static int contractSize(String rarity) {
        return "covert".equals(rarity) ? 5 : 10;
    }

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

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String baseSkinName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        String[] wearSuffixes = new String[] {
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
        for (String suffix : wearSuffixes) {
            if (trimmed.endsWith(suffix)) {
                return trimmed.substring(0, trimmed.length() - suffix.length()).trim();
            }
        }
        return trimmed;
    }

    private static final class OutputFamily {
        private final String collection;
        private final String rarity;
        private final boolean statTrak;
        private final String categoryKey;
        private final String name;
        private final List<CatalogSkin> variants = new ArrayList<CatalogSkin>();
        private double minFloat = Double.MAX_VALUE;
        private double maxFloat = 0.0d;

        private OutputFamily(String collection, String rarity, boolean statTrak, String categoryKey, String name) {
            this.collection = collection;
            this.rarity = rarity;
            this.statTrak = statTrak;
            this.categoryKey = categoryKey;
            this.name = name;
        }

        private void addVariant(CatalogSkin skin) {
            variants.add(skin);
            minFloat = Math.min(minFloat, skin.getMinFloat());
            maxFloat = Math.max(maxFloat, skin.getMaxFloat());
        }

        private CatalogSkin selectVariantForFloat(double floatValue) {
            if (variants.isEmpty()) {
                return null;
            }
            CatalogSkin fallback = variants.get(0);
            double fallbackDistance = Double.MAX_VALUE;
            for (CatalogSkin variant : variants) {
                if (floatValue >= variant.getMinFloat() && floatValue <= variant.getMaxFloat()) {
                    return variant;
                }
                double distance = Math.min(Math.abs(floatValue - variant.getMinFloat()), Math.abs(floatValue - variant.getMaxFloat()));
                if (distance < fallbackDistance) {
                    fallbackDistance = distance;
                    fallback = variant;
                }
            }
            return fallback;
        }

        private boolean isGloveFamily() {
            String text = normalizeKey(categoryKey) + " " + normalizeKey(name);
            return text.contains("glove")
                || text.contains("hand wrap")
                || text.contains("手套")
                || text.contains("裹手")
                || text.contains("驾驶")
                || text.contains("运动")
                || text.contains("专业")
                || text.contains("摩托")
                || text.contains("九头蛇")
                || text.contains("狂牙")
                || text.contains("血猎");
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
