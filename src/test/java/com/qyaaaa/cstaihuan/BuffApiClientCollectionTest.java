package com.qyaaaa.cstaihuan;

import static org.assertj.core.api.Assertions.assertThat;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.service.BuffApiClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：武库通行证(armory)只是发放渠道，itemset 标签统一显示成“武库通行证”，
 * 真正的箱子在 weaponcase 标签里（如 Fever Case）。解析收藏品时必须用 weaponcase + 配置映射
 * 还原成真实中文收藏品名（热潮收藏品），否则整箱 armory 皮会被错归到“武库通行证”。
 */
class BuffApiClientCollectionTest {

    private final BuffApiClient client = newClientWithFeverMapping();

    private static BuffApiClient newClientWithFeverMapping() {
        BuffProperties props = new BuffProperties();
        props.getCollectionNameMapping().put("fever case", "热潮收藏品");
        return new BuffApiClient(null, props);
    }

    @Test
    void armoryItemResolvesRealCollectionFromWeaponcase() {
        // itemset=武库通行证（渠道），weaponcase=Fever Case（真实箱子）
        CatalogSkin skin = client.parseCatalogSkinFromGoodsDetail(
            armoryPayload("weapon_ump45", "classified", "武库通行证", "Fever Case", "fever case",
                "UMP-45 | K.O. 工厂 (略有磨损)"),
            null
        );

        assertThat(skin).isNotNull();
        assertThat(skin.getCollection())
            .as("armory 皮应还原成真实收藏品热潮收藏品，而不是渠道名武库通行证")
            .isEqualTo("热潮收藏品");
    }

    @Test
    void nonArmoryItemKeepsItemsetCollection() {
        // 对照组：没有 weaponcase 映射命中时，正常收藏品仍走 itemset
        CatalogSkin skin = client.parseCatalogSkinFromGoodsDetail(
            armoryPayload("weapon_ak47", "classified", "变革收藏品", null, null,
                "AK-47 | 一发入魂 (略有磨损)"),
            null
        );

        assertThat(skin).isNotNull();
        assertThat(skin.getCollection()).isEqualTo("变革收藏品");
    }

    private static Map<String, Object> armoryPayload(String weaponInternalName, String rarityInternalName,
            String itemsetLocalizedName, String weaponcaseInternalName, String weaponcaseLocalizedName, String name) {
        Map<String, Object> rarityTag = new LinkedHashMap<String, Object>();
        rarityTag.put("internal_name", rarityInternalName);

        Map<String, Object> weaponTag = new LinkedHashMap<String, Object>();
        weaponTag.put("internal_name", weaponInternalName);

        Map<String, Object> itemsetTag = new LinkedHashMap<String, Object>();
        itemsetTag.put("internal_name", "armory");
        itemsetTag.put("localized_name", itemsetLocalizedName);

        Map<String, Object> tags = new LinkedHashMap<String, Object>();
        tags.put("rarity", rarityTag);
        tags.put("weapon", weaponTag);
        tags.put("itemset", itemsetTag);
        if (weaponcaseInternalName != null || weaponcaseLocalizedName != null) {
            Map<String, Object> weaponcaseTag = new LinkedHashMap<String, Object>();
            weaponcaseTag.put("internal_name", weaponcaseInternalName);
            weaponcaseTag.put("localized_name", weaponcaseLocalizedName);
            tags.put("weaponcase", weaponcaseTag);
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("tags", tags);
        data.put("name", name);
        data.put("goods_id", "test-goods-1");

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("data", data);
        return payload;
    }
}
