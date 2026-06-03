package com.qyaaaa.cstaihuan.controller;

import com.qyaaaa.cstaihuan.service.SkinFloatRangeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skin-float-range")
public class SkinFloatRangeController {
    private final SkinFloatRangeService skinFloatRangeService;

    public SkinFloatRangeController(SkinFloatRangeService skinFloatRangeService) {
        this.skinFloatRangeService = skinFloatRangeService;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("count", Integer.valueOf(skinFloatRangeService.count()));
        return result;
    }

    @GetMapping("/collections")
    public List<Map<String, Object>> collections() {
        return skinFloatRangeService.listCollectionBrowser();
    }

    @PostMapping("/import")
    public Map<String, Object> reimport() {
        int imported = skinFloatRangeService.importFromSnapshot();
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("imported", Integer.valueOf(imported));
        return result;
    }
}
