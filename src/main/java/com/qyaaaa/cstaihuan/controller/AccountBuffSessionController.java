package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.BuffSessionImportRequest;
import com.qyaaaa.cstaihuan.dto.BuffSessionStatusResponse;
import com.qyaaaa.cstaihuan.service.BuffSessionService;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/accounts/{accountId}/session")
public class AccountBuffSessionController {
    private final BuffSessionService buffSessionService;

    public AccountBuffSessionController(BuffSessionService buffSessionService) {
        this.buffSessionService = buffSessionService;
    }

    @GetMapping("/status")
    public BuffSessionStatusResponse status(@PathVariable long accountId) throws Exception {
        return buffSessionService.getStatus(accountId);
    }

    @PostMapping("/import")
    public BuffSessionStatusResponse importSession(@PathVariable long accountId, @Valid @RequestBody BuffSessionImportRequest request) throws Exception {
        return buffSessionService.importSession(accountId, request);
    }

    @PostMapping("/validate")
    public BuffSessionStatusResponse validate(@PathVariable long accountId) throws Exception {
        return buffSessionService.validateSession(accountId);
    }

    @DeleteMapping
    public void clear(@PathVariable long accountId) throws Exception {
        buffSessionService.clearSession(accountId);
    }
}
