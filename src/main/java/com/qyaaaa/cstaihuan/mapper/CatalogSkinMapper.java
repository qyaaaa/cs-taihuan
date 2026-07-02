package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface CatalogSkinMapper extends BaseMapper<CatalogSkin> {
    List<CatalogSkin> selectAllOrdered();

    CatalogSkin selectByGoodsId(@Param("goodsId") String goodsId);

    List<CatalogSkin> searchTargets(@Param("collection") String collection, @Param("name") String name, @Param("rarity") String rarity, @Param("limit") int limit);

    List<CatalogSkin> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    List<String> selectExistingGoodsIds();

    List<String> selectFreshGoodsIds(@Param("freshAfterTimestamp") long freshAfterTimestamp);

    List<Map<String, Object>> selectIncompleteSkinAnchors(@Param("freshAfterTimestamp") long freshAfterTimestamp, @Param("limit") int limit);

    int deleteAll();

    int insertBatch(@Param("items") List<CatalogSkin> items);

    int upsertBatch(@Param("items") List<CatalogSkin> items);

    List<CatalogSkin> selectNameCollections();

    List<CatalogSkin> selectGoodsPrices();

    List<String> selectSkinNamesByCollectionAndRarity(@Param("collection") String collection, @Param("rarity") String rarity);
}
