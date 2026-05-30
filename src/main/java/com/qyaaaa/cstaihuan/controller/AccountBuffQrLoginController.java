package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.QrLoginCancelRequest;
import com.qyaaaa.cstaihuan.dto.QrLoginStartResponse;
import com.qyaaaa.cstaihuan.dto.QrLoginStatusResponse;
import com.qyaaaa.cstaihuan.service.BuffQrLoginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountId}/login/qrcode")
public class AccountBuffQrLoginController {
    private final BuffQrLoginService buffQrLoginService;

    public AccountBuffQrLoginController(BuffQrLoginService buffQrLoginService) {
        this.buffQrLoginService = buffQrLoginService;
    }

    @PostMapping("/start")
    public QrLoginStartResponse start(@PathVariable long accountId) throws Exception {
        return buffQrLoginService.startLogin(accountId);
    }

    @GetMapping("/status")
    public QrLoginStatusResponse status(@PathVariable long accountId, @RequestParam String sessionId) throws Exception {
        return buffQrLoginService.getStatus(sessionId);
    }

    @PostMapping("/cancel")
    public void cancel(@PathVariable long accountId, @RequestBody QrLoginCancelRequest request) throws Exception {
        buffQrLoginService.cancelLogin(request.getSessionId());
    }
}
