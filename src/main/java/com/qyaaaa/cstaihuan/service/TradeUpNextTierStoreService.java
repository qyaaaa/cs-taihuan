package com.qyaaaa.cstaihuan.service;

import com.qyaaaa.cstaihuan.dto.NextTierCatalogGroup;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TradeUpNextTierStoreService {
    private final JdbcTemplate jdbcTemplate;

    public TradeUpNextTierStoreService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int replaceForSnapshot(long snapshotId, List<NextTierCatalogGroup> groups) {
        jdbcTemplate.update("DELETE FROM trade_up_next_tier_item WHERE snapshot_id = ?", Long.valueOf(snapshotId));

        int total = 0;
        final long now = System.currentTimeMillis();
        for (NextTierCatalogGroup group : groups) {
            List<CatalogSkin> items = group.getItems();
            if (items == null || items.isEmpty()) {
                continue;
            }
            total += items.size();
            batchInsert(snapshotId, now, group, items);
        }
        return total;
    }

    private void batchInsert(final long snapshotId, final long createdAt, final NextTierCatalogGroup group, final List<CatalogSkin> items) {
        jdbcTemplate.batchUpdate(
            "INSERT INTO trade_up_next_tier_item (snapshot_id, collection_name, base_rarity, target_rarity, inventory_count, skin_name, skin_price, min_float, max_float, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                public void setValues(PreparedStatement statement, int index) throws java.sql.SQLException {
                    CatalogSkin skin = items.get(index);
                    statement.setLong(1, snapshotId);
                    statement.setString(2, group.getCollection());
                    statement.setString(3, group.getBaseRarity());
                    statement.setString(4, group.getTargetRarity());
                    statement.setInt(5, group.getInventoryCount());
                    statement.setString(6, skin.getName());
                    statement.setBigDecimal(7, java.math.BigDecimal.valueOf(skin.getPrice()));
                    statement.setDouble(8, skin.getMinFloat());
                    statement.setDouble(9, skin.getMaxFloat());
                    statement.setLong(10, createdAt);
                }

                public int getBatchSize() {
                    return items.size();
                }
            }
        );
    }
}
