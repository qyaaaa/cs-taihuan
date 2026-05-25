package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.dto.FloatCalculationRequest;
import com.qyaaaa.cstaihuan.dto.FloatCalculationResponse;
import com.qyaaaa.cstaihuan.dto.FloatTargetOption;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FloatCalculationService {
    private static final double EPSILON = 0.000000001d;

    private final CatalogService catalogService;

    public FloatCalculationService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public List<FloatTargetOption> searchTargets(String keyword, int limit) {
        List<CatalogSkin> skins = catalogService.searchTargets(keyword, limit);
        List<FloatTargetOption> options = new ArrayList<FloatTargetOption>();
        for (CatalogSkin skin : skins) {
            options.add(toOption(skin));
        }
        return options;
    }

    /**
     * 反推目标产物磨损需要的素材平均磨损，并在用户锁定部分素材后计算剩余槽位的平均磨损约束。
     */
    public FloatCalculationResponse calculate(FloatCalculationRequest request) {
        CatalogSkin target = catalogService.findByGoodsId(request.getTargetGoodsId().trim())
            .orElseThrow(() -> new IllegalArgumentException(ErrorMessages.catalogSkinNotFound(request.getTargetGoodsId())));
        int contractSize = normalizeContractSize(request.getContractSize(), target.getRarity());
        List<Double> lockedInputFloats = request.getLockedInputFloats();
        if (lockedInputFloats != null && lockedInputFloats.size() > contractSize) {
            throw new IllegalArgumentException(ErrorMessages.LOCKED_INPUT_FLOAT_SIZE);
        }

        double targetMinFloat = target.getMinFloat();
        double targetMaxFloat = target.getMaxFloat();
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
        int remainingSlots = contractSize - locked.count;
        double remainingTotal = requiredTotal - locked.sum;
        boolean reachable = requiredAverage >= -EPSILON
            && requiredAverage <= 1d + EPSILON
            && remainingTotal >= -EPSILON
            && remainingTotal <= remainingSlots + EPSILON;

        FloatCalculationResponse response = new FloatCalculationResponse();
        response.setTargetGoodsId(target.getGoodsId());
        response.setTargetName(target.getName());
        response.setTargetCollection(target.getCollection());
        response.setTargetRarity(target.getRarity());
        response.setTargetQualityLabel(target.getQualityLabel());
        response.setTargetFloat(targetFloat);
        response.setTargetMinFloat(targetMinFloat);
        response.setTargetMaxFloat(targetMaxFloat);
        response.setContractSize(contractSize);
        response.setRequiredAverageInputFloat(clampTiny(requiredAverage));
        response.setRequiredTotalInputFloat(clampTiny(requiredTotal));
        response.setLockedFloatSum(clampTiny(locked.sum));
        response.setLockedSlotCount(locked.count);
        response.setRemainingSlotCount(remainingSlots);
        if (remainingSlots > 0) {
            response.setRequiredRemainingAverageFloat(clampTiny(remainingTotal / remainingSlots));
            response.setAllowedRemainingMinFloat(clampTiny(boundUnit(remainingTotal - (remainingSlots - 1))));
            response.setAllowedRemainingMaxFloat(clampTiny(boundUnit(remainingTotal)));
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
        LockedFloats locked = new LockedFloats();
        if (values == null) {
            return locked;
        }
        for (Double value : values) {
            if (value == null) {
                continue;
            }
            locked.sum += value.doubleValue();
            locked.count++;
        }
        return locked;
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
        return String.format(java.util.Locale.ROOT, "%.8f", value);
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
        return option;
    }

    private static class LockedFloats {
        private double sum;
        private int count;
    }
}
