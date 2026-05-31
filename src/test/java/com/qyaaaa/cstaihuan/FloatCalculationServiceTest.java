package com.qyaaaa.cstaihuan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.qyaaaa.cstaihuan.dto.FloatCalculationRequest;
import com.qyaaaa.cstaihuan.dto.FloatCalculationResponse;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.service.CatalogService;
import com.qyaaaa.cstaihuan.service.SkinFloatRangeService;
import com.qyaaaa.cstaihuan.service.FloatCalculationService;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FloatCalculationServiceTest {
    private static final double ASSERTION_TOLERANCE = 0.000001d;

    // 验证二西莫夫这类固定磨损区间产物会用自身 Min/Max Float 反推素材平均磨损。
    @Test
    void calculatesRequiredInputFloatsFromTargetRangeAndLockedSlots() {
        FloatCalculationService service = serviceWithTarget(target("target-asiimov", 0.18d, 1.0d));

        FloatCalculationRequest request = request(10, 0.508d, 0.22d, 0.24d, null, null, null, null, null, null, null, null);
        FloatCalculationResponse response = service.calculate(request);

        assertThat(response.isReachable()).isTrue();
        assertThat(response.getRequiredAverageInputFloat()).isCloseTo(0.4d, within(ASSERTION_TOLERANCE));
        assertThat(response.getRequiredTotalInputFloat()).isCloseTo(4.0d, within(ASSERTION_TOLERANCE));
        assertThat(response.getLockedFloatSum()).isCloseTo(0.46d, within(ASSERTION_TOLERANCE));
        assertThat(response.getRemainingSlotCount()).isEqualTo(8);
        assertThat(response.getRequiredRemainingAverageFloat()).isCloseTo(0.4425d, within(ASSERTION_TOLERANCE));
        assertThat(response.getAllowedRemainingMinFloat()).isEqualTo(0d);
        assertThat(response.getAllowedRemainingMaxFloat()).isCloseTo(1.0d, within(ASSERTION_TOLERANCE));
    }

    // 当已锁定材料磨损总和超过目标所需总磨损时，剩余槽位无法通过负磨损修正。
    @Test
    void marksPlanUnreachableWhenLockedFloatsAlreadyExceedRequiredTotal() {
        FloatCalculationService service = serviceWithTarget(target("target-low-float", "gold", 0.0d, 0.8d));

        FloatCalculationRequest request = request(5, 0.08d, 0.7d, 0.2d, null, null, null);
        FloatCalculationResponse response = service.calculate(request);

        assertThat(response.isReachable()).isFalse();
        assertThat(response.getRequiredAverageInputFloat()).isCloseTo(0.1d, within(ASSERTION_TOLERANCE));
        assertThat(response.getRequiredTotalInputFloat()).isCloseTo(0.5d, within(ASSERTION_TOLERANCE));
        assertThat(response.getLockedFloatSum()).isCloseTo(0.9d, within(ASSERTION_TOLERANCE));
        assertThat(response.getRequiredRemainingAverageFloat()).isCloseTo(-0.133333d, within(ASSERTION_TOLERANCE));
    }

    @Test
    void rejectsTargetFloatOutsideTargetSkinRange() {
        FloatCalculationService service = serviceWithTarget(target("target-range", 0.18d, 1.0d));

        assertThatThrownBy(() -> service.calculate(request(10, 0.05d)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ErrorMessages.targetFloatOutOfRange(0.18d, 1.0d));
    }

    @Test
    void rejectsFiveItemContractForCovertTarget() {
        FloatCalculationService service = serviceWithTarget(target("target-covert", "covert", 0.0d, 0.07d));

        assertThatThrownBy(() -> service.calculate(request(5, 0.05d)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ErrorMessages.CONTRACT_SIZE_TARGET_MISMATCH);
    }

    @Test
    void rejectsTenItemContractForGoldTarget() {
        FloatCalculationService service = serviceWithTarget(target("target-gold", "gold", 0.0d, 0.8d));

        assertThatThrownBy(() -> service.calculate(request(10, 0.2d)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ErrorMessages.CONTRACT_SIZE_TARGET_MISMATCH);
    }

    private static FloatCalculationService serviceWithTarget(CatalogSkin target) {
        CatalogService catalogService = Mockito.mock(CatalogService.class);
        when(catalogService.findByGoodsId(anyString())).thenReturn(Optional.of(target));
        SkinFloatRangeService skinFloatRangeService = Mockito.mock(SkinFloatRangeService.class);
        Mockito.lenient().when(skinFloatRangeService.findByName(anyString())).thenReturn(Optional.empty());
        return new FloatCalculationService(catalogService, skinFloatRangeService);
    }

    private static FloatCalculationRequest request(int contractSize, double targetFloat, Double... lockedFloats) {
        FloatCalculationRequest request = new FloatCalculationRequest();
        request.setTargetGoodsId("target-asiimov");
        request.setTargetFloat(Double.valueOf(targetFloat));
        request.setContractSize(Integer.valueOf(contractSize));
        request.setLockedInputFloats(Arrays.asList(lockedFloats));
        return request;
    }

    private static CatalogSkin target(String goodsId, double minFloat, double maxFloat) {
        return target(goodsId, "covert", minFloat, maxFloat);
    }

    private static CatalogSkin target(String goodsId, String rarity, double minFloat, double maxFloat) {
        CatalogSkin skin = new CatalogSkin();
        skin.setGoodsId(goodsId);
        skin.setName("AWP | Asiimov");
        skin.setCollection("Phoenix Collection");
        skin.setRarity(rarity);
        skin.setQualityLabel("Covert");
        skin.setMinFloat(minFloat);
        skin.setMaxFloat(maxFloat);
        skin.setPrice(1200d);
        return skin;
    }
}
