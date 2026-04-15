package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest;
import com.qyaaaa.cstaihuan.dto.BuffSessionStatusResponse;
import com.qyaaaa.cstaihuan.service.BuffSessionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/buff/session")
public class BuffSessionController {
    private final BuffSessionService buffSessionService;

    public BuffSessionController(BuffSessionService buffSessionService) {
        this.buffSessionService = buffSessionService;
    }

    @GetMapping("/status")
    public BuffSessionStatusResponse status() throws Exception {
        return buffSessionService.getStatus();
    }

    @PostMapping("/import")
    public BuffSessionStatusResponse importSession(@RequestBody BuffSessionImportRequest request) throws Exception {
        return buffSessionService.importSession(request);
    }

    @PostMapping("/validate")
    public BuffSessionStatusResponse validate() throws Exception {
        return buffSessionService.validateSession();
    }

    @DeleteMapping
    public void clear() throws Exception {
        buffSessionService.clearSession();
    }
}

