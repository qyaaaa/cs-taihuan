package com.qyaaaa.cstaihuan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.FloatPriceBand;
import com.qyaaaa.cstaihuan.model.Outcome;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TradeUpOptimizerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double ASSERTION_TOLERANCE = 0.000001d;

    // 回归常规 10 合 1：验证平均磨损、产物自身 Min/Max Float、价格区间和税后 EV 同时生效。
    @Test
    void evaluatesTenItemContractWithOutputFloatFormulaAndPriceBands() throws Exception {
        TradeUpOptimizer optimizer = new TradeUpOptimizer(catalog(), 0.025d, null, outputPriceBands());

        List<TradeUpPlan> plans = optimizer.findBestContracts(
            inventory(),
            5,
            20,
            100,
            "expectedOutputValue",
            "mil-spec",
            "normal",
            "regular"
        );

        JsonNode expected = expectedPlans().get("milSpecToRestricted");
        assertThat(plans).hasSize(1);

        TradeUpPlan plan = plans.get(0);
        assertPlanTotals(plan, expected);
        assertThat(plan.getInputs()).hasSize(10);
        assertThat(plan.getOutcomes()).hasSize(expected.get("outcomeCount").asInt());

        Outcome alpha = outcomeByGoodsId(plan, "out-alpha-ft");
        assertThat(alpha.getProbability()).isCloseTo(expected.get("probability").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(alpha.getEstimatedFloat()).isCloseTo(expected.get("alphaEstimatedFloat").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(alpha.getEstimatedSalePrice()).isCloseTo(expected.get("alphaEstimatedSalePrice").asDouble(), within(ASSERTION_TOLERANCE));

        Outcome beta = outcomeByGoodsId(plan, "out-beta-ft");
        assertThat(beta.getProbability()).isCloseTo(expected.get("probability").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(beta.getEstimatedFloat()).isCloseTo(expected.get("betaEstimatedFloat").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(beta.getEstimatedSalePrice()).isCloseTo(expected.get("betaEstimatedSalePrice").asDouble(), within(ASSERTION_TOLERANCE));
    }

    // 回归隐秘到金色 5 合 1：验证暗金属性锁定，并确认暗金手套不会进入金色产出池。
    @Test
    void evaluatesFiveCovertStatTrakContractAndFiltersGoldGloves() throws Exception {
        TradeUpOptimizer optimizer = new TradeUpOptimizer(catalog(), 0.025d);

        List<TradeUpPlan> plans = optimizer.findBestContracts(
            inventory(),
            5,
            20,
            100,
            "expectedOutputValue",
            "covert",
            "stattrak",
            "gold"
        );

        JsonNode expected = expectedPlans().get("statTrakCovertToGold");
        assertThat(plans).hasSize(1);

        TradeUpPlan plan = plans.get(0);
        assertPlanTotals(plan, expected);
        assertThat(plan.getInputs()).hasSize(5);
        assertThat(plan.getOutcomes()).hasSize(expected.get("outcomeCount").asInt());

        Outcome knife = outcomeByGoodsId(plan, "out-stattrak-knife-fn");
        assertThat(knife.getProbability()).isCloseTo(expected.get("probability").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(knife.getEstimatedFloat()).isCloseTo(expected.get("knifeEstimatedFloat").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(knife.getEstimatedSalePrice()).isCloseTo(expected.get("knifeEstimatedSalePrice").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(plan.getOutcomes()).extracting(outcome -> outcome.getSkin().getGoodsId()).doesNotContain("out-stattrak-gloves-ft");
    }

    // 如果某箱暗金金色产物只剩手套，暗金红皮不能签署有效汰换合同。
    @Test
    void refusesStatTrakGoldContractWhenOnlyGlovesAreAvailable() throws Exception {
        List<CatalogSkin> onlyGloveCatalog = new ArrayList<CatalogSkin>();
        for (CatalogSkin skin : catalog()) {
            if ("out-stattrak-gloves-ft".equals(skin.getGoodsId())) {
                onlyGloveCatalog.add(skin);
            }
        }
        TradeUpOptimizer optimizer = new TradeUpOptimizer(onlyGloveCatalog, 0.025d);

        List<TradeUpPlan> plans = optimizer.findBestContracts(
            inventory(),
            5,
            20,
            100,
            "expectedOutputValue",
            "covert",
            "stattrak",
            "gold"
        );

        assertThat(plans).isEmpty();
    }

    // 纪念品规则：纪念品可作为汰换素材投入，但产物只能是普通版皮肤，纪念品本身不可能作为汰换产物。
    @Test
    void souvenirInputProducesNormalOutcomesAndNeverSouvenirOutcomes() {
        TradeUpOptimizer optimizer = new TradeUpOptimizer(souvenirScenarioCatalog(), 0.025d);

        List<BuffItem> souvenirInputs = new ArrayList<BuffItem>();
        for (int i = 1; i <= 10; i++) {
            souvenirInputs.add(souvenirInput(i));
        }

        List<TradeUpPlan> plans = optimizer.findBestContracts(
            souvenirInputs,
            5,
            20,
            100,
            "expectedOutputValue",
            "mil-spec",
            "normal",
            "regular"
        );

        assertThat(plans).hasSize(1);
        TradeUpPlan plan = plans.get(0);

        // 纪念品可以作为投入素材。
        assertThat(plan.getInputs()).hasSize(10);
        assertThat(plan.getInputs()).allMatch(item -> item.getName().contains("纪念品"));

        // 产物只有普通版皮肤，绝不包含纪念品产物。
        assertThat(plan.getOutcomes()).isNotEmpty();
        assertThat(plan.getOutcomes()).noneMatch(outcome -> outcome.getSkin().getName().contains("纪念品"));
        assertThat(plan.getOutcomes())
            .extracting(outcome -> outcome.getSkin().getGoodsId())
            .contains("out-normal-ft")
            .doesNotContain("out-souvenir-ft");
    }

    // 同一收藏品下既有普通也有纪念品的上级产物，用来验证纪念品被排除在产出池之外。
    private static List<CatalogSkin> souvenirScenarioCatalog() {
        List<CatalogSkin> catalog = new ArrayList<CatalogSkin>();
        catalog.add(catalogSkin("out-normal-ft", "AK-47 | 测试产物 (久经沙场)", "测试收藏品", "restricted", 0.15d, 0.38d, 120.0d));
        catalog.add(catalogSkin("out-souvenir-ft", "AK-47（纪念品） | 测试产物 (久经沙场)", "测试收藏品", "restricted", 0.15d, 0.38d, 60.0d));
        return catalog;
    }

    private static CatalogSkin catalogSkin(String goodsId, String name, String collection, String rarity, double minFloat, double maxFloat, double price) {
        CatalogSkin skin = new CatalogSkin();
        skin.setGoodsId(goodsId);
        skin.setName(name);
        skin.setCollection(collection);
        skin.setRarity(rarity);
        skin.setCategoryKey("weapon_ak47");
        skin.setQualityLabel("普通");
        skin.setMinFloat(minFloat);
        skin.setMaxFloat(maxFloat);
        skin.setPrice(price);
        return skin;
    }

    // 各投入用不同 goods_id 以免被组合去重折叠；名称含“纪念品”以触发纪念品识别。
    private static BuffItem souvenirInput(int index) {
        return new BuffItem(
            "asset-souv-" + index,
            "格洛克 18 型（纪念品） | 测试素材 (久经沙场)",
            5.0d,
            Double.valueOf(0.2d),
            "0.2",
            null,
            null,
            "测试收藏品",
            "mil-spec",
            "weapon_glock",
            "mil-spec",
            "军规级",
            true,
            "souv-in-" + index,
            new LinkedHashMap<String, Object>()
        );
    }

    // 统一断言方案级数值，确保 fixtures 中的预期结果能作为稳定回归基准。
    private static void assertPlanTotals(TradeUpPlan plan, JsonNode expected) {
        assertThat(plan.getRarity()).isEqualTo(expected.get("rarity").asText());
        assertThat(plan.getInputCost()).isCloseTo(expected.get("inputCost").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(plan.getExpectedOutputValue()).isCloseTo(expected.get("expectedOutputValue").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(plan.getExpectedProfit()).isCloseTo(expected.get("expectedProfit").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(plan.getRoi()).isCloseTo(expected.get("roi").asDouble(), within(ASSERTION_TOLERANCE));
        assertThat(plan.getAverageInputFloat()).isCloseTo(expected.get("averageInputFloat").asDouble(), within(ASSERTION_TOLERANCE));
    }

    // 产物顺序可能随概率或排序规则调整，测试用 goods_id 定位具体产物更稳。
    private static Outcome outcomeByGoodsId(TradeUpPlan plan, String goodsId) {
        for (Outcome outcome : plan.getOutcomes()) {
            if (goodsId.equals(outcome.getSkin().getGoodsId())) {
                return outcome;
            }
        }
        throw new AssertionError("Missing outcome goods_id=" + goodsId);
    }

    private static List<BuffItem> inventory() throws IOException {
        return readFixture("sample-inventory.json", new TypeReference<List<BuffItem>>() {
        });
    }

    private static List<CatalogSkin> catalog() throws IOException {
        return readFixture("sample-catalog.json", new TypeReference<List<CatalogSkin>>() {
        });
    }

    private static JsonNode expectedPlans() throws IOException {
        InputStream stream = fixtureStream("expected-plans.json");
        try {
            return OBJECT_MAPPER.readTree(stream);
        } finally {
            stream.close();
        }
    }

    // 样例里只给 Alpha 配精细价格档，用来证明价格档优先于 catalog 默认价格。
    private static Map<String, List<FloatPriceBand>> outputPriceBands() {
        Map<String, List<FloatPriceBand>> bands = new LinkedHashMap<String, List<FloatPriceBand>>();
        bands.put("out-alpha-ft", Collections.singletonList(band(0.30d, 0.40d, 200.0d)));
        return bands;
    }

    private static FloatPriceBand band(double minFloat, double maxFloat, double price) {
        FloatPriceBand band = new FloatPriceBand();
        band.setMinFloat(minFloat);
        band.setMaxFloat(maxFloat);
        band.setPrice(price);
        return band;
    }

    // 所有测试数据都从脱敏 fixtures 读取，避免单测依赖真实 BUFF 响应或本地数据库。
    private static <T> T readFixture(String fileName, TypeReference<T> type) throws IOException {
        InputStream stream = fixtureStream(fileName);
        try {
            return OBJECT_MAPPER.readValue(stream, type);
        } finally {
            stream.close();
        }
    }

    private static InputStream fixtureStream(String fileName) {
        String path = "fixtures/trade-up/" + fileName;
        InputStream stream = TradeUpOptimizerTest.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Missing test fixture: " + path);
        }
        return stream;
    }
}
