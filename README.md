# cs-taihuan

基于 BUFF 账号库存数据，自动筛选更优的 CS 汰换方案。当前实现为 `Spring Boot + Maven + Java 8` 项目。

## 当前能力

1. 通过 `application.yml` 读取 BUFF 基础配置和 Cookie。
2. 提供 REST API 抓取 BUFF 库存并输出标准化 JSON。
3. 读取本地 `catalog` 元数据，自动计算最优汰换方案。
4. 按手续费后期望利润排序返回候选合同。

## 项目结构

```text
src/main/java/com/qyaaaa/cstaihuan
  ├── config
  ├── controller
  ├── dto
  ├── model
  └── service
src/main/resources
  └── application.yml
```

## 配置

在 [src/main/resources/application.yml](/Users/qiaoyu/project/cs-taihuan/src/main/resources/application.yml) 中配置：

```yml
buff:
  base-url: https://buff.163.com
  cookie: ${BUFF_COOKIE:}
  game: csgo
  page-size: 80
trade-up:
  sale-fee-rate: 0.025
  max-items-per-rarity: 18
  max-combinations: 25000
```

建议仍然把 Cookie 放环境变量里：

```bash
export BUFF_COOKIE='session=...; csrf_token=...'
```

## 启动

```bash
mvn spring-boot:run
```

默认接口：

- `POST /api/buff/inventory/fetch`
- `POST /api/trade-up/optimize`

### 抓取库存

```bash
curl -X POST http://localhost:8080/api/buff/inventory/fetch \
  -H 'Content-Type: application/json' \
  -d '{
    "outputPath": "data/buff_inventory.json",
    "game": "csgo"
  }'
```

### 计算汰换

```bash
curl -X POST http://localhost:8080/api/trade-up/optimize \
  -H 'Content-Type: application/json' \
  -d '{
    "inventoryPath": "data/buff_inventory.json",
    "catalogPath": "data/catalog.json",
    "topK": 3
  }'
```

## Catalog 格式

`catalog` 需要补齐以下字段：

- `name`
- `collection`
- `rarity`
- `min_float`
- `max_float`
- `price`

## 注意事项

- BUFF 接口可能调整字段结构，首次接入建议先调用抓取接口检查输出内容。
- Cookie 过期后需要重新从浏览器复制登录态。
- 当前优化目标是“手续费后期望利润最大化”，不是“爆金概率最大化”。
