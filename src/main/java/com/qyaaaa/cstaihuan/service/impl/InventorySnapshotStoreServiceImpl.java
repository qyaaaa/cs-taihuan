package com.qyaaaa.cstaihuan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.mapper.InventoryItemMapper;
import com.qyaaaa.cstaihuan.mapper.InventorySnapshotMapper;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.InventoryItem;
import com.qyaaaa.cstaihuan.model.InventorySnapshotRecord;
import com.qyaaaa.cstaihuan.model.InventorySnapshotSummary;
import com.qyaaaa.cstaihuan.service.InventorySnapshotStoreService;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventorySnapshotStoreServiceImpl extends ServiceImpl<InventorySnapshotMapper, InventorySnapshotRecord> implements InventorySnapshotStoreService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final InventoryItemMapper inventoryItemMapper;
    private final ObjectMapper objectMapper;

    public InventorySnapshotStoreServiceImpl(InventoryItemMapper inventoryItemMapper, ObjectMapper objectMapper) {
        this.inventoryItemMapper = inventoryItemMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<InventorySnapshotRecord> findLatest(String game) {
        return findLatest(1L, game);
    }

    @Override
    public Optional<InventorySnapshotRecord> findLatest(long accountId, String game) {
        return Optional.ofNullable(baseMapper.selectLatest(accountId, game));
    }

    @Override
    public Optional<InventorySnapshotRecord> findById(long snapshotId) {
        return Optional.ofNullable(getById(snapshotId));
    }

    @Override
    public Optional<InventorySnapshotRecord> findById(long accountId, long snapshotId) {
        return Optional.ofNullable(baseMapper.selectByAccountAndId(accountId, snapshotId));
    }

    @Override
    public List<BuffItem> loadItems(long snapshotId) {
        return toBuffItems(inventoryItemMapper.selectWeaponItems(snapshotId));
    }

    @Override
    public List<BuffItem> loadPagedItems(long snapshotId, int page, int pageSize) {
        return loadPagedItems(snapshotId, page, pageSize, null);
    }

    @Override
    public List<BuffItem> loadPagedItems(long snapshotId, int page, int pageSize, String rarity) {
        int offset = (page - 1) * pageSize;
        return toBuffItems(inventoryItemMapper.selectPagedWeaponItems(snapshotId, normalizeRarity(rarity), pageSize, offset));
    }

    @Override
    public int countItems(long snapshotId) {
        return countItems(snapshotId, null);
    }

    @Override
    public int countItems(long snapshotId, String rarity) {
        return inventoryItemMapper.countWeaponItems(snapshotId, normalizeRarity(rarity));
    }

    @Override
    public InventorySnapshotSummary summarizeSnapshot(long snapshotId) {
        return summarizeSnapshot(snapshotId, null);
    }

    @Override
    public InventorySnapshotSummary summarizeSnapshot(long snapshotId, String rarity) {
        InventorySnapshotSummary summary = inventoryItemMapper.summarizeWeaponItems(snapshotId, normalizeRarity(rarity));
        if (summary == null) {
            summary = new InventorySnapshotSummary();
        }
        return summary;
    }

    @Override
    @Transactional
    public InventorySnapshotRecord saveSnapshot(String game, String fingerprint, List<BuffItem> items) {
        return saveSnapshot(1L, game, fingerprint, items);
    }

    /**
     * 库存快照先保存主表并回填快照 ID，再批量保存明细；两步必须在同一事务里保持一致。
     */
    @Override
    @Transactional
    public InventorySnapshotRecord saveSnapshot(long accountId, String game, String fingerprint, List<BuffItem> items) {
        List<BuffItem> safeItems = items == null ? Collections.<BuffItem>emptyList() : items;
        long now = System.currentTimeMillis();
        InventorySnapshotRecord record = new InventorySnapshotRecord();
        record.setAccountId(Long.valueOf(accountId));
        record.setGame(game);
        record.setItemCount(Integer.valueOf(safeItems.size()));
        record.setFingerprint(fingerprint);
        record.setCreatedAt(Long.valueOf(now));
        record.setLastSeenAt(Long.valueOf(now));
        save(record);

        if (record.getId() == null) {
            throw new IllegalStateException(ErrorMessages.CREATE_INVENTORY_SNAPSHOT_FAILED);
        }
        if (!safeItems.isEmpty()) {
            inventoryItemMapper.insertBatch(toInventoryItems(record.getId().longValue(), safeItems));
        }
        return record;
    }

    @Override
    public void touch(long snapshotId) {
        InventorySnapshotRecord record = new InventorySnapshotRecord();
        record.setId(Long.valueOf(snapshotId));
        record.setLastSeenAt(Long.valueOf(System.currentTimeMillis()));
        updateById(record);
    }

    private List<BuffItem> toBuffItems(List<InventoryItem> rows) {
        List<BuffItem> items = new ArrayList<BuffItem>();
        for (InventoryItem row : rows) {
            items.add(toBuffItem(row));
        }
        return items;
    }

    private BuffItem toBuffItem(InventoryItem row) {
        BuffItem item = new BuffItem();
        item.setAssetId(row.getAssetId());
        item.setGoodsId(row.getGoodsId());
        item.setName(row.getName());
        item.setPrice(row.getPrice() == null ? 0D : row.getPrice().doubleValue());
        item.setFloatValue(row.getFloatValue());
        item.setFloatValueRaw(row.getFloatValueRaw());
        item.setImageUrl(row.getImageUrl());
        item.setWearName(row.getWearName());
        item.setCollection(row.getCollection());
        item.setRarity(row.getRarity());
        item.setCategoryKey(row.getCategoryKey());
        item.setFilterRarity(row.getFilterRarity());
        item.setQualityLabel(row.getQualityLabel());
        item.setTradable(Boolean.TRUE.equals(row.getTradable()));
        item.setRaw(readRaw(row.getRawJson()));
        return item;
    }

    private List<InventoryItem> toInventoryItems(long snapshotId, List<BuffItem> items) {
        List<InventoryItem> rows = new ArrayList<InventoryItem>();
        for (BuffItem item : items) {
            rows.add(toInventoryItem(snapshotId, item));
        }
        return rows;
    }

    private InventoryItem toInventoryItem(long snapshotId, BuffItem item) {
        InventoryItem row = new InventoryItem();
        row.setSnapshotId(Long.valueOf(snapshotId));
        row.setAssetId(item.getAssetId());
        row.setGoodsId(item.getGoodsId());
        row.setName(item.getName());
        row.setPrice(BigDecimal.valueOf(item.getPrice()));
        row.setFloatValue(item.getFloatValue());
        row.setFloatValueRaw(item.getFloatValueRaw());
        row.setImageUrl(item.getImageUrl());
        row.setWearName(item.getWearName());
        row.setCollection(item.getCollection());
        row.setRarity(item.getRarity());
        row.setCategoryKey(item.getCategoryKey());
        row.setFilterRarity(item.getFilterRarity());
        row.setQualityLabel(item.getQualityLabel());
        row.setTradable(Boolean.valueOf(item.isTradable()));
        row.setRawJson(writeRaw(item.getRaw()));
        return row;
    }

    private String normalizeRarity(String rarity) {
        return rarity == null || rarity.trim().isEmpty() ? null : rarity.trim().toLowerCase();
    }

    private Map<String, Object> readRaw(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(rawJson, MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException(ErrorMessages.READ_RAW_INVENTORY_ITEM_FAILED, ex);
        }
    }

    private String writeRaw(Map<String, Object> raw) {
        try {
            return objectMapper.writeValueAsString(raw == null ? Collections.emptyMap() : raw);
        } catch (IOException ex) {
            throw new IllegalStateException(ErrorMessages.PERSIST_RAW_INVENTORY_ITEM_FAILED, ex);
        }
    }
}
