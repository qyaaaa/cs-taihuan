package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.Outcome;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TradeUpOptimizer {
    private final List<CatalogSkin> catalog;
    private final double saleFeeRate;
    private final Map<String, CatalogSkin> catalogByName = new HashMap<String, CatalogSkin>();
    private final Map<String, List<CatalogSkin>> byCollectionAndRarity = new HashMap<String, List<CatalogSkin>>();

    public TradeUpOptimizer(List<CatalogSkin> catalog, double saleFeeRate) {
        this.catalog = catalog;
        this.saleFeeRate = saleFeeRate;
        for (CatalogSkin skin : catalog) {
            catalogByName.put(skin.getName(), skin);
            String key = key(skin.getCollection(), skin.getRarity());
            List<CatalogSkin> rows = byCollectionAndRarity.get(key);
            if (rows == null) {
                rows = new ArrayList<CatalogSkin>();
                byCollectionAndRarity.put(key, rows);
            }
            rows.add(skin);
        }
    }

    public List<BuffItem> enrichInventory(List<BuffItem> items) {
        List<BuffItem> enriched = new ArrayList<BuffItem>();
        for (BuffItem item : items) {
            CatalogSkin skin = catalogByName.get(item.getName());
            if (skin != null) {
                enriched.add(item.withCatalog(skin));
            } else if (item.getCollection() != null && item.getRarity() != null) {
                enriched.add(item);
            }
        }
        return enriched;
    }

    public List<TradeUpPlan> findBestContracts(List<BuffItem> items, int topK, int maxItemsPerRarity, int maxCombinations) {
        Map<String, List<BuffItem>> byRarity = new HashMap<String, List<BuffItem>>();
        for (BuffItem item : items) {
            if (!item.isTradable() || item.getRarity() == null || item.getCollection() == null || item.getFloatValue() == null) {
                continue;
            }
            if (!hasValidOutcomes(item)) {
                continue;
            }
            List<BuffItem> rows = byRarity.get(item.getRarity());
            if (rows == null) {
                rows = new ArrayList<BuffItem>();
                byRarity.put(item.getRarity(), rows);
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
            enumerate(shortlist, entry.getKey(), 0, new ArrayList<BuffItem>(), candidates, maxCombinations);
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

    private void enumerate(List<BuffItem> shortlist, String rarity, int start, List<BuffItem> current, List<TradeUpPlan> candidates, int maxCombinations) {
        if (candidates.size() >= maxCombinations) {
            return;
        }
        if (current.size() == 10) {
            candidates.add(evaluateContract(rarity, current));
            return;
        }
        for (int i = start; i <= shortlist.size() - (10 - current.size()); i++) {
            current.add(shortlist.get(i));
            enumerate(shortlist, rarity, i + 1, current, candidates, maxCombinations);
            current.remove(current.size() - 1);
            if (candidates.size() >= maxCombinations) {
                return;
            }
        }
    }

    private boolean hasValidOutcomes(BuffItem item) {
        String targetRarity = Rarity.next(item.getRarity());
        return targetRarity != null && byCollectionAndRarity.containsKey(key(item.getCollection(), targetRarity));
    }

    private TradeUpPlan evaluateContract(String rarity, List<BuffItem> combo) {
        double totalCost = 0.0d;
        double totalFloat = 0.0d;
        Map<String, Integer> collectionCounts = new HashMap<String, Integer>();
        for (BuffItem item : combo) {
            totalCost += item.getPrice();
            totalFloat += item.getFloatValue();
            Integer count = collectionCounts.get(item.getCollection());
            collectionCounts.put(item.getCollection(), count == null ? 1 : count + 1);
        }
        double averageFloat = totalFloat / combo.size();
        double expectedValue = 0.0d;
        List<Outcome> outcomes = new ArrayList<Outcome>();
        String targetRarity = Rarity.next(rarity);

        for (Map.Entry<String, Integer> entry : collectionCounts.entrySet()) {
            List<CatalogSkin> rows = byCollectionAndRarity.get(key(entry.getKey(), targetRarity));
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            double probability = (double) entry.getValue().intValue() / (double) combo.size() / (double) rows.size();
            for (CatalogSkin skin : rows) {
                double estimatedFloat = estimateOutputFloat(averageFloat, skin);
                double estimatedSalePrice = skin.getPrice() * (1.0d - saleFeeRate);
                expectedValue += probability * estimatedSalePrice;
                outcomes.add(new Outcome(skin, probability, estimatedFloat, estimatedSalePrice));
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

    private double estimateOutputFloat(double averageInputFloat, CatalogSkin skin) {
        double value = skin.getMinFloat() + averageInputFloat * (skin.getMaxFloat() - skin.getMinFloat());
        if (value < skin.getMinFloat()) {
            value = skin.getMinFloat();
        }
        if (value > skin.getMaxFloat()) {
            value = skin.getMaxFloat();
        }
        return round6(value);
    }

    private static String key(String collection, String rarity) {
        return collection + "||" + rarity;
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

