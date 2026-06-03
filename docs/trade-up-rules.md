# 汰换计算规则

本文档按 2026 口径整理项目当前采用的 CS 饰品汰换合同计算规则，并标注后端已实现的边界。

## 1. 层级与槽位

系统根据投入饰品的品质自动决定合同槽位数量：

| 汰换路径 | 投入数量 | 产出比例 | 备注 |
| --- | ---: | ---: | --- |
| `consumer -> industrial -> mil-spec -> restricted -> classified -> covert` | 10 件 | 10:1 | 标准汰换 |
| `covert -> gold` | 5 件 | 5:1 | 隐秘红皮到罕见特殊物品的特殊规则 |

内部品质顺序：

```text
consumer -> industrial -> mil-spec -> restricted -> classified -> covert -> gold
```

品质中英文与 BUFF 原始字段对照：

| 系统内部值 | 英文档位 | 中文档位 | 常用叫法 | BUFF 常见原始值 |
| --- | --- | --- | --- | --- |
| `consumer` | Consumer Grade | 消费级 | 白 | `consumer_weapon` / `default_weapon` |
| `industrial` | Industrial Grade | 工业级 | 浅蓝 | `industrial_weapon` / `common_weapon` |
| `mil-spec` | Mil-Spec Grade | 军规级 | 蓝 | `milspec_weapon` / `rare_weapon` |
| `restricted` | Restricted | 受限 | 紫 | `restricted_weapon` / `mythical_weapon` |
| `classified` | Classified | 保密 | 粉 | `classified_weapon` / `legendary_weapon` |
| `covert` | Covert | 隐秘 | 红 / 红皮 / 隐秘级 | `covert_weapon` / `ancient_weapon` |
| `gold` | Exceedingly Rare / Gold | 罕见特殊物品 / 金色 | 金 / 刀手套 | `gold_item` / `rare_special_item` / `exceedingly_rare_item` |

因此文档和代码里的 `covert` 就是中文“隐秘级”，也就是俗称红皮；`covert -> gold` 表示“隐秘级红皮投入，产出金色罕见特殊物品”。

逐级关系说明：

- `consumer` 对应中文“消费级”，标准汰换下一档是 `industrial` / 工业级。
- `industrial` 对应中文“工业级”，标准汰换下一档是 `mil-spec` / 军规级。
- `mil-spec` 对应中文“军规级”，标准汰换下一档是 `restricted` / 受限。
- `restricted` 对应中文“受限”，标准汰换下一档是 `classified` / 保密。
- `classified` 对应中文“保密”，标准汰换下一档是 `covert` / 隐秘。
- `covert` 对应中文“隐秘级”，常规语境也叫红皮；项目支持它走特殊 `covert -> gold` 五合一路径。
- `gold` 对应中文“罕见特殊物品 / 金色”，通常是刀、手套等特殊物品。

`gold` 没有下一档，不能作为输入继续计算。

## 2. 基础约束

- 输入饰品必须是同一品质等级。
- 普通和 StatTrak 饰品禁止混放。
- 输入饰品必须可交易、有磨损值、有收藏品或金色上级来源信息。
- 输入饰品必须能在目录数据中找到有效的下一档产物。
- 常规合同使用 10 件；`covert -> gold` 使用 5 件。

## 3. 概率模型

概率按“来源权重 / 来源下可出底板数量”计算。磨损档位价格行不拆分概率，同一个底板的崭新、略磨、久经等只算一个产物底板。

常规 10 合 1：

```text
单个产物概率 = 该收藏品输入数量 / 10 / 该收藏品下一档可出底板数量
```

隐秘到金色 5 合 1：

```text
单个产物概率 = 该箱源输入数量 / 5 / 该箱源金色可出底板数量
```

金色合成不会只按普通收藏品名匹配。后端会优先读取 `tags.weaponcase.localized_name` 作为箱源；只有输入箱源和金色产物箱源一致时，才参与概率和 EV。

## 4. StatTrak 与手套规则

StatTrak 是独立产物池：

- 普通输入只匹配普通产物。
- StatTrak 输入只匹配 StatTrak 产物。
- 普通和 StatTrak 不会混入同一份合同。

`covert -> gold` 的特殊规则：

- 5 件普通隐秘输入：产物池允许匹配箱源下的刀和手套。
- 5 件 StatTrak 隐秘输入：只能使用可通向暗金刀的隐秘下级。
- 暗金手套下级无法参与汰换，因此不能和暗金刀下级混入同一份合同。
- 如果某个箱源的金色路径只对应手套，没有可用暗金刀类产物，那么该箱源的 StatTrak 隐秘下级视为不可用，不进入候选方案。

这条规则比简单的“暗金炼金更优”更严格：只有明确通向暗金刀类产物的隐秘输入，才允许进入 StatTrak 红转金方案。

### 纪念品（Souvenir）规则

纪念品只能从纪念品包中开出，不可能通过汰换合同产出，但它本身可以作为汰换素材：

- **纪念品可以作为投入素材**：与同档位的普通皮肤一样参与合同，按其所属收藏品和档位计算。
- **产物永远是普通版皮肤**：纪念品输入走普通（非 StatTrak）产物池，产出该收藏品上一档的普通皮肤。
- **纪念品被排除在产物池之外**：任何方案的产物都不会出现纪念品；同一收藏品下的普通版上级皮肤照常保留。
- **磨损档补全跳过纪念品**：补全产物磨损档的维护逻辑只补普通产物，不会为纪念品补全磨损档（补了也用不上）。

