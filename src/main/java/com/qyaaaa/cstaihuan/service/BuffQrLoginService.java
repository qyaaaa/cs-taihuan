package com.qyaaaa.cstaihuan.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BuffQrLoginService {
    private static final Logger log = LoggerFactory.getLogger(BuffQrLoginService.class);
    private static final long SESSION_TIMEOUT_MILLIS = 5L * 60L * 1000L; // 5 minutes
    private static final long QR_RENDER_TIMEOUT_MILLIS = 22000L; // max wait for QR image to appear (BUFF render varies ~6-22s; returns as soon as found)
    private static final long QR_POLL_INTERVAL_MILLIS = 250L; // poll interval while waiting for QR
    private static final String BUFF_LOGIN_URL = "https://buff.163.com/account/login";
    // A pre-warmed QR is only handed out while this fresh, so we never serve a BUFF-expired code.
    private static final long WARM_MAX_AGE_MILLIS = 120_000L;
    // How often the background refresher rebuilds the warm session before it ages out.
    private static final long WARM_REFRESH_INTERVAL_MILLIS = 60_000L;

    private final BuffSessionService buffSessionService;
    private final BuffAccountService buffAccountService;
    private final Map<String, QrLoginSession> sessions = new ConcurrentHashMap<>();

    // Playwright (Java) is not thread-safe: a single driver connection backs the whole browser,
    // so concurrent calls from the warm-pool thread and request threads must be serialized.
    private final ReentrantLock pwLock = new ReentrantLock(true);
    // One pre-rendered session waiting to be handed out instantly on the next start request.
    private final AtomicReference<QrLoginSession> warmSession = new AtomicReference<>();
    private ScheduledExecutorService warmExecutor;
    private volatile boolean shuttingDown = false;

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
            // Single thread keeps every warm build serialized w.r.t. each other; the pwLock keeps
            // it serialized w.r.t. request threads. Pre-render one QR now and keep it fresh.
            warmExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "qr-warm-pool");
                t.setDaemon(true);
                return t;
            });
            warmExecutor.execute(this::ensureWarmFresh);
            warmExecutor.scheduleWithFixedDelay(this::ensureWarmFresh,
                WARM_REFRESH_INTERVAL_MILLIS, WARM_REFRESH_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to launch Playwright browser: {}", e.getMessage(), e);
            playwright = null;
            browser = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        if (warmExecutor != null) {
            warmExecutor.shutdownNow();
            try {
                warmExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        QrLoginSession warm = warmSession.getAndSet(null);
        if (warm != null) {
            safeCloseContext(warm);
        }
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

        try {
            // Fast path: hand out a pre-rendered QR if one is fresh, then refill in the background.
            QrLoginSession session = takeWarmSession();
            if (session != null) {
                long now = Instant.now().toEpochMilli();
                session.accountId = accountId;
                session.createdAt = now;
                session.expiresAt = now + SESSION_TIMEOUT_MILLIS;
                log.info("QR login session={} served from warm pool, accountId={}", session.sessionId, accountId);
            } else {
                // Slow path: nothing warm available, build synchronously. captureQrCode already
                // falls back to a QR-box-only screenshot, so we never show a full-page capture.
                session = buildSession(accountId, UUID.randomUUID().toString());
            }

            sessions.put(session.sessionId, session);
            log.info("QR login session={} created, status={}, accountId={}, expiresAt={}",
                session.sessionId, session.status, session.accountId, Long.valueOf(session.expiresAt));

            // Top the warm pool back up for the next request.
            scheduleWarm();

            return new QrLoginStartResponse(
                session.sessionId,
                session.qrcodeBase64,
                session.status,
                "SUCCESS".equals(session.status) ? "已检测到登录状态，正在保存会话。" : "请使用网易 BUFF App 扫描二维码登录。",
                session.expiresAt,
                session.accountId
            );
        } catch (Exception e) {
            log.error("Failed to start QR login: {}", e.getMessage(), e);
            scheduleWarm();
            // Surface a clean, actionable message (handled -> 400) instead of a raw 500 stack.
            throw new IllegalArgumentException("BUFF 登录页暂时无法访问（可能被风控限流），请稍候重试，或改用「手动导入」。");
        }
    }

    // Navigates to the BUFF login page under the Playwright lock, retrying once on transient
    // connection failures (BUFF intermittently resets the connection under load).
    private void navigateWithRetry(Page page, String sessionId) {
        try {
            page.navigate(BUFF_LOGIN_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.COMMIT)
                .setTimeout(15000));
        } catch (RuntimeException first) {
            log.warn("QR login session={} first navigation failed ({}), retrying once...",
                sessionId, first.getMessage());
            try {
                Thread.sleep(800);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            page.navigate(BUFF_LOGIN_URL, new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.COMMIT)
                .setTimeout(15000));
        }
    }

    // Atomically takes the warm session if it is still fresh, usable, and on the login page.
    private QrLoginSession takeWarmSession() {
        QrLoginSession warm = warmSession.getAndSet(null);
        if (warm == null) {
            return null;
        }
        long now = Instant.now().toEpochMilli();
        boolean tooOld = (now - warm.createdAt) > WARM_MAX_AGE_MILLIS;
        boolean unusable = warm.qrcodeBase64 == null || isPageClosedLocked(warm.page);
        if (tooOld || unusable) {
            safeCloseContext(warm);
            return null;
        }
        return warm;
    }

    /**
     * Builds a fresh login session with the QR already captured. Shared by the synchronous slow
     * path and the warm-pool pre-render. All Playwright calls run under {@link #pwLock}.
     */
    private QrLoginSession buildSession(Long accountId, String sessionId) {
        BrowserContext context = null;
        Page page;
        String status = "PENDING";
        Long resolvedAccountId = accountId;
        String cookieStrForSuccess = null;

        pwLock.lock();
        try {
            context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("zh-CN"));
            page = context.newPage();

            // Return as soon as the document response commits (COMMIT) instead of waiting for
            // DOMContentLoaded/NETWORKIDLE — BUFF's heavy blocking scripts make those wait ~15s.
            // BUFF occasionally drops the connection (ERR_CONNECTION_CLOSED, e.g. transient
            // throttling); retry once before giving up so a blip doesn't surface as an error.
            log.info("Building QR login session={}, navigating to BUFF login page...", sessionId);
            navigateWithRetry(page, sessionId);

            String currentUrl = page.url().toLowerCase();
            // If we somehow land already logged in (existing cookies), BUFF redirects away from the
            // login page. Detect it under the lock; persist cookies after releasing the lock.
            if (!currentUrl.contains("/account/login")) {
                List<Cookie> cookies = context.cookies();
                if (hasLoginCookie(cookies)) {
                    cookieStrForSuccess = buildCookieString(cookies);
                    status = "SUCCESS";
                }
            }
        } catch (Exception e) {
            if (context != null) {
                try { context.close(); } catch (Exception ex) { /* ignore */ }
            }
            pwLock.unlock();
            throw new IllegalStateException("启动扫码登录失败: " + e.getMessage(), e);
        }
        pwLock.unlock();

        // captureQrCode locks per poll internally so it doesn't block status polls while it waits.
        String qrcodeBase64 = null;
        if (!"SUCCESS".equals(status)) {
            qrcodeBase64 = captureQrCode(page, sessionId);
        }

        long now = Instant.now().toEpochMilli();
        QrLoginSession session = new QrLoginSession();
        session.sessionId = sessionId;
        session.browserContext = context;
        session.page = page;
        session.accountId = resolvedAccountId;
        session.status = status;
        session.qrcodeBase64 = qrcodeBase64;
        session.createdAt = now;
        session.expiresAt = now + SESSION_TIMEOUT_MILLIS;

        // DB writes for the rare "already logged in" case happen outside the Playwright lock.
        if ("SUCCESS".equals(status) && cookieStrForSuccess != null) {
            try {
                session.accountId = Long.valueOf(resolveOrCreateAccountId(accountId));
                importCookies(cookieStrForSuccess, session.accountId);
            } catch (Exception e) {
                log.warn("QR login session={} pre-authenticated cookie import failed: {}", sessionId, e.getMessage());
            }
        }
        return session;
    }

    // Submits a warm refresh to the single warm thread (no-op if one is already fresh).
    private void scheduleWarm() {
        if (shuttingDown || warmExecutor == null || !isAvailable()) {
            return;
        }
        try {
            warmExecutor.execute(this::ensureWarmFresh);
        } catch (Exception e) {
            // Executor shutting down; ignore.
        }
    }

    // Runs only on the single warm thread: rebuilds the warm session when missing or stale.
    private void ensureWarmFresh() {
        if (shuttingDown || !isAvailable()) {
            return;
        }
        try {
            QrLoginSession cur = warmSession.get();
            long now = Instant.now().toEpochMilli();
            boolean fresh = cur != null
                && cur.qrcodeBase64 != null
                && (now - cur.createdAt) < WARM_MAX_AGE_MILLIS
                && !isPageClosedLocked(cur.page);
            if (fresh) {
                return;
            }
            QrLoginSession old = warmSession.getAndSet(null);
            if (old != null) {
                safeCloseContext(old);
            }
            QrLoginSession built = buildSession(null, UUID.randomUUID().toString());
            if (shuttingDown || built.qrcodeBase64 == null) {
                safeCloseContext(built);
                return;
            }
            warmSession.set(built);
            log.info("Warm QR session={} ready", built.sessionId);
        } catch (Exception e) {
            log.warn("Failed to refresh warm QR session: {}", e.getMessage());
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

        // Read all page state in a single locked block; do DB work afterwards outside the lock.
        boolean onLoginPage;
        List<Cookie> cookies = null;
        boolean qrInvalid = false;
        boolean scanFinished = false;
        pwLock.lock();
        try {
            Page page = session.page;
            if (page == null || page.isClosed()) {
                pwLock.unlock();
                cleanupSession(sessionId);
                return new QrLoginStatusResponse(sessionId, "FAILED", "浏览器页面已关闭。", false, false);
            }
            String currentUrl = page.url().toLowerCase();
            onLoginPage = currentUrl.contains("/account/login");
            if (!onLoginPage) {
                cookies = session.browserContext.cookies();
            } else {
                qrInvalid = isElementVisibleLocked(page, "#qrcode_invalid");
                scanFinished = isElementVisibleLocked(page, "#qrcode_scan_finish");
            }
        } catch (Exception e) {
            pwLock.unlock();
            log.error("Error polling QR login session={}: {}", sessionId, e.getMessage(), e);
            return new QrLoginStatusResponse(sessionId, "PENDING", "轮询中...", false, false);
        }
        pwLock.unlock();

        // Login complete: BUFF redirects away from /account/login once the scan is confirmed.
        if (!onLoginPage) {
            if (cookies == null || !hasLoginCookie(cookies)) {
                // Redirected but no session cookie yet; keep waiting one more poll.
                return new QrLoginStatusResponse(sessionId, session.status, "登录跳转中，请稍候...", false, false);
            }
            log.info("QR login session={} left login page, extracting cookies...", sessionId);
            long targetAccountId = resolveOrCreateAccountId(session.accountId);
            session.accountId = Long.valueOf(targetAccountId);
            importCookies(buildCookieString(cookies), session.accountId);
            session.status = "SUCCESS";
            log.info("QR login session={} succeeded, cookies saved to account={}", sessionId, Long.valueOf(targetAccountId));
            safeCloseContext(session);
            return new QrLoginStatusResponse(sessionId, "SUCCESS", "登录成功，会话已保存。", true, true, Long.valueOf(targetAccountId));
        }

        if (qrInvalid) {
            session.status = "EXPIRED";
            return new QrLoginStatusResponse(sessionId, "EXPIRED", "二维码已过期，请重新获取。", false, false);
        }
        if (scanFinished) {
            if (!"CONFIRMED".equals(session.status)) {
                session.status = "CONFIRMED";
                log.info("QR login session={} status updated to CONFIRMED (scan finished)", sessionId);
            }
            return new QrLoginStatusResponse(sessionId, "CONFIRMED", "已扫码，请在网易 BUFF App 中确认登录。", false, false);
        }
        return new QrLoginStatusResponse(sessionId, session.status, "等待扫码中，请使用网易 BUFF App 扫描二维码。", false, false);
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

    // Returns the base64 payload (without the data URL prefix) of BUFF's login QR code. Polls under
    // the Playwright lock per attempt and sleeps outside it, so status polls can interleave.
    private String captureQrCode(Page page, String sessionId) {
        long deadline = Instant.now().toEpochMilli() + QR_RENDER_TIMEOUT_MILLIS;
        while (Instant.now().toEpochMilli() < deadline) {
            String qr = tryReadQrCode(page);
            if (qr != null) {
                log.info("QR login session={}, QR data URL captured", sessionId);
                return qr;
            }
            try {
                Thread.sleep(QR_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // The crisp data:image never showed up in time. Rather than capturing the whole page,
        // screenshot just the QR box so the user still sees only the QR (slightly larger image).
        String boxShot = captureQrBoxScreenshotLocked(page);
        if (boxShot != null) {
            log.info("QR login session={}, QR captured via box element screenshot (data URL timed out)", sessionId);
            return boxShot;
        }
        log.info("QR login session={}, QR not found after waiting", sessionId);
        return null;
    }

    // One locked attempt to read the QR data URL from any known selector; null if not ready.
    private String tryReadQrCode(Page page) {
        pwLock.lock();
        try {
            for (String selector : QR_SELECTORS) {
                try {
                    ElementHandle qrElement = page.querySelector(selector);
                    if (qrElement != null && qrElement.isVisible()) {
                        String src = qrElement.getAttribute("src");
                        if (src != null && src.startsWith("data:image")) {
                            int comma = src.indexOf(',');
                            if (comma > 0 && comma < src.length() - 1) {
                                return src.substring(comma + 1);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("QR selector {} failed: {}", selector, e.getMessage());
                }
            }
            return null;
        } finally {
            pwLock.unlock();
        }
    }

    private boolean isElementVisibleLocked(Page page, String selector) {
        pwLock.lock();
        try {
            ElementHandle el = page.querySelector(selector);
            return el != null && el.isVisible();
        } catch (Exception e) {
            return false;
        } finally {
            pwLock.unlock();
        }
    }

    private boolean isPageClosedLocked(Page page) {
        if (page == null) {
            return true;
        }
        pwLock.lock();
        try {
            return page.isClosed();
        } catch (Exception e) {
            return true;
        } finally {
            pwLock.unlock();
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

    // QR-box candidates for the element-screenshot fallback — the white login card holding the QR.
    private static final String[] QR_BOX_SELECTORS = {
        "#qr_code_box",
        "#login_qr_code_content",
        ".login-qrcode",
    };

    // Screenshots only the QR card element (not the full page), returned as base64 PNG.
    private String captureQrBoxScreenshotLocked(Page page) {
        pwLock.lock();
        try {
            for (String selector : QR_BOX_SELECTORS) {
                ElementHandle box = page.querySelector(selector);
                if (box != null && box.isVisible()) {
                    byte[] png = box.screenshot(new ElementHandle.ScreenshotOptions().setType(ScreenshotType.PNG));
                    return Base64.getEncoder().encodeToString(png);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to capture QR box screenshot: {}", e.getMessage());
            return null;
        } finally {
            pwLock.unlock();
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
        pwLock.lock();
        try {
            if (session.page != null && !session.page.isClosed()) {
                session.page.close();
            }
        } catch (Exception e) {
            log.debug("Error closing page: {}", e.getMessage());
        } finally {
            pwLock.unlock();
        }
        pwLock.lock();
        try {
            if (session.browserContext != null) {
                session.browserContext.close();
            }
        } catch (Exception e) {
            log.debug("Error closing browser context: {}", e.getMessage());
        } finally {
            pwLock.unlock();
        }
    }

    private static class QrLoginSession {
        String sessionId;
        BrowserContext browserContext;
        Page page;
        Long accountId;
        String status;
        String qrcodeBase64;
        long createdAt;
        long expiresAt;
    }
}
