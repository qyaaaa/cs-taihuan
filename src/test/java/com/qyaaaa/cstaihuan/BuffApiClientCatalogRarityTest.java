package com.qyaaaa.cstaihuan;

import static org.assertj.core.api.Assertions.assertThat;

import com.qyaaaa.cstaihuan.config.BuffProperties;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.service.BuffApiClient;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：刀/手套即使 BUFF 来源品质是 covert，落库产物品质也必须归一成 gold（暗金），
 * 否则会作为隐秘(covert)产物混进普通汰换产物池，出现“保密级->隐秘级却练出刀”的错误方案。
 */
class BuffApiClientCatalogRarityTest {

    private final BuffApiClient client = new BuffApiClient(null, new BuffProperties());

    @Test
    void knifeWithCovertSourceRarity_isStoredAsGold() {
        // 千瓦箱的廓尔喀刀，BUFF 来源品质给的是 covert
        CatalogSkin skin = client.parseCatalogSkinFromGoodsDetail(
            goodsDetailPayload("weapon_knife_kukri", "covert", "廓尔喀刀（★） | 渐变之色"),
            "千瓦收藏品"
        );

        assertThat(skin).isNotNull();
        assertThat(skin.getRarity())
            .as("刀必须归一成 gold，不能当作 covert 产物")
            .isEqualTo("gold");
    }

    @Test
    void regularWeaponWithCovertRarity_staysCovert() {
        // 对照组：普通武器的 covert 品质不应被误改成 gold
        CatalogSkin skin = client.parseCatalogSkinFromGoodsDetail(
            goodsDetailPayload("weapon_ak47", "covert", "AK-47 | 传承"),
            "千瓦收藏品"
        );

        assertThat(skin).isNotNull();
        assertThat(skin.getRarity()).isEqualTo("covert");
    }

    @Test
    void uncommonWeaponRarity_isStoredAsIndustrial() {
        // CS 内部 tier 命名：uncommon = 工业级。早期未映射导致整箱皮被排除在汰换之外。
        CatalogSkin skin = client.parseCatalogSkinFromGoodsDetail(
            goodsDetailPayload("weapon_cz75a", "uncommon_weapon", "CZ75 | 测试"),
            "测试收藏品"
        );

        assertThat(skin).isNotNull();
        assertThat(skin.getRarity()).isEqualTo("industrial");
    }

    @Test
    void commonWeaponRarity_isStoredAsConsumer() {
        // CS 内部 tier 命名：common = 消费级（而非工业级），早期误归到 industrial。
        CatalogSkin skin = client.parseCatalogSkinFromGoodsDetail(
            goodsDetailPayload("weapon_glock", "common_weapon", "格洛克 | 测试"),
            "测试收藏品"
        );

        assertThat(skin).isNotNull();
        assertThat(skin.getRarity()).isEqualTo("consumer");
    }

    private static Map<String, Object> goodsDetailPayload(String weaponInternalName, String rarityInternalName, String name) {
        Map<String, Object> rarityTag = new LinkedHashMap<String, Object>();
        rarityTag.put("internal_name", rarityInternalName);

        Map<String, Object> weaponTag = new LinkedHashMap<String, Object>();
        weaponTag.put("internal_name", weaponInternalName);

        Map<String, Object> tags = new LinkedHashMap<String, Object>();
        tags.put("rarity", rarityTag);
        tags.put("weapon", weaponTag);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("tags", tags);
        data.put("name", name);
        data.put("goods_id", "test-goods-1");

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("data", data);
        return payload;
    }
}
