package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.QrLoginCancelRequest;
import com.qyaaaa.cstaihuan.dto.QrLoginStartResponse;
import com.qyaaaa.cstaihuan.dto.QrLoginStatusResponse;
import com.qyaaaa.cstaihuan.service.BuffQrLoginService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buff/login/qrcode")
public class BuffQrLoginController {
    private final BuffQrLoginService buffQrLoginService;

    public BuffQrLoginController(BuffQrLoginService buffQrLoginService) {
        this.buffQrLoginService = buffQrLoginService;
    }

    @PostMapping("/start")
    public QrLoginStartResponse start() throws Exception {
        return buffQrLoginService.startLogin();
    }

    @GetMapping("/status")
    public QrLoginStatusResponse status(@RequestParam String sessionId) throws Exception {
        return buffQrLoginService.getStatus(sessionId);
    }

    @PostMapping("/cancel")
    public void cancel(@RequestBody QrLoginCancelRequest request) throws Exception {
        buffQrLoginService.cancelLogin(request.getSessionId());
    }
}
