package com.qyaaaa.cstaihuan;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.mapper.InventoryItemMapper;
import com.qyaaaa.cstaihuan.mapper.InventorySnapshotMapper;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import com.qyaaaa.cstaihuan.model.InventorySnapshotSummary;
import com.qyaaaa.cstaihuan.service.InventorySnapshotStoreService;
import com.qyaaaa.cstaihuan.service.impl.InventorySnapshotStoreServiceImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(classes = InventorySnapshotStoreServiceMapperTest.TestConfig.class)
@Sql(statements = {
    "DROP TABLE IF EXISTS inventory_item",
    "DROP TABLE IF EXISTS inventory_snapshot",
    "CREATE TABLE inventory_snapshot (" +
        "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
        "account_id BIGINT NOT NULL DEFAULT 1, " +
        "game VARCHAR(32) NOT NULL, " +
        "item_count INT NOT NULL, " +
        "fingerprint VARCHAR(64) NOT NULL, " +
        "created_at BIGINT NOT NULL, " +
        "last_seen_at BIGINT NOT NULL)",
    "CREATE TABLE inventory_item (" +
        "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
        "snapshot_id BIGINT NOT NULL, " +
        "asset_id VARCHAR(128), " +
        "goods_id VARCHAR(128), " +
        "name VARCHAR(255), " +
        "price DECIMAL(18, 2) NOT NULL, " +
        "float_price DECIMAL(18, 2), " +
        "float_value DOUBLE, " +
        "float_value_raw VARCHAR(64), " +
        "image_url VARCHAR(1024), " +
        "wear_name VARCHAR(128), " +
        "collection_name VARCHAR(255), " +
        "rarity VARCHAR(64), " +
        "category_key VARCHAR(128), " +
        "filter_rarity VARCHAR(64), " +
        "quality_label VARCHAR(128), " +
        "tradable TINYINT(1) NOT NULL, " +
        "raw_json CLOB)"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class InventorySnapshotStoreServiceMapperTest {
    @Autowired
    private InventorySnapshotStoreService snapshotStore;

    @Test
    void floatPriceLifecyclePersistsCarriesOverAndFiltersMissingAssets() {
        InventorySnapshotRecord previous = snapshotStore.saveSnapshot(7L, "csgo", "prev", Collections.singletonList(
            item("asset-a", "goods-a", "AK-47 | A", 100.0, 0.012D, "weapon_ak47")
        ));
        snapshotStore.updateFloatPrice(previous.getId(), "asset-a", 12.34D);

        InventorySnapshotRecord current = snapshotStore.saveSnapshot(7L, "csgo", "current", Arrays.asList(
            item("asset-a", "goods-a", "AK-47 | A", 110.0, 0.012D, "weapon_ak47"),
            item("asset-b", "goods-b", "M4A1-S | B", 99.0, 0.067D, "weapon_m4a1_silencer"),
            item("asset-c", "goods-c", "P250 | C", 10.0, 0.11D, "weapon_p250"),
            item("asset-d", "goods-d", "Glock-18 | D", 88.0, null, "weapon_glock"),
            item("asset-e", "", "USP-S | E", 77.0, 0.22D, "weapon_usp_silencer"),
            item("asset-f", "goods-f", "Sticker | F", 120.0, 0.33D, "sticker")
        ));

        assertThat(snapshotStore.carryOverFloatPrices(current.getId(), previous.getId())).isEqualTo(1);
        assertThat(missingFloatPrice(snapshotStore.loadItems(current.getId()), 20.0D)).containsExactly("asset-b");

        assertThat(snapshotStore.updateFloatPrice(current.getId(), "asset-b", 56.78D)).isEqualTo(1);
        assertThat(missingFloatPrice(snapshotStore.loadItems(current.getId()), 20.0D)).isEmpty();

        // 批量写回：一条 SQL 覆盖多件。
        java.util.Map<String, Double> batch = new java.util.LinkedHashMap<String, Double>();
        batch.put("asset-c", 3.21D);
        assertThat(snapshotStore.batchUpdateFloatPrices(current.getId(), batch)).isEqualTo(1);

        List<BuffItem> loaded = snapshotStore.loadItems(current.getId());
        assertThat(loaded).extracting(BuffItem::getAssetId)
            .containsExactly("asset-a", "asset-b", "asset-c", "asset-d", "asset-e");
        assertThat(find(loaded, "asset-a").getFloatPrice()).isEqualTo(12.34D);
        assertThat(find(loaded, "asset-b").getFloatPrice()).isEqualTo(56.78D);

        InventorySnapshotSummary summary = snapshotStore.summarizeSnapshot(current.getId());
        assertThat(summary.getItemCount()).isEqualTo(5);
        assertThat(summary.getWithFloatCount()).isEqualTo(4);
        assertThat(summary.getTotalCost()).isEqualTo(384.0D);
    }

    // 复刻精估待办口径：武器件、有 float、有 goods_id、价格达门槛且还没精估价。
    private static List<String> missingFloatPrice(List<BuffItem> items, double minPrice) {
        List<String> assetIds = new java.util.ArrayList<String>();
        for (BuffItem item : items) {
            if (item.getFloatPrice() == null && item.getFloatValue() != null
                && item.getGoodsId() != null && !item.getGoodsId().isEmpty()
                && item.getPrice() >= minPrice) {
                assetIds.add(item.getAssetId());
            }
        }
        return assetIds;
    }

    private static BuffItem find(List<BuffItem> items, String assetId) {
        for (BuffItem item : items) {
            if (assetId.equals(item.getAssetId())) {
                return item;
            }
        }
        throw new AssertionError("未找到测试饰品: " + assetId);
    }

    private static BuffItem item(String assetId, String goodsId, String name, double price, Double floatValue, String categoryKey) {
        return new BuffItem(
            assetId, name, price, floatValue, floatValue == null ? null : String.valueOf(floatValue),
            null, "略有磨损", "测试收藏品", "mil-spec", categoryKey, "mil-spec", "军规级",
            true, goodsId, new LinkedHashMap<String, Object>()
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:inventory_mapper_test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        MybatisSqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            MybatisConfiguration configuration = new MybatisConfiguration();
            GlobalConfig globalConfig = new GlobalConfig();
            globalConfig.setBanner(false);
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setGlobalConfig(globalConfig);
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:/mapper/Inventory*Mapper.xml"));
            return factory;
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        MapperFactoryBean<InventoryItemMapper> inventoryItemMapper(org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<InventoryItemMapper> factory = new MapperFactoryBean<InventoryItemMapper>(InventoryItemMapper.class);
            factory.setSqlSessionFactory(sqlSessionFactory);
            return factory;
        }

        @Bean
        MapperFactoryBean<InventorySnapshotMapper> inventorySnapshotMapper(org.apache.ibatis.session.SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<InventorySnapshotMapper> factory = new MapperFactoryBean<InventorySnapshotMapper>(InventorySnapshotMapper.class);
            factory.setSqlSessionFactory(sqlSessionFactory);
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        InventorySnapshotStoreService inventorySnapshotStoreService(InventoryItemMapper inventoryItemMapper, ObjectMapper objectMapper) {
            return new InventorySnapshotStoreServiceImpl(inventoryItemMapper, objectMapper);
        }
    }
}
