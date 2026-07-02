package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CatalogService {
    List<CatalogSkin> loadAll();

    Optional<CatalogSkin> findByGoodsId(String goodsId);

    List<CatalogSkin> searchTargets(String collection, String name, String rarity, int limit);

    List<CatalogSkin> searchTargets(String keyword, int limit);

    Set<String> loadExistingGoodsIds();

    Set<String> loadFreshGoodsIds(long freshAfterTimestamp);

    Map<String, String> findIncompleteSkinAnchors(long freshAfterTimestamp, int limit);

    int replaceAll(List<CatalogSkin> items);

    int upsertAll(List<CatalogSkin> items);

    int count();

    Map<String, String> nameToCollection();

    List<String> collectionSkinNames(String collection, String rarity);
}
