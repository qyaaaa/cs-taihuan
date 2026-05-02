package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.dto.BuffAccountRequest;
import com.qyaaaa.cstaihuan.model.BuffAccount;
import com.qyaaaa.cstaihuan.service.BuffAccountService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/accounts")
public class BuffAccountController {
    private final BuffAccountService buffAccountService;

    public BuffAccountController(BuffAccountService buffAccountService) {
        this.buffAccountService = buffAccountService;
    }

    @GetMapping
    public List<BuffAccount> listAccounts() {
        return buffAccountService.listAccounts();
    }

    @PostMapping
    public BuffAccount createAccount(@Valid @RequestBody(required = false) BuffAccountRequest request) {
        return buffAccountService.createAccount(request);
    }

    @PutMapping("/{accountId}")
    public BuffAccount updateAccount(@PathVariable long accountId, @Valid @RequestBody BuffAccountRequest request) {
        return buffAccountService.updateAccount(accountId, request);
    }

    @DeleteMapping("/{accountId}")
    public void deleteAccount(@PathVariable long accountId) {
        buffAccountService.deleteAccount(accountId);
    }
}
