# BUFF 饰品样例

下面这份数据是系统当前抓取并标准化后的单件饰品样例，适合用来核对后端字段提取、数据库落库和前端展示逻辑。

## 标准化结果

```json
{
  "name": "StatTrak™ FAMAS | Bad Trip (Factory New)",
  "price": 650.0,
  "collection": "武库通行证",
  "rarity": "ancient_weapon",
  "category_key": "weapon_famas",
  "tradable": false,
  "raw": {
    "action_link": "steam://run/730//+csgo_econ_action_preview%201E0EB9D5C583A01F06143EBE1736182E1726F99FA2F21D5EC918561E4E1E769D9E9E9E126E1A0DE96A5E",
    "allow_auction": true,
    "allow_bundle_inventory": false,
    "allow_low_fee_rate": null,
    "allow_pre_sell": true,
    "allow_rent": true,
    "amount": 1,
    "appid": 730,
    "asset_info": {
      "action_link": "/api/market/cs2_inspect/?assetid=51065054631",
      "appid": 730,
      "assetid": "51065054631",
      "classid": "7993045445",
      "contextid": 2,
      "goods_id": 1115683,
      "id": "M4411518242",
      "info": {
        "fraudwarnings": "",
        "icon_url": "https://market.fp.ps.netease.com/file/67eb2f0625d69181ecad352eBogINRiE06?fop=imageView/6/f/webp/q/75",
        "keychains": [],
        "original_icon_url": "https://market.fp.ps.netease.com/file/67eb2f040bdac510559c3ca0vk9x8xIJ06?fop=imageView/6/f/webp/q/75",
        "paintindex": 1184,
        "paintseed": 855,
        "stickers": [],
        "tournament_tags": []
      },
      "instanceid": "8377507313",
      "paintwear": "0.06982593983411789"
    },
    "assetid": "51065054631",
    "auction_sell_order_id": null,
    "buy_max_price": "0",
    "buy_max_price_auto_accept": null,
    "classid": "7993045445",
    "contextid": 16,
    "cool_down_type": 2,
    "coupon_infos": null,
    "deposit_index": null,
    "equipped": false,
    "fraudwarnings": "",
    "game": "csgo",
    "goods_id": 1115683,
    "icon_url": "https://market.fp.ps.netease.com/file/67eb2f0625d69181ecad352eBogINRiE06",
    "instanceid": "8377507313",
    "is_renting": false,
    "item_id": null,
    "low_fee_percent": null,
    "low_fee_rate": null,
    "market_bill_order_id": null,
    "market_hash_name": "StatTrak™ FAMAS | Bad Trip (Factory New)",
    "market_min_price": "0",
    "name": "法玛斯（StatTrak™） | 幻灭之旅 (崭新出厂)",
    "newest_low_fee_percent": null,
    "newest_low_fee_rate": null,
    "newest_low_fee_type": null,
    "on_steam_market": false,
    "original_icon_url": "https://market.fp.ps.netease.com/file/67eb2f040bdac510559c3ca0vk9x8xIJ06",
    "pre_sell_order": null,
    "progress": null,
    "progress_text": null,
    "properties": null,
    "punish_end_time": null,
    "rent_order": null,
    "sealed_type": 0,
    "sell_min_price": "650",
    "sell_order_coupon_infos": null,
    "sell_order_id": null,
    "sell_order_income": "0",
    "sell_order_mode": null,
    "sell_order_order_type": null,
    "sell_order_price": "0",
    "short_name": "法玛斯（StatTrak™） | 幻灭之旅",
    "state": 6,
    "state_text": "保护期中",
    "state_toast": "该物品处于保护期中",
    "steam_price": "141",
    "tags": {
      "category": {
        "category": "category",
        "internal_name": "weapon_famas",
        "localized_name": "法玛斯"
      },
      "category_group": {
        "category": "category_group",
        "internal_name": "rifle",
        "localized_name": "步枪"
      },
      "custom": {
        "category": "custom",
        "internal_name": "beast",
        "localized_name": "beast"
      },
      "exterior": {
        "category": "exterior",
        "internal_name": "wearcategory0",
        "localized_name": "崭新出厂"
      },
      "itemset": {
        "category": "itemset",
        "internal_name": "armory",
        "localized_name": "武库通行证"
      },
      "model_version": {
        "category": "model_version",
        "internal_name": "CS2",
        "localized_name": "cs2"
      },
      "quality": {
        "category": "quality",
        "internal_name": "strange",
        "localized_name": "StatTrak™"
      },
      "rarity": {
        "category": "rarity",
        "internal_name": "ancient_weapon",
        "localized_name": "隐秘"
      },
      "type": {
        "category": "type",
        "internal_name": "csgo_type_rifle",
        "localized_name": "步枪"
      },
      "weapon": {
        "category": "weapon",
        "internal_name": "weapon_famas",
        "localized_name": "法玛斯"
      },
      "weaponcase": {
        "category": "weaponcase",
        "internal_name": "Fever Case",
        "localized_name": "fever case"
      }
    },
    "tradable": false,
    "tradable_text": "5天20小时",
    "tradable_time": 1776841200
  },
  "asset_id": "51065054631",
  "float_value": 0.06982593983411789,
  "float_value_raw": null,
  "image_url": "https://market.fp.ps.netease.com/file/67eb2f040bdac510559c3ca0vk9x8xIJ06?fop=imageView/6/f/webp/q/75",
  "wear_name": "崭新出厂",
  "quality_label": "隐秘",
  "goods_id": "1115683"
}
```

