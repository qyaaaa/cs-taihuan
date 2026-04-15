package com.qyaaaa.cstaihuan;

import com.qyaaaa.cstaihuan.json.Json;
import com.qyaaaa.cstaihuan.model.BuffItem;
import com.qyaaaa.cstaihuan.model.CatalogSkin;
import com.qyaaaa.cstaihuan.model.TradeUpPlan;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CliApplication {
    public int run(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return 1;
        }

        String command = args[0];
        ArgsParser parser = new ArgsParser(args);
        if ("fetch".equals(command)) {
            return handleFetch(parser);
        }
        if ("optimize".equals(command)) {
            return handleOptimize(parser);
        }
        if ("run".equals(command)) {
            int fetchResult = handleFetch(parser);
            if (fetchResult != 0) {
                return fetchResult;
            }
            return handleOptimize(parser);
        }

        printHelp();
        return 1;
    }

    private int handleFetch(ArgsParser parser) throws Exception {
        String cookie = Config.loadCookie(parser.get("cookie", ""));
        String baseUrl = parser.get("base-url", Config.DEFAULT_BUFF_BASE_URL);
        String game = parser.get("game", "csgo");
        int pageSize = parser.getInt("page-size", 80);
        Integer maxPages = parser.getNullableInt("max-pages");
        String output = parser.require("output");

        BuffClient client = new BuffClient(cookie, baseUrl, 15000);
        List<BuffItem> items = client.fetchInventory(game, pageSize, maxPages);
        Path outputPath = Paths.get(output);
        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        Files.write(outputPath, Json.stringify(BuffItem.toJsonList(items)).getBytes(StandardCharsets.UTF_8));
        System.out.println("Fetched " + items.size() + " items into " + outputPath);
        return 0;
    }

    private int handleOptimize(ArgsParser parser) throws IOException {
        Path inventoryPath = Paths.get(parser.require("inventory"));
        Path catalogPath = Paths.get(parser.require("catalog"));
        int topK = parser.getInt("top-k", 5);
        double saleFeeRate = parser.getDouble("sale-fee-rate", 0.025d);
        int maxItemsPerRarity = parser.getInt("max-items-per-rarity", 18);
        int maxCombinations = parser.getInt("max-combinations", 25000);

        List<BuffItem> inventory = loadInventory(inventoryPath);
        List<CatalogSkin> catalog = Config.loadCatalog(catalogPath);

        TradeUpOptimizer optimizer = new TradeUpOptimizer(catalog, saleFeeRate);
        List<TradeUpPlan> plans = optimizer.findBestContracts(
            optimizer.enrichInventory(inventory),
            topK,
            maxItemsPerRarity,
            maxCombinations
        );
        System.out.println(Renderer.renderPlans(plans));
        return 0;
    }

    private List<BuffItem> loadInventory(Path inventoryPath) throws IOException {
        Object root = Json.parse(new String(Files.readAllBytes(inventoryPath), StandardCharsets.UTF_8));
        if (!(root instanceof List)) {
            throw new IllegalArgumentException("Inventory must be a JSON array.");
        }

        List<?> rows = (List<?>) root;
        List<BuffItem> items = new ArrayList<BuffItem>();
        for (Object row : rows) {
            if (!(row instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) row;
            items.add(BuffItem.fromMap(payload));
        }
        return items;
    }

    private void printHelp() {
        System.out.println("Usage:");
        System.out.println("  fetch --output <file> [--cookie <cookie>] [--game csgo] [--page-size 80] [--max-pages 1]");
        System.out.println("  optimize --inventory <file> --catalog <file> [--top-k 5]");
        System.out.println("  run --output <file> --catalog <file> [--cookie <cookie>] [--game csgo]");
    }
}

