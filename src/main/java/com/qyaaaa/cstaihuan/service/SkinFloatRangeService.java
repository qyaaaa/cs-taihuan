package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.model.SkinFloatRange;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SkinFloatRangeService {
    int count();

    int importFromSnapshot();

    int importMissingFromSnapshot();

    int backfillMissingImages();

    /** 为尚无上线日期的行按快照补 release_date；返回补充行数。 */
    int backfillMissingReleaseDates();

    Optional<SkinFloatRange> findByName(String name);

    List<SkinFloatRange> search(String collection, String name, String rarity, int limit);

    Map<String, double[]> nameToRange();

    List<String> collectionSkinNames(String collectionZh, String rarity);

    List<String[]> listCollections();

    List<Map<String, Object>> listCollectionBrowser();
}