## 顶层字段说明

- `name`
  系统当前标准化后的饰品名称。  
  建议保存中文展示名，也就是 `raw.name`，例如 `法玛斯（StatTrak™） | 幻灭之旅 (崭新出厂)`。

- `price`
  当前 BUFF 库存页里可见的价格，当前主要来自 `sell_min_price`。

- `collection`
  收藏品名称，通常从 `raw.tags.itemset.localized_name` 提取。  
  示例值：`武库通行证`

- `rarity`
  BUFF 原始稀有度标识，通常来自 `raw.tags.rarity.internal_name`。  
  示例值：`ancient_weapon`

- `category_key`
  BUFF 物品类目标识，通常来自 `raw.tags.category.internal_name`。  
  过滤库存时应该看这个字段是否以 `weapon_` 开头。  
  示例值：`weapon_famas`

- `tradable`
  是否可立即交易。  
  示例里是 `false`，因为当前物品处于保护期。

- `asset_id`
  库存里这件具体饰品的唯一资产 ID。  
  用来区分同名但不同实例的物品。

- `goods_id`
  BUFF 商品 ID。  
  一般同一款饰品的 BUFF 商品页会共用这个值。

- `float_value`
  数值化后的磨损度，方便后端计算和排序。

- `float_value_raw`
  原始字符串形式的磨损度，建议保留原始精度。  
  当前这条样例里还是 `null`，说明这是旧逻辑抓到的数据；新逻辑应保存 `0.06982593983411789`。

- `image_url`
  前端展示主图，建议优先取 `raw.asset_info.info.original_icon_url`。

- `wear_name`
  磨损阶段展示文案，通常来自 `raw.tags.exterior.localized_name`。  
  示例值：`崭新出厂`

- `quality_label`
  前端展示用的品质文案，通常来自 `raw.tags.rarity.localized_name`。  
  示例值：`隐秘`

- `raw`
  BUFF 原始返回数据，用来做字段兜底和后续排查。  
  不建议前端主要逻辑依赖它，但保留它对调试很有帮助。

## 关键原始字段映射

### 名称

- `raw.name`
  中文展示名，适合前端直接展示
- `raw.short_name`
  不含磨损阶段的简化中文名
- `raw.market_hash_name`
  英文市场名，适合日志或跨平台比对

推荐：

- 展示名称优先使用 `raw.name`
- 英文名只作为兜底

### 图片

- `raw.asset_info.info.original_icon_url`
  更适合做主展示图
- `raw.asset_info.info.icon_url`
  备选图
- `raw.original_icon_url`
  也是可用兜底图

推荐：

1. `raw.asset_info.info.original_icon_url`
2. `raw.asset_info.info.icon_url`
3. `raw.original_icon_url`
4. `raw.icon_url`

### 磨损度

- `raw.asset_info.paintwear`
  原始字符串磨损度，精度最完整
- `float_value`
  系统转成数值后的磨损度
- `float_value_raw`
  建议持久化保存的原始字符串磨损度

推荐：

- 展示时优先用 `float_value_raw`
- 计算时使用 `float_value`

### 磨损阶段

- `raw.tags.exterior.localized_name`
  示例值：`崭新出厂`

这是前端展示“磨损阶段”的推荐来源。

### 收藏品

- `raw.tags.itemset.localized_name`
  示例值：`武库通行证`

这是 `collection` 字段的推荐来源。

### 品质

- `raw.tags.rarity.localized_name`
  示例值：`隐秘`
  前端展示建议映射到 `quality_label`

- `raw.tags.rarity.internal_name`
  示例值：`ancient_weapon`
  这类值更适合保留为原始稀有度，不应该直接拿来决定“是不是武器”。

### 武器类目

- `raw.tags.category.internal_name`
  武器过滤最关键的字段，示例值：`weapon_famas`
- `raw.tags.weapon.internal_name`
  可作为兜底字段，示例值：`weapon_famas`

推荐：

- 是否保留到武器库存里，优先看 `category_key`
- 规则为 `category_key` 以 `weapon_` 开头

也就是说：

- `quality_label` 用于展示中文品质
- `rarity` 用于系统内部筛选、持久化和计算

## 当前这条样例的推荐落库值

如果按当前项目目标，这条数据建议落库成：

```json
{
  "name": "法玛斯（StatTrak™） | 幻灭之旅 (崭新出厂)",
  "price": 650.0,
  "collection": "武库通行证",
  "rarity": "ancient_weapon",
  "category_key": "weapon_famas",
  "quality_label": "隐秘",
  "tradable": false,
  "asset_id": "51065054631",
  "goods_id": "1115683",
  "float_value": 0.06982593983411789,
  "float_value_raw": "0.06982593983411789",
  "image_url": "https://market.fp.ps.netease.com/file/67eb2f040bdac510559c3ca0vk9x8xIJ06?fop=imageView/6/f/webp/q/75",
  "wear_name": "崭新出厂"
}
```

## 备注

- 当前项目数据库只持久化 `category_key` 以 `weapon_` 开头的武器类物品
- 相同物品不再折叠，前端按单件展示
- 旧快照如果没有 `float_value_raw` 或中文名称，需要重新抓取一次库存
