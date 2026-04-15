# cs-taihuan

基于 BUFF 账号库存数据，自动筛选更优的 CS 汰换方案。当前实现为 `Spring Boot + Maven + Java 8` 项目。

## 当前能力

1. 通过 `application.yml` 读取 BUFF 基础配置，并由后端托管 BUFF 会话。
2. 前端提供登录中心，可导入、校验、清除 BUFF 会话。
3. 提供 REST API 抓取 BUFF 库存并输出标准化 JSON。
4. 读取本地 `catalog` 元数据，自动计算最优汰换方案。
5. 按手续费后期望利润排序返回候选合同。
6. 前端已拆分为独立的 `Vue 3 + Element Plus` 项目，展示库存看板和 EV 推荐方案。

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
frontend
  ├── src
  ├── index.html
  └── vite.config.js
```

## 配置

在 [src/main/resources/application.yml](/Users/qiaoyu/project/cs-taihuan/src/main/resources/application.yml) 中配置：

```yml
buff:
  base-url: https://buff.163.com
  game: csgo
  page-size: 80
  session:
    storage-path: data/buff-session.json
trade-up:
  sale-fee-rate: 0.025
  max-items-per-rarity: 18
  max-combinations: 25000
```

## 启动

后端：

```bash
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

默认前端开发地址为 `http://localhost:5173`，并通过 Vite 代理访问后端接口。

默认接口：

- `GET /api/buff/session/status`
- `POST /api/buff/session/import`
- `POST /api/buff/session/validate`
- `DELETE /api/buff/session`
- `POST /api/buff/inventory/fetch`
- `POST /api/buff/inventory/load`
- `POST /api/trade-up/optimize`

### 会话托管

推荐通过前端登录中心导入 Cookie，由后端保存到 `data/buff-session.json`。

当前 BUFF 没有稳定公开的第三方网页扫码登录接口，所以这版实现的是：

- 前端负责登录状态显示与会话导入
- 后端负责保存和校验 BUFF session
- 库存抓取默认优先使用后端保存的 session

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

### 使用前端控制台

启动前后端后访问 [http://localhost:5173](http://localhost:5173)，页面包含：

- 库存看板：支持列表/卡片切换，展示当前炼金素材
- 方案列表：按 EV 降序排列推荐方案，点击右侧查看详情
- 登录中心：查看 BUFF 会话状态，导入/校验/清除后端托管会话

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
- Cookie 过期后需要重新从浏览器复制登录态并在前端重新导入。
- 当前优化目标是“手续费后期望利润最大化”，不是“爆金概率最大化”。
- 当前前端基于 Vite，需要较新的 Node.js 版本；若你本机仍是 Node 10，建议升级到 Node 18+ 再运行。
