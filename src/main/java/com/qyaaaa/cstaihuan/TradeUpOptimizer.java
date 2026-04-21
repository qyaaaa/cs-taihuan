package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.Outcome;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TradeUpOptimizer {
    private final double saleFeeRate;
    private final Map<String, CatalogSkin> catalogByName = new HashMap<String, CatalogSkin>();
    private final Map<String, List<OutputFamily>> outputFamiliesByCollectionRarityAndTrack = new HashMap<String, List<OutputFamily>>();

    public TradeUpOptimizer(List<CatalogSkin> catalog, double saleFeeRate) {
        this.saleFeeRate = saleFeeRate;
        Map<String, OutputFamily> outputFamilyByIdentity = new LinkedHashMap<String, OutputFamily>();
        for (CatalogSkin skin : catalog) {
            catalogByName.put(skin.getName(), skin);

            String familyKey = outputFamilyKey(skin);
            OutputFamily family = outputFamilyByIdentity.get(familyKey);
            if (family == null) {
                family = new OutputFamily(skin.getCollection(), skin.getRarity(), isStatTrakName(skin.getName()));
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
        Map<String, List<BuffItem>> byRarity = new HashMap<String, List<BuffItem>>();
        for (BuffItem item : items) {
            String contractRarity = contractRarity(item);
            if (!item.isTradable() || contractRarity == null || item.getCollection() == null || item.getFloatValue() == null) {
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
            enumerate(shortlist, rarity, contractSize(rarity), 0, new ArrayList<BuffItem>(), candidates, maxCombinations);
        }

        Collections.sort(candidates, new Comparator<TradeUpPlan>() {
            public int compare(TradeUpPlan left, TradeUpPlan right) {
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
        });
        if (candidates.size() > topK) {
            return new ArrayList<TradeUpPlan>(candidates.subList(0, topK));
        }
        return candidates;
    }

    private void enumerate(List<BuffItem> shortlist, String rarity, int contractSize, int start, List<BuffItem> current, List<TradeUpPlan> candidates, int maxCombinations) {
        if (candidates.size() >= maxCombinations) {
            return;
        }
        if (current.size() == contractSize) {
            candidates.add(evaluateContract(rarity, current));
            return;
        }
        for (int i = start; i <= shortlist.size() - (contractSize - current.size()); i++) {
            current.add(shortlist.get(i));
            enumerate(shortlist, rarity, contractSize, i + 1, current, candidates, maxCombinations);
            current.remove(current.size() - 1);
            if (candidates.size() >= maxCombinations) {
                return;
            }
        }
    }

    private boolean hasValidOutcomes(BuffItem item) {
        String targetRarity = Rarity.next(contractRarity(item));
        return targetRarity != null
            && outputFamiliesByCollectionRarityAndTrack.containsKey(outputFamilyGroupKey(contractCollectionKey(item, targetRarity), targetRarity, isStatTrakItem(item)));
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
            List<OutputFamily> families = outputFamiliesByCollectionRarityAndTrack.get(outputFamilyGroupKey(entry.getKey(), targetRarity, statTrakContract));
            if (families == null || families.isEmpty()) {
                continue;
            }
            double probability = (double) entry.getValue().intValue() / (double) combo.size() / (double) families.size();
            for (OutputFamily family : families) {
                double estimatedFloat = estimateOutputFloat(averageFloat, family.minFloat, family.maxFloat);
                CatalogSkin pricedSkin = family.selectVariantForFloat(estimatedFloat);
                if (pricedSkin == null) {
                    continue;
                }
                double estimatedSalePrice = pricedSkin.getPrice() * (1.0d - saleFeeRate);
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
        private final List<CatalogSkin> variants = new ArrayList<CatalogSkin>();
        private double minFloat = Double.MAX_VALUE;
        private double maxFloat = 0.0d;

        private OutputFamily(String collection, String rarity, boolean statTrak) {
            this.collection = collection;
            this.rarity = rarity;
            this.statTrak = statTrak;
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
