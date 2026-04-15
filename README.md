# cs-taihuan

基于 BUFF 账号库存数据，自动筛选更优的 CS 汰换方案。当前实现为 `Java 8 + Maven` 项目。

## 当前能力

1. 读取 `BUFF_COOKIE`，分页拉取 BUFF 库存数据。
2. 将库存标准化为 JSON 文件，方便留档和二次分析。
3. 读取本地 `catalog` 元数据，补齐收藏品、品质、磨损范围和目标售价。
4. 自动枚举 10 连汰换合同，按手续费后期望利润排序输出最优方案。

## 目录结构

```text
src/main/java/com/qyaaaa/cstaihuan
```

## 快速开始

如果本机有 Maven：

```bash
mvn compile
```

运行前准备 Cookie：

```bash
export BUFF_COOKIE='session=...; csrf_token=...'
```

抓取库存：

```bash
mvn exec:java -Dexec.args="fetch --game csgo --output data/buff_inventory.json"
```

运行汰换优化：

```bash
mvn exec:java -Dexec.args="optimize --inventory data/buff_inventory.json --catalog examples/catalog.sample.json --top-k 5"
```

一键执行：

```bash
mvn exec:java -Dexec.args="run --game csgo --output data/buff_inventory.json --catalog examples/catalog.sample.json"
```

本地没有 Maven 也可以直接编译运行：

```bash
mkdir -p out
javac --release 8 -d out $(find src/main/java -name '*.java')
java -cp out com.qyaaaa.cstaihuan.Main optimize --inventory examples/inventory.sample.json --catalog examples/catalog.sample.json --top-k 3
```

## Catalog 格式

`catalog` 需要补齐以下字段：

- `name`
- `collection`
- `rarity`
- `min_float`
- `max_float`
- `price`

示例见 [examples/catalog.sample.json](/Users/qiaoyu/project/cs-taihuan/examples/catalog.sample.json)，库存示例见 [examples/inventory.sample.json](/Users/qiaoyu/project/cs-taihuan/examples/inventory.sample.json)。

## 注意事项

- BUFF 接口可能会调整字段结构，首次接入建议先跑一次 `fetch` 检查落盘 JSON。
- 若 `catalog` 缺少饰品信息，该饰品会在优化时被跳过。
- 当前优化目标是“手续费后期望利润最大化”，不是“爆金概率最大化”。
