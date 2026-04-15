package com.qyaaaa.cstaihuan.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qyaaaa.cstaihuan.model.BuffItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InventoryFileService {
    private final ObjectMapper objectMapper;

    public InventoryFileService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<BuffItem> load(Path path) throws IOException {
        return objectMapper.readValue(path.toFile(), new TypeReference<List<BuffItem>>() {
        });
    }

    public void save(Path path, List<BuffItem> items) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), items);
    }
}

