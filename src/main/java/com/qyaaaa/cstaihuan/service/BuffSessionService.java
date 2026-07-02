package com.qyaaaa.cstaihuan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest;
import com.qyaaaa.cstaihuan.dto.BuffSessionStatusResponse;
import com.qyaaaa.cstaihuan.model.BuffSession;
import java.io.IOException;

public interface BuffSessionService extends IService<BuffSession> {
    BuffSessionStatusResponse getStatus() throws IOException;

    BuffSessionStatusResponse getStatus(long accountId) throws IOException;

    BuffSessionStatusResponse importSession(BuffSessionImportRequest request) throws IOException;

    BuffSessionStatusResponse importSession(long accountId, BuffSessionImportRequest request) throws IOException;

    BuffSessionStatusResponse validateSession() throws IOException;

    BuffSessionStatusResponse validateSession(long accountId) throws IOException;

    void clearSession() throws IOException;

    void clearSession(long accountId) throws IOException;

    String resolveCookie(String requestCookie) throws IOException;

    String resolveCookie(long accountId, String requestCookie) throws IOException;
}
