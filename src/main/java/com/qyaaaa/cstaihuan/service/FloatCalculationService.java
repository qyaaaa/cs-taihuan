package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.dto.FloatCalculationRequest;
import com.qyaaaa.cstaihuan.dto.FloatCalculationResponse;
import com.qyaaaa.cstaihuan.dto.FloatTargetOption;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.SkinFloatRange;
import com.qyaaaa.cstaihuan.util.SkinRarity;
import com.qyaaaa.cstaihuan.util.WearSuffix;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FloatCalculationService {
    private static final double EPSILON = 0.000000001d;
    private static final Locale FORMAT_LOCALE = Locale.ROOT;

    private final CatalogService catalogService;
    private final SkinFloatRangeService skinFloatRangeService;

    public FloatCalculationService(CatalogService catalogService, SkinFloatRangeService skinFloatRangeService) {
        this.catalogService = catalogService;
        this.skinFloatRangeService = skinFloatRangeService;
    }

    /** Field-scoped target search merging BUFF catalog (preferred) with the float-range library. */
    public List<FloatTargetOption> searchTargets(String collection, String name, String rarity, int limit) {
        Map<String, FloatTargetOption> byKey = new LinkedHashMap<String, FloatTargetOption>();
        for (CatalogSkin skin : catalogService.searchTargets(collection, name, rarity, limit)) {
            FloatTargetOption option = toOption(skin);
            if (isDefaultRange(option.getMinFloat(), option.getMaxFloat())) {
                Optional<SkinFloatRange> r = skinFloatRangeService.findByName(skin.getName());
                if (r.isPresent()) {
                    option.setMinFloat(r.get().getMinFloat());
                    option.setMaxFloat(r.get().getMaxFloat());
                }
            }
            byKey.put(WearSuffix.toMatchKey(skin.getName()), option);
        }
        for (SkinFloatRange row : skinFloatRangeService.search(collection, name, rarity, limit)) {
            String key = StringUtils.hasText(row.getBaseNameEn()) ? row.getBaseNameEn() : WearSuffix.toMatchKey(row.getNameEn());
            if (!byKey.containsKey(key)) {
                byKey.put(key, toOption(row));
            }
        }
        List<FloatTargetOption> all = new ArrayList<FloatTargetOption>(byKey.values());
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        return all.size() > normalizedLimit ? new ArrayList<FloatTargetOption>(all.subList(0, normalizedLimit)) : all;
    }

    /** Distinct collections (zh/en) for the search dropdown. */
    public List<Map<String, String>> listCollections() {
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        for (String[] row : skinFloatRangeService.listCollections()) {
            Map<String, String> item = new java.util.HashMap<String, String>();
            item.put("zh", row[0]);
            item.put("en", row[1]);
            result.add(item);
        }
        return result;
    }

    /**
     * 反推目标产物磨损需要的素材平均磨损，并在用户锁定部分素材后计算剩余槽位的平均磨损约束。
     */
    public FloatCalculationResponse calculate(FloatCalculationRequest request) {
        ResolvedTarget target = resolveTarget(request);
        int contractSize = normalizeContractSize(request.getContractSize(), target.rarity);
        List<Double> lockedInputFloats = request.getLockedInputFloats();
        if (lockedInputFloats != null && lockedInputFloats.size() > contractSize) {
            throw new IllegalArgumentException(ErrorMessages.LOCKED_INPUT_FLOAT_SIZE);
        }

        double targetMinFloat = target.minFloat;
        double targetMaxFloat = target.maxFloat;
        double targetRange = targetMaxFloat - targetMinFloat;
        if (targetRange <= 0d) {
            throw new IllegalArgumentException(ErrorMessages.targetFloatOutOfRange(targetMinFloat, targetMaxFloat));
        }

        double targetFloat = request.getTargetFloat().doubleValue();
        if (targetFloat < targetMinFloat - EPSILON || targetFloat > targetMaxFloat + EPSILON) {
            throw new IllegalArgumentException(ErrorMessages.targetFloatOutOfRange(targetMinFloat, targetMaxFloat));
        }

        double requiredAverage = (targetFloat - targetMinFloat) / targetRange;
        double requiredTotal = requiredAverage * contractSize;
        LockedFloats locked = sumLockedFloats(lockedInputFloats);
        int remainingSlots = contractSize - locked.getCount();
        double remainingTotal = requiredTotal - locked.getSum();
        boolean reachable = requiredAverage >= -EPSILON
            && requiredAverage <= 1d + EPSILON
            && remainingTotal >= -EPSILON
            && remainingTotal <= remainingSlots + EPSILON;

        FloatCalculationResponse response = new FloatCalculationResponse();
        response.setTargetGoodsId(target.goodsId);
        response.setTargetName(target.name);
        response.setTargetCollection(target.collection);
        response.setTargetRarity(target.rarity);
        response.setTargetQualityLabel(target.qualityLabel);
        response.setTargetFloat(targetFloat);
        response.setTargetMinFloat(targetMinFloat);
        response.setTargetMaxFloat(targetMaxFloat);
        response.setContractSize(contractSize);
        response.setRequiredAverageInputFloat(clampTiny(requiredAverage));
        response.setRequiredTotalInputFloat(clampTiny(requiredTotal));
        response.setLockedFloatSum(clampTiny(locked.getSum()));
        response.setLockedSlotCount(locked.getCount());
        response.setRemainingSlotCount(remainingSlots);
        if (remainingSlots > 0) {
            response.setRequiredRemainingAverageFloat(clampTiny(remainingTotal / remainingSlots));
            if (reachable) {
                response.setAllowedRemainingMinFloat(clampTiny(boundUnit(remainingTotal - (remainingSlots - 1))));
                response.setAllowedRemainingMaxFloat(clampTiny(boundUnit(remainingTotal)));
            }
        }
        response.setReachable(reachable);
        response.setMessage(buildMessage(reachable, remainingSlots, response.getRequiredRemainingAverageFloat()));
        return response;
    }

    private int normalizeContractSize(Integer contractSize, String targetRarity) {
        int normalized = contractSize == null ? 10 : contractSize.intValue();
        if (normalized != 5 && normalized != 10) {
            throw new IllegalArgumentException(ErrorMessages.CONTRACT_SIZE_UNSUPPORTED);
        }
        if (normalized != expectedContractSize(targetRarity)) {
            throw new IllegalArgumentException(ErrorMessages.CONTRACT_SIZE_TARGET_MISMATCH);
        }
        return normalized;
    }

    private int expectedContractSize(String targetRarity) {
        return "gold".equals(targetRarity) ? 5 : 10;
    }

    private LockedFloats sumLockedFloats(List<Double> values) {
        if (values == null) {
            return LockedFloats.empty();
        }
        double sum = 0.0d;
        int count = 0;
        for (Double value : values) {
            if (value == null) {
                continue;
            }
            sum += value.doubleValue();
            count++;
        }
        return new LockedFloats(sum, count);
    }

    private String buildMessage(boolean reachable, int remainingSlots, Double requiredRemainingAverage) {
        if (!reachable) {
            return "当前已锁定材料无法达成目标磨损，请降低已锁定磨损或调整目标。";
        }
        if (remainingSlots == 0) {
            return "所有材料已锁定，当前总磨损可以达成目标。";
        }
        return "剩余 " + remainingSlots + " 件材料平均磨损需要约为 " + formatFloat(requiredRemainingAverage) + "。";
    }

    private String formatFloat(Double value) {
        if (value == null) {
            return "--";
        }
        return String.format(FORMAT_LOCALE, "%.8f", value);
    }

    private double clampTiny(double value) {
        if (Math.abs(value) < EPSILON) {
            return 0d;
        }
        if (Math.abs(value - 1d) < EPSILON) {
            return 1d;
        }
        return value;
    }

    private double boundUnit(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private FloatTargetOption toOption(CatalogSkin skin) {
        FloatTargetOption option = new FloatTargetOption();
        option.setGoodsId(skin.getGoodsId());
        option.setName(skin.getName());
        option.setCollection(skin.getCollection());
        option.setRarity(skin.getRarity());
        option.setQualityLabel(skin.getQualityLabel());
        option.setMinFloat(skin.getMinFloat());
        option.setMaxFloat(skin.getMaxFloat());
        option.setPrice(skin.getPrice());
        option.setFloatSource("catalog");
        return option;
    }

    private FloatTargetOption toOption(SkinFloatRange row) {
        FloatTargetOption option = new FloatTargetOption();
        option.setGoodsId(null);
        option.setName(StringUtils.hasText(row.getNameZh()) ? row.getNameZh() : row.getNameEn());
        option.setCollection(StringUtils.hasText(row.getCollectionZh()) ? row.getCollectionZh() : row.getCollectionEn());
        // Rarity is already normalized to the trade-up scheme at import time.
        option.setRarity(SkinRarity.normalize(row.getRarity(), row.getWeapon()));
        option.setQualityLabel(null);
        option.setMinFloat(row.getMinFloat());
        option.setMaxFloat(row.getMaxFloat());
        option.setPrice(0d);
        option.setFloatSource("library");
        return option;
    }

    // Resolves the target's wear range, preferring the BUFF catalog (with goods_id/price) and
    // falling back to the float-range library — both to correct a 0~1 fallback and to support
    // targets that only exist in the library.
    private ResolvedTarget resolveTarget(FloatCalculationRequest request) {
        String goodsId = request.getTargetGoodsId();
        if (StringUtils.hasText(goodsId)) {
            CatalogSkin c = catalogService.findByGoodsId(goodsId.trim())
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.catalogSkinNotFound(goodsId)));
            ResolvedTarget t = new ResolvedTarget();
            t.goodsId = c.getGoodsId();
            t.name = c.getName();
            t.collection = c.getCollection();
            t.rarity = c.getRarity();
            t.qualityLabel = c.getQualityLabel();
            t.minFloat = c.getMinFloat();
            t.maxFloat = c.getMaxFloat();
            if (isDefaultRange(t.minFloat, t.maxFloat)) {
                Optional<SkinFloatRange> r = skinFloatRangeService.findByName(c.getName());
                if (r.isPresent()) {
                    t.minFloat = r.get().getMinFloat();
                    t.maxFloat = r.get().getMaxFloat();
                }
            }
            return t;
        }
        String name = request.getTargetName();
        if (StringUtils.hasText(name)) {
            SkinFloatRange r = skinFloatRangeService.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.catalogSkinNotFound(name)));
            ResolvedTarget t = new ResolvedTarget();
            t.goodsId = null;
            t.name = StringUtils.hasText(r.getNameZh()) ? r.getNameZh() : r.getNameEn();
            t.collection = StringUtils.hasText(r.getCollectionZh()) ? r.getCollectionZh() : r.getCollectionEn();
            t.rarity = SkinRarity.normalize(r.getRarity(), r.getWeapon());
            t.qualityLabel = null;
            t.minFloat = r.getMinFloat();
            t.maxFloat = r.getMaxFloat();
            return t;
        }
        throw new IllegalArgumentException(ErrorMessages.TARGET_GOODS_ID_NOT_BLANK);
    }

    private boolean isDefaultRange(double min, double max) {
        return min <= EPSILON && max >= 1d - EPSILON;
    }

    private static final class ResolvedTarget {
        private String goodsId;
        private String name;
        private String collection;
        private String rarity;
        private String qualityLabel;
        private double minFloat;
        private double maxFloat;
    }

    private static final class LockedFloats {
        private static final LockedFloats EMPTY = new LockedFloats(0.0d, 0);

        private final double sum;
        private final int count;

        private LockedFloats(double sum, int count) {
            this.sum = sum;
            this.count = count;
        }

        private static LockedFloats empty() {
            return EMPTY;
        }

        private double getSum() {
            return sum;
        }

        private int getCount() {
            return count;
        }
    }
}