纪念品按名称识别（中文名含“纪念品”，英文 `market_hash_name` 含 `Souvenir`），因为 `quality_label` 存的是品质档位（如“保密”），无法区分纪念品与普通。

## 5. 磨损计算

产物磨损不是随机值，而是由输入素材的算术平均磨损决定。输入平均磨损使用实际槽位数量作为分母：常规合同除以 10，五合一除以 5。

```text
AvgFloat = sum(inputFloat) / contractSize
常规级 AvgFloat = sum(inputFloat) / 10
隐秘合金 AvgFloat = sum(inputFloat) / 5
```

产出磨损再按该产物皮肤自身的磨损上下限做线性映射：

```text
OutcomeFloat = AvgFloat * (MaxFloat_out - MinFloat_out) + MinFloat_out
```

例如二西莫夫这类固定磨损区间的产物，会使用它自己的 `MinFloat_out` 和 `MaxFloat_out`，而不是使用输入素材的磨损上下限。

如果计算结果低于目标最小磨损，按最小磨损处理；如果高于最大磨损，按最大磨损处理。

## 6. 价格区间

目录同步会从 BUFF 商品详情保存同一底板不同磨损档位的价格行，例如：

- Factory New / 崭新出厂
- Minimal Wear / 略有磨损
- Field-Tested / 久经沙场
- Well-Worn / 破损不堪
- Battle-Scarred / 战痕累累

后端先计算产物的精确 `OutcomeFloat`，再在该底板的多个价格行中选择覆盖该磨损区间的价格。若没有精确命中，选择与目标磨损最近的价格行作为兜底。

BUFF 的交易页还会在同一个大磨损档内继续细分价格，例如久经沙场常见会拆成：

- `0.15 <= float < 0.18`
- `0.18 <= float < 0.21`
- `0.21 <= float < 0.24`
- `0.24 <= float < 0.27`
- `0.27 <= float <= 0.38`

如果某个产物在这些细档价格差异很大，可以通过 `trade-up.output-price-bands` 配置精细磨损价格档。EV 计算会优先用精确 `OutcomeFloat` 命中细档价格；未命中或未配置时，再回退到 `catalog_skin` 的大档位价格。

```yml
trade-up:
  output-price-bands:
    "AWP | Asiimov":
      - min-float: 0.15
        max-float: 0.18
        price: 420.00
      - min-float: 0.18
        max-float: 0.21
        price: 360.00
      - min-float: 0.21
        max-float: 0.24
        price: 330.00
```

细档匹配顺序和特殊因子一致：先按 `goods_id`，再按完整名称，最后按去掉磨损后缀的底板名称。区间按左闭右开匹配，最后一档右边界包含。

## 7. 特殊溢价因子

某些皮肤存在“磨损越烂越贵”或“极限低磨溢价”等现象。后端支持通过 `trade-up.output-price-factors` 配置特殊价格权重：

```yml
trade-up:
  output-price-factors:
    "AWP | Asiimov": 1.18
    "1234567": 1.35
```

匹配顺序：

1. `goods_id`
2. 完整名称
3. 去掉磨损后缀的底板名称

未配置时因子为 `1.0`。如果同时配置了精细磨损价格档和特殊因子，计算顺序是先命中细档价格，再乘以特殊因子。

## 8. EV 计算

每个产物先按磨损匹配到价格，再乘以特殊因子并扣除手续费：

```text
TaxedPrice_j = Price(OutcomeFloat_j) * PriceFactor_j * (1 - saleFeeRate)
```

期望产出价值：

```text
ExpectedOutputValue = sum(P_j * TaxedPrice_j)
```

输入成本：

```text
InputCost = sum(Cost(Input_i))
```

期望利润和 ROI：

```text
ExpectedProfit = ExpectedOutputValue - InputCost
ROI = ExpectedProfit / InputCost
```

对于 `covert -> gold`：

```text
EV = sum(P_j * Price(OutcomeFloat_j)) - sum(i=1..5 Cost(Input_i))
```

项目接口中 `expected_output_value` 表示税后期望产出价值，`expected_profit` 表示扣除输入成本后的期望利润。

## 9. 当前实现位置

核心计算逻辑：

```text
src/main/java/com/qyaaaa/cstaihuan/TradeUpOptimizer.java
```

关键规则实现位置：

- 纪念品识别与排除：`TradeUpOptimizer#isSouvenirName`（产物池排除 + 投入仍可用）
- StatTrak / 暗金刀手套规则：`TradeUpOptimizer#validOutcomeFamilies` / `#isUnavailableStatTrakGoldInput`
- 产物磨损档补全（不依赖库存可达性）：`CatalogService#findIncompleteSkinAnchors` + `CatalogApplicationService`（同步时把残缺普通产物的锚点重新入队，经 `relative_goods` 补齐磨损档；跳过纪念品）

相关数据来源：

- 库存输入：`inventory_snapshot` / `inventory_item`
- 目录产物：`catalog_skin`
- 手续费率：`trade-up.sale-fee-rate`
- 特殊价格因子：`trade-up.output-price-factors`
- 精细磨损价格档：`trade-up.output-price-bands`
