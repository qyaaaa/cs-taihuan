package com.qyaaaa.cstaihuan.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ScreenshotType;
import com.qyaaaa.cstaihuan.dto.QrLoginStartResponse;
import com.qyaaaa.cstaihuan.dto.QrLoginStatusResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BuffQrLoginService {
    private static final Logger log = LoggerFactory.getLogger(BuffQrLoginService.class);
    private static final long SESSION_TIMEOUT_MILLIS = 5L * 60L * 1000L; // 5 minutes
    private static final long QR_RENDER_TIMEOUT_MILLIS = 8000L; // max wait for QR image to appear
    private static final long QR_POLL_INTERVAL_MILLIS = 500L; // poll interval while waiting for QR
    private static final String BUFF_LOGIN_URL = "https://buff.163.com/account/login";

    private final BuffSessionService buffSessionService;
    private final BuffAccountService buffAccountService;
    private final Map<String, QrLoginSession> sessions = new ConcurrentHashMap<>();

    private Playwright playwright;
    private Browser browser;

    public BuffQrLoginService(BuffSessionService buffSessionService, BuffAccountService buffAccountService) {
        this.buffSessionService = buffSessionService;
        this.buffAccountService = buffAccountService;
    }

    @PostConstruct
    public void init() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList(
                    "--no-sandbox",
                    "--disable-setuid-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-gpu"
                )));
            log.info("Playwright browser launched successfully");
        } catch (Exception e) {
            log.error("Failed to launch Playwright browser: {}", e.getMessage(), e);
            playwright = null;
            browser = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        cleanupAllSessions();
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                log.warn("Error closing browser: {}", e.getMessage());
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception e) {
                log.warn("Error closing playwright: {}", e.getMessage());
            }
        }
    }

    public boolean isAvailable() {
        return browser != null && playwright != null;
    }

    public QrLoginStartResponse startLogin() throws IOException {
        return startLoginInternal(null);
    }

    public QrLoginStartResponse startLogin(long accountId) throws IOException {
        return startLoginInternal(accountId);
    }

    private QrLoginStartResponse startLoginInternal(Long accountId) throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("浏览器环境未就绪，请检查服务器 Chromium 安装。");
        }

        // Clean up any existing session for this account
        cleanupAccountSession(accountId);
        cleanupExpiredSessions();

        String sessionId = UUID.randomUUID().toString();
        String qrcodeBase64;
        String status = "PENDING";

        BrowserContext context = null;
        Page page = null;

        try {
            context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("zh-CN"));
            page = context.newPage();

            // Open BUFF's own login page. Its default panel is the "扫码登录" (scan with the
            // NetEase BUFF App) flow; the QR lives in #qr_code_box as a data:image base64 src.
            log.info("Starting QR login session={}, navigating to BUFF login page...", sessionId);
            page.navigate(BUFF_LOGIN_URL);
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));

            String currentUrl = page.url().toLowerCase();
            log.info("QR login session={}, current URL after navigation: {}", sessionId, currentUrl);

            // If we somehow land already logged in (existing cookies), BUFF redirects away
            // from the login page. Treat that as success.
            Long resolvedAccountId = accountId;
            if (!currentUrl.contains("/account/login")) {
                List<Cookie> cookies = context.cookies();
                String cookieStr = buildCookieString(cookies);
                if (hasLoginCookie(cookies)) {
                    resolvedAccountId = Long.valueOf(resolveOrCreateAccountId(accountId));
                    importCookies(cookieStr, resolvedAccountId);
                    status = "SUCCESS";
                }
            }

            // Capture BUFF's QR code data URL (no screenshot needed).
            qrcodeBase64 = null;
            if (!"SUCCESS".equals(status)) {
                qrcodeBase64 = captureQrCode(page, sessionId);
                if (qrcodeBase64 == null) {
                    // Fallback: screenshot just the QR area, then the whole login panel.
                    log.warn("QR login session={}, QR data URL not found, falling back to screenshot", sessionId);
                    qrcodeBase64 = capturePageScreenshot(page);
                }
            }

            long expiresAt = Instant.now().toEpochMilli() + SESSION_TIMEOUT_MILLIS;

            QrLoginSession session = new QrLoginSession();
            session.sessionId = sessionId;
            session.browserContext = context;
            session.page = page;
            session.accountId = resolvedAccountId;
            session.status = status;
            session.createdAt = Instant.now().toEpochMilli();
            session.expiresAt = expiresAt;

            sessions.put(sessionId, session);

            log.info("QR login session={} created, status={}, accountId={}, expiresAt={}", sessionId, status, resolvedAccountId, expiresAt);

            return new QrLoginStartResponse(
                sessionId,
                qrcodeBase64,
                status,
                status.equals("SUCCESS") ? "已检测到登录状态，正在保存会话。" : "请使用网易 BUFF App 扫描二维码登录。",
                expiresAt,
                resolvedAccountId
            );

        } catch (Exception e) {
            log.error("Failed to start QR login session={}: {}", sessionId, e.getMessage(), e);
            if (context != null) {
                try { context.close(); } catch (Exception ex) { /* ignore */ }
            }
            throw new IllegalStateException("启动扫码登录失败: " + e.getMessage(), e);
        }
    }

    public QrLoginStatusResponse getStatus(String sessionId) throws IOException {
        QrLoginSession session = sessions.get(sessionId);
        if (session == null) {
            return new QrLoginStatusResponse(sessionId, "EXPIRED", "登录会话已过期或不存在。", false, false);
        }

        // Check timeout
        if (Instant.now().toEpochMilli() > session.expiresAt) {
            cleanupSession(sessionId);
            return new QrLoginStatusResponse(sessionId, "EXPIRED", "二维码已过期，请重新获取。", false, false);
        }

        if ("SUCCESS".equals(session.status)) {
            // Already succeeded
            return new QrLoginStatusResponse(sessionId, "SUCCESS", "登录成功。", true, true, session.accountId);
        }

        try {
            Page page = session.page;
            if (page == null || page.isClosed()) {
                cleanupSession(sessionId);
                return new QrLoginStatusResponse(sessionId, "FAILED", "浏览器页面已关闭。", false, false);
            }

            String currentUrl = page.url().toLowerCase();
            log.debug("QR login session={}, polling URL: {}", sessionId, currentUrl);

            // Login complete: BUFF redirects away from /account/login once the scan is
            // confirmed in the app. Extract the authenticated cookies and persist them.
            if (!currentUrl.contains("/account/login")) {
                log.info("QR login session={} left login page, extracting cookies...", sessionId);
                List<Cookie> cookies = session.browserContext.cookies();
                if (!hasLoginCookie(cookies)) {
                    // Redirected but no session cookie yet; keep waiting one more poll.
                    return new QrLoginStatusResponse(sessionId, session.status, "登录跳转中，请稍候...", false, false);
                }
                // For a pending session (no account yet) this creates the account on success.
                long targetAccountId = resolveOrCreateAccountId(session.accountId);
                session.accountId = Long.valueOf(targetAccountId);
                importCookies(buildCookieString(cookies), session.accountId);
                session.status = "SUCCESS";
                log.info("QR login session={} succeeded, cookies saved to account={}", sessionId, targetAccountId);
                safeCloseContext(session);
                return new QrLoginStatusResponse(sessionId, "SUCCESS", "登录成功，会话已保存。", true, true, Long.valueOf(targetAccountId));
            }

            // Still on the login page: read BUFF's own QR status indicators.
            if (isElementVisible(page, "#qrcode_invalid")) {
                session.status = "EXPIRED";
                return new QrLoginStatusResponse(sessionId, "EXPIRED", "二维码已过期，请重新获取。", false, false);
            }
            if (isElementVisible(page, "#qrcode_scan_finish")) {
                if (!"CONFIRMED".equals(session.status)) {
                    session.status = "CONFIRMED";
                    log.info("QR login session={} status updated to CONFIRMED (scan finished)", sessionId);
                }
                return new QrLoginStatusResponse(sessionId, "CONFIRMED", "已扫码，请在网易 BUFF App 中确认登录。", false, false);
            }

            return new QrLoginStatusResponse(sessionId, session.status, "等待扫码中，请使用网易 BUFF App 扫描二维码。", false, false);

        } catch (Exception e) {
            log.error("Error polling QR login session={}: {}", sessionId, e.getMessage(), e);
            return new QrLoginStatusResponse(sessionId, "PENDING", "轮询中...", false, false);
        }
    }

    public void cancelLogin(String sessionId) {
        cleanupSession(sessionId);
        log.info("QR login session={} cancelled", sessionId);
    }

    // BUFF renders its login QR as an <img> with a data:image base64 src inside #qr_code_box.
    private static final String[] QR_SELECTORS = {
        "#qr_code_box img",
        "#login_qr_code_content img",
        ".login-qrcode .qrcode-area img",
        ".login-qrcode img",
    };

    // Returns the base64 payload (without the data URL prefix) of BUFF's login QR code.
    private String captureQrCode(Page page, String sessionId) {
        long deadline = Instant.now().toEpochMilli() + QR_RENDER_TIMEOUT_MILLIS;
        while (Instant.now().toEpochMilli() < deadline) {
            for (String selector : QR_SELECTORS) {
                try {
                    ElementHandle qrElement = page.querySelector(selector);
                    if (qrElement != null && qrElement.isVisible()) {
                        String src = qrElement.getAttribute("src");
                        if (src != null && src.startsWith("data:image")) {
                            int comma = src.indexOf(',');
                            if (comma > 0 && comma < src.length() - 1) {
                                log.info("QR login session={}, QR data URL captured via selector: {}", sessionId, selector);
                                return src.substring(comma + 1);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("QR selector {} failed: {}", selector, e.getMessage());
                }
            }
            try {
                Thread.sleep(QR_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("QR login session={}, QR data URL not found after waiting", sessionId);
        return null;
    }

    private boolean isElementVisible(Page page, String selector) {
        try {
            ElementHandle el = page.querySelector(selector);
            return el != null && el.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    // BUFF sets a non-empty `session` cookie only after a successful login.
    private boolean hasLoginCookie(List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            if ("session".equalsIgnoreCase(cookie.name) && cookie.value != null && !cookie.value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void importCookies(String cookieStr, Long accountId) throws IOException {
        com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest importReq =
            new com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest();
        importReq.setCookie(cookieStr);
        importReq.setSource("qrcode");
        buffSessionService.importSession(
            accountId != null ? accountId : buffAccountService.resolveDefaultAccountId(),
            importReq
        );
    }

    // Returns the given account id, or creates a brand-new local account when none was bound
    // to this QR session yet (the "新增 → 扫码成功后才建账号" flow).
    private long resolveOrCreateAccountId(Long accountId) {
        if (accountId != null) {
            return accountId.longValue();
        }
        com.qyaaaa.cstaihuan.dto.BuffAccountRequest request = new com.qyaaaa.cstaihuan.dto.BuffAccountRequest();
        request.setNickname("账号 " + (buffAccountService.listAccounts().size() + 1));
        long newId = buffAccountService.createAccount(request).getId();
        log.info("Created new account={} for pending QR login", newId);
        return newId;
    }

    private String capturePageScreenshot(Page page) {
        try {
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setType(ScreenshotType.PNG)
                .setFullPage(false));
            return Base64.getEncoder().encodeToString(screenshot);
        } catch (Exception e) {
            log.warn("Failed to capture page screenshot: {}", e.getMessage());
            return null;
        }
    }

    private String buildCookieString(List<Cookie> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Cookie cookie : cookies) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(cookie.name).append('=').append(cookie.value);
        }
        return sb.toString();
    }

    private void cleanupAccountSession(Long accountId) {
        if (accountId == null) {
            return;
        }
        Iterator<Map.Entry<String, QrLoginSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, QrLoginSession> entry = it.next();
            QrLoginSession session = entry.getValue();
            if (accountId.equals(session.accountId)) {
                safeCloseContext(session);
                it.remove();
                log.info("Cleaned up existing QR login session={} for account={}", session.sessionId, accountId);
            }
        }
    }

    private void cleanupExpiredSessions() {
        long now = Instant.now().toEpochMilli();
        Iterator<Map.Entry<String, QrLoginSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, QrLoginSession> entry = it.next();
            QrLoginSession session = entry.getValue();
            if (now > session.expiresAt) {
                safeCloseContext(session);
                it.remove();
                log.info("Cleaned up expired QR login session={}", session.sessionId);
            }
        }
    }

    private void cleanupSession(String sessionId) {
        QrLoginSession session = sessions.remove(sessionId);
        if (session != null) {
            safeCloseContext(session);
        }
    }

    private void cleanupAllSessions() {
        for (QrLoginSession session : sessions.values()) {
            safeCloseContext(session);
        }
        sessions.clear();
    }

    private void safeCloseContext(QrLoginSession session) {
        try {
            if (session.page != null && !session.page.isClosed()) {
                session.page.close();
            }
        } catch (Exception e) {
            log.debug("Error closing page: {}", e.getMessage());
        }
        try {
            if (session.browserContext != null) {
                session.browserContext.close();
            }
        } catch (Exception e) {
            log.debug("Error closing browser context: {}", e.getMessage());
        }
    }

    private static class QrLoginSession {
        String sessionId;
        BrowserContext browserContext;
        Page page;
        Long accountId;
        String status;
        long createdAt;
        long expiresAt;
        String lastPollUrl;
    }
}
