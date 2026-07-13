package com.qyaaaa.cstaihuan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qyaaaa.cstaihuan.model.SkinFloatRange;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface SkinFloatRangeMapper extends BaseMapper<SkinFloatRange> {
    int deleteAll();

    List<String> selectSkinIds();

    int insertBatch(@Param("rows") List<SkinFloatRange> rows);

    List<SkinFloatRange> selectSyncStates();

    int syncFieldsBySkinId(@Param("rows") List<SkinFloatRange> rows);

    SkinFloatRange selectByBaseName(@Param("key") String key);

    List<SkinFloatRange> search(@Param("collection") String collection, @Param("name") String name, @Param("rarity") String rarity, @Param("limit") int limit);

    List<SkinFloatRange> selectNameRanges();

    List<String> selectSkinNamesByCollectionAndRarity(@Param("collectionZh") String collectionZh, @Param("rarity") String rarity);

    List<Map<String, String>> selectCollections();

    List<SkinFloatRange> selectCollectionBrowserRows();
}
