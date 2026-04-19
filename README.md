# cs-taihuan

基于 BUFF 账号库存数据，自动筛选更优的 CS 汰换方案。当前实现为 `Spring Boot + Maven + Java 8` 后端，前端为独立的 `Vue 3 + Element Plus` 项目。

## 功能概览

- BUFF 会话由后端托管，前端可导入、校验、清除登录态
- 抓取 BUFF 库存时会输出本地 JSON，并把 `tags.category.internal_name` 以 `weapon_` 开头的武器类物品写入 MySQL
- MySQL 库表结构由 Flyway 管理
- 数据库存储图片、中文名称、磨损阶段、磨损度、收藏品、品质等展示字段
- 汰换所需的 catalog 目录数据也由 MySQL 托管，并可基于库存快照递归补抓 BUFF 市场目录
- 前端库存看板按单件展示武器库存，支持后端分页读取数据库结果
- 汰换方案按手续费后期望利润排序返回
- BUFF 限流 `429` 时，优先回退到最近一次数据库快照

## 项目结构

```text
src/main/java/com/qyaaaa/cstaihuan
  ├── config
  ├── controller
  ├── dto
  ├── exception
  ├── model
  └── service
src/main/resources
  ├── application.yml
  └── db/migration
frontend
  ├── src
  ├── index.html
  └── vite.config.js
```

## 快速开始

后端启动：

```bash
mvn spring-boot:run
```

前端启动：

```bash
cd frontend
npm install
npm run dev
```

默认前端地址是 [http://localhost:5173](http://localhost:5173)，通过 Vite 代理访问后端接口。

## 配置

配置文件在 [application.yml](/Users/qiaoyu/project/cs-taihuan/src/main/resources/application.yml)。

```yml
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    url: jdbc:mysql://mc-mysql:3306/cs_taihuan?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowMultiQueries=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: abc123_
  flyway:
    enabled: true
    user: root
    password: abc123_
    url: jdbc:mysql://mc-mysql:3306/cs_taihuan?characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=false&allowMultiQueries=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=Asia/Shanghai
    table: co_flyway_schema_history
    baseline-version: 0
    baseline-on-migrate: true

buff:
  base-url: https://buff.163.com
  game: csgo
  page-size: 80
  fetch-cooldown-seconds: 180
  session:
    storage-path: data/buff-session.json

trade-up:
  sale-fee-rate: 0.025
  max-items-per-rarity: 18
  max-combinations: 25000
```

抓取相关数据默认会落到：

- 本地 JSON：`data/buff_inventory.json`
- MySQL 数据库：`cs_taihuan`
- Flyway 历史表：`co_flyway_schema_history`
- 本地会话文件：`data/buff-session.json`
- Catalog 表：`catalog_skin`

## 登录与库存同步

推荐通过前端登录中心导入浏览器里的 BUFF Cookie，由后端统一托管。

当前登录方案是：

- 前端负责展示登录状态和导入 Cookie
- 后端负责保存、校验和复用 BUFF session
- 主动抓取库存时默认会请求 BUFF 校验最新数据
- 仅在显式关闭 `forceRefresh` 时，才优先复用最近快照
- 若 BUFF 返回 `429 Too Many Requests`，优先回退到最近一次数据库快照

BUFF 没有稳定公开的第三方网页扫码接口，所以当前不是 OAuth/扫码直连模式。

## 常用接口

会话相关：

- `GET /api/buff/session/status`
- `POST /api/buff/session/import`
- `POST /api/buff/session/validate`
- `DELETE /api/buff/session`

库存相关：

- `POST /api/buff/inventory/fetch`
- `POST /api/buff/inventory/load`
- `POST /api/buff/inventory/page`

方案相关：

- `POST /api/catalog/sync`
- `POST /api/trade-up/optimize`
- `POST /api/trade-up/next-tier`
- `POST /api/trade-up/next-tier/persist`

### 抓取库存

```bash
curl -X POST http://localhost:8080/api/buff/inventory/fetch \
  -H 'Content-Type: application/json' \
  -d '{
    "outputPath": "data/buff_inventory.json",
    "game": "csgo",
    "forceRefresh": true
  }'
```

返回里会包含这些关键信息：

- 是否命中缓存
- 数据来源：`CACHE`、`REUSED`、`REMOTE`
- 当前快照 `snapshotId`

### 数据库分页读取库存

```bash
curl -X POST http://localhost:8080/api/buff/inventory/page \
  -H 'Content-Type: application/json' \
  -d '{
    "page": 1,
    "pageSize": 50
  }'
```

这个接口返回的是数据库里已保存的武器类库存分页结果，前端库存看板默认就用它。

### 从 BUFF 同步 Catalog 到数据库

```bash
curl -X POST http://localhost:8080/api/catalog/sync \
  -H 'Content-Type: application/json' \
  -d '{
    "snapshotId": 123
  }'
```

这个接口会：

- 读取数据库里的库存快照，提取其中的 `goods_id`
- 复用后端托管的 BUFF 会话，请求 `goods/info`
- 递归跟进 BUFF 返回的 `relative_goods`
- 整批覆盖写入 `catalog_skin` 表

运行时的方案计算和关联档位计算只读取数据库里的 catalog，不再依赖本地 `catalog.json`。

### 计算汰换

```bash
curl -X POST http://localhost:8080/api/trade-up/optimize \
  -H 'Content-Type: application/json' \
  -d '{
    "snapshotId": 123,
    "topK": 3
  }'
```

## 前端页面

启动前后端后访问 [http://localhost:5173](http://localhost:5173)，页面包含：

- 登录中心：查看 BUFF 会话状态，导入、校验、清除会话
- 库存看板：从数据库分页读取武器类库存，逐件展示图片、中文名、品质、收藏品、磨损阶段和磨损度
- 方案列表：按 EV 降序展示推荐方案，点击可查看合同详情
- Catalog 同步：基于最近一次库存快照和 BUFF 会话，同步 `catalog_skin`

## 注意事项

- 首次接入建议先抓一次库存，确认 BUFF 返回字段结构没有变化
- 首次使用方案计算前，需要先同步一次 `catalog_skin`
- 旧快照不会自动补齐新字段；字段逻辑调整后，需要重新抓取库存
- Cookie 过期后需要重新从浏览器复制并导入前端
- 当前优化目标是“手续费后期望利润最大化”，不是“爆金概率最大化”
- 前端基于 Vite，建议 Node.js 18+
