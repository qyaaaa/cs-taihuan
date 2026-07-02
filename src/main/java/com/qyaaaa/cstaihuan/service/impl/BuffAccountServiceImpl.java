package com.qyaaaa.cstaihuan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qyaaaa.cstaihuan.dto.BuffAccountRequest;
import com.qyaaaa.cstaihuan.exception.ErrorMessages;
import com.qyaaaa.cstaihuan.mapper.BuffAccountMapper;
import com.qyaaaa.cstaihuan.mapper.CatalogSyncTaskMapper;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import com.qyaaaa.cstaihuan.model.CatalogSyncTaskRecord;
import com.qyaaaa.cstaihuan.service.BuffAccountService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BuffAccountServiceImpl extends ServiceImpl<BuffAccountMapper, BuffAccount> implements BuffAccountService {
    private static final String DEFAULT_NICKNAME = "默认账号";
    private static final String STATUS_UNKNOWN = "UNKNOWN";
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final int MAX_BUFF_USER_ID_LENGTH = 128;

    private final CatalogSyncTaskMapper catalogSyncTaskMapper;

    public BuffAccountServiceImpl(CatalogSyncTaskMapper catalogSyncTaskMapper) {
        this.catalogSyncTaskMapper = catalogSyncTaskMapper;
    }

    @Override
    public List<BuffAccount> listAccounts() {
        ensureDefaultAccount();
        return list(new LambdaQueryWrapper<BuffAccount>().orderByAsc(BuffAccount::getId));
    }

    @Override
    public BuffAccount createAccount(BuffAccountRequest request) {
        long now = System.currentTimeMillis();
        BuffAccount account = new BuffAccount();
        account.setNickname(normalizeNickname(request == null ? null : request.getNickname()));
        account.setBuffUserId(normalizeNullable(request == null ? null : request.getBuffUserId()));
        account.setStatus(STATUS_UNKNOWN);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        save(account);
        return requireAccount(account.getId());
    }

    @Override
    public BuffAccount updateAccount(long accountId, BuffAccountRequest request) {
        requireAccount(accountId);
        BuffAccount account = new BuffAccount();
        account.setId(accountId);
        account.setNickname(normalizeNickname(request == null ? null : request.getNickname()));
        account.setBuffUserId(normalizeNullable(request == null ? null : request.getBuffUserId()));
        account.setUpdatedAt(System.currentTimeMillis());
        updateById(account);
        return requireAccount(accountId);
    }

    /**
     * 删除账号时需要同步清理账号私有数据；目录商品本身是全局数据，不能跟着账号删除。
     */
    @Override
    @Transactional
    public void deleteAccount(long accountId) {
        requireAccount(accountId);
        if (listAccounts().size() <= 1) {
            throw new IllegalArgumentException(ErrorMessages.BUFF_ACCOUNT_KEEP_ONE);
        }
        // 这些表带 account_id 但没有外键级联；删除账号前显式清理子表，避免遗留孤儿数据。
        catalogSyncTaskMapper.delete(new LambdaQueryWrapper<CatalogSyncTaskRecord>()
            .eq(CatalogSyncTaskRecord::getAccountId, accountId));
        baseMapper.deleteTradeUpNextTierItemsByAccountId(accountId);
        baseMapper.deleteInventorySnapshotsByAccountId(accountId);
        // buff_session 会通过 ON DELETE CASCADE 自动删除。
        removeById(accountId);
    }

    @Override
    public long resolveDefaultAccountId() {
        ensureDefaultAccount();
        BuffAccount account = baseMapper.selectFirst();
        return account == null ? 1L : account.getId();
    }

    @Override
    public BuffAccount requireAccount(long accountId) {
        BuffAccount account = getById(accountId);
        if (account == null) {
            throw new IllegalArgumentException(ErrorMessages.buffAccountNotFound(accountId));
        }
        return account;
    }

    @Override
    public void updateSessionSummary(long accountId, String maskedCookie, String status, String lastValidatedAt) {
        requireAccount(accountId);
        BuffAccount account = new BuffAccount();
        account.setId(accountId);
        account.setMaskedCookie(maskedCookie);
        account.setStatus(StringUtils.hasText(status) ? status : STATUS_UNKNOWN);
        account.setLastValidatedAt(lastValidatedAt);
        account.setUpdatedAt(System.currentTimeMillis());
        updateById(account);
    }

    @Override
    public void updateImportedIdentity(long accountId, String nickname, String buffUserId) {
        BuffAccount current = requireAccount(accountId);
        String normalizedNickname = normalizeNullable(nickname);
        String normalizedBuffUserId = normalizeNullable(buffUserId);
        BuffAccount account = new BuffAccount();
        account.setId(accountId);
        account.setNickname(normalizedNickname == null ? current.getNickname() : limit(normalizedNickname, MAX_NICKNAME_LENGTH));
        account.setBuffUserId(normalizedBuffUserId == null ? current.getBuffUserId() : limit(normalizedBuffUserId, MAX_BUFF_USER_ID_LENGTH));
        account.setUpdatedAt(System.currentTimeMillis());
        updateById(account);
    }

    @Override
    public Optional<BuffAccount> findOtherAccountByBuffUserId(String buffUserId, long excludeAccountId) {
        String normalized = normalizeNullable(buffUserId);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(baseMapper.selectOtherByBuffUserId(normalized, excludeAccountId));
    }

    private void ensureDefaultAccount() {
        if (count() > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        BuffAccount account = new BuffAccount();
        account.setNickname(DEFAULT_NICKNAME);
        account.setStatus(STATUS_UNKNOWN);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        save(account);
    }

    private String normalizeNickname(String nickname) {
        return StringUtils.hasText(nickname) ? limit(nickname.trim(), MAX_NICKNAME_LENGTH) : DEFAULT_NICKNAME;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
