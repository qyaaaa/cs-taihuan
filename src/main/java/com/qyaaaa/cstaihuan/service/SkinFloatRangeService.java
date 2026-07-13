package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.model.SkinFloatRange;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SkinFloatRangeService {
    int count();

    int importFromSnapshot();

    int importMissingFromSnapshot();

    /** 按 skin_id 把图标/上线日期同步为快照值（快照非空才覆盖）；返回更新行数。 */
    int syncSnapshotFields();

    Optional<SkinFloatRange> findByName(String name);

    List<SkinFloatRange> search(String collection, String name, String rarity, int limit);

    Map<String, double[]> nameToRange();

    List<String> collectionSkinNames(String collectionZh, String rarity);

    List<String[]> listCollections();

    List<Map<String, Object>> listCollectionBrowser();
}
