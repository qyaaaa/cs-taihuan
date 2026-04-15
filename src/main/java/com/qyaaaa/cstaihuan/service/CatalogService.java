package com.qyaaaa.cstaihuan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final ObjectMapper objectMapper;

    public CatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CatalogSkin> load(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), new TypeReference<List<CatalogSkin>>() {
        });
    }
}

