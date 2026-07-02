package com.qyaaaa.cstaihuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qyaaaa.cstaihuan.dto.BuffAccountRequest;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import java.util.List;
import java.util.Optional;

public interface BuffAccountService extends IService<BuffAccount> {
    List<BuffAccount> listAccounts();

    BuffAccount createAccount(BuffAccountRequest request);

    BuffAccount updateAccount(long accountId, BuffAccountRequest request);

    void deleteAccount(long accountId);

    long resolveDefaultAccountId();

    BuffAccount requireAccount(long accountId);

    void updateSessionSummary(long accountId, String maskedCookie, String status, String lastValidatedAt);

    void updateImportedIdentity(long accountId, String nickname, String buffUserId);

    Optional<BuffAccount> findOtherAccountByBuffUserId(String buffUserId, long excludeAccountId);
}
