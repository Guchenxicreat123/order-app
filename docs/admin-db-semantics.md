# 点餐小程序 — 后端持久化语义（供独立 Node.js 直连 MySQL 服务使用）

> 目的：让一个新的、**不经业务后端**、直接对 `order_app` 库执行原生 SQL 的 Node 服务，能写出**语义完全等价**的读写。
> 依据：`backend/src/main/java/com/example/order/**`（controller / service / mapper / entity）+ `backend/init.sql`（线上真实结构导出，17 张表）。
> 阅读顺序建议：先看 §0 全局约定，再看 §1 DDL，然后按操作号 §2–§23 查阅。

---

## 0. 全局约定（读之前必须知道的事实）

### 0.1 数据访问层行为

* MyBatis-Plus，配置见 `backend/src/main/resources/application.yml`：
  ```yaml
  mybatis-plus:
    configuration:
      map-underscore-to-camel-case: true
    global-config:
      db-config:
        id-type: auto
        # 已移除 logic-delete-field，表无 deleted 字段
  ```
* **没有逻辑删除**。所有 `DELETE` 都是物理 `DELETE FROM`。`grep TableLogic` 无任何命中。
* **没有 MetaObjectHandler**。`PublicDish` 上的 `@TableField(fill = FieldFill.INSERT)` / `(fill = FieldFill.INSERT_UPDATE)` **是死代码**（全仓无 `MetaObjectHandler` 实现），`created_at/updated_at` 全部由 service 手动 `LocalDateTime.now()` 或由 MySQL 列默认值/`ON UPDATE` 填充。
* `updateById(entity)` 使用 MyBatis-Plus 默认字段策略 `NOT_NULL`：**null 字段不参与 UPDATE**。因此"部分字段更新"是通过 `SELECT` 出实体 → 改字段 → 整行回写实现的（读-改-写，非原子）。
* `insert(entity)` 同理：**null 字段不出现在 INSERT 列清单里** → 落到 DDL 的 DEFAULT。
* 没有全局 Jackson `@JsonInclude(NON_NULL)` 配置 → **null 会被序列化成 JSON `null`（键仍然存在）**，不会缺键。
* 实体字段全是包装类型（`Long/Integer/BigDecimal/String/LocalDateTime`），故任何列都可能返回 `null`。
* `Result<T>` 信封：`{"code":0,"message":"ok","data":...}`；错误 `Result.error(code,msg)` → `{"code":400,"message":"...","data":null}`。全局兜底 `GlobalExceptionHandler.handleGeneral` 把**任何未捕获异常（含 MySQL 唯一键冲突、NOT NULL 违约）都变成 `{"code":500,"message":"服务器异常，请稍后重试"}`**。

### 0.2 数据库层约束现状

* **全库没有任何 `FOREIGN KEY`，没有 `CHECK`，没有 `ENUM`**。`init.sql` 里只有 `PRIMARY KEY` / `UNIQUE KEY` / `KEY`。所有"级联删除"、"引用检查"、枚举取值都是**应用层**在维护 → 直连 SQL 时必须自己复刻。
* **MySQL 8.0 默认 STRICT 模式**（`docker-compose.yml` 未关闭）。列无 DEFAULT 且 NOT NULL 时，INSERT 缺列 = `Error 1364` → 后端返回 500。
* 时间列普遍是 `datetime DEFAULT CURRENT_TIMESTAMP [ON UPDATE CURRENT_TIMESTAMP]`，精度到秒。
* 字符集 `utf8mb4_0900_ai_ci`；`t_order` 是 `utf8mb4_general_ci`（唯一一张不一致的表）。

### 0.3 家庭隔离（family scoping）——最容易被直连实现忽略的一点

后端所有家庭维度的读写都从 **`UserContext.getFamily()`** 取 `familyId`，其来源是 `JwtInterceptor`：

```java
// JwtInterceptor.loadAndVerify
if (user.getActiveFamilyId() != null) {
    UserContext.setFamily(user.getActiveFamilyId());
}
// applyFamilyHeader：仅当 is_chef=1（全局管理员）或该家庭 CHEF 时，
// 才允许用请求头 X-Family-Id 覆盖
```

即：**默认家庭 = `t_user.active_family_id`**，管理员可用 `X-Family-Id` 覆盖。
新的直连 SQL 服务没有这个上下文，**必须把 `family_id` 作为显式参数传入每条 SQL**，不能"自动推导"。

权限校验统一形如（`DishService.checkFamilyChefOrOwner`）：

```java
FamilyMember me = familyMemberMapper.selectOne(... familyId + userId ...);
if (me == null) return Result.error(403, "您不是该家庭成员");
if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
    return Result.error(403, "只有创建者或主厨才能管理家庭菜单");
}
```

鉴权路由（`JwtInterceptor.preHandle`）决定用 ADMIN / CHEF / USER 哪种 token：

```java
if (path.startsWith("/api/admin")
        || (path.startsWith("/api/public/dishes") && !"GET".equals(method))
        || (path.startsWith("/api/public/ingredients") && !"GET".equals(method))) {
    return requireAdmin(request, response);          // 只认 ADMIN token
}
```

### 0.4 本文件引用的 DDL 原文位置

`backend/init.sql`。`backend/migrations/V1..V7b` 是历史演进脚本，**`init.sql` 已是最终合并结果**，直接以 `init.sql` 为准。（唯一差异：`V2` 给 `t_order` 加过 `meal_type` / `address`，但 `init.sql` 导出的线上结构里**没有这两列**，`Order` 实体里也没有 → 视为不存在。）

---

## 1. DDL 原文（直连服务必须对齐的真实约束）

### 1.1 `t_dish`

```sql
CREATE TABLE IF NOT EXISTS `t_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL,
  `image_emoji` varchar(8) DEFAULT '',
  `description` text,
  `spice_level` tinyint DEFAULT '0',
  `price` decimal(10,2) DEFAULT '0.00',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  -- 菜名唯一是「家庭内」维度：不同家庭可以存在同名菜（如各自从公共库加入"鱼香肉丝"）
  UNIQUE KEY `uk_family_name` (`family_id`, `name`),
  KEY `idx_cat` (`category_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `category_id` NOT NULL 无 DEFAULT → 直连 INSERT 必须给值（后端 `DEFAULT_CATEGORY_ID = 1L` 兜底见 §3）。
* `uk_family_name` 是**同家庭内菜名唯一**的硬约束。

### 1.2 `t_dish_ingredient`

```sql
CREATE TABLE IF NOT EXISTS `t_dish_ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dish_id` bigint NOT NULL,
  `ing_id` bigint NOT NULL,
  `amount` decimal(10,3) NOT NULL,
  `unit` varchar(16) DEFAULT '',
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dish_ing` (`dish_id`,`ing_id`),
  KEY `idx_ing` (`ing_id`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `amount` NOT NULL 无 DEFAULT（`decimal(10,3)`，注意**小数位是 3**，而 `t_ingredient.price` 是 2 位）。
* `uk_dish_ing` → 同一道菜不能重复引用同一配菜；**一次请求里重复 `ingId` 会触发 1062 → 后端 500 回滚**。

### 1.3 `t_ingredient`

```sql
CREATE TABLE IF NOT EXISTS `t_ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL,
  `unit` varchar(16) NOT NULL,
  `price` decimal(8,2) NOT NULL DEFAULT '0.00',
  `emoji` varchar(8) DEFAULT '',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name_family` (`name`,`family_id`),
  KEY `idx_cat` (`category_id`),
  KEY `idx_name` (`name`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `unit` NOT NULL **无 DEFAULT** → `POST /api/ingredients` 不传 `unit` 时后端会 500（见 §6）。
* `category_id` NOT NULL 无 DEFAULT。
* `uk_name_family` → 同家庭内配菜名唯一。

### 1.4 `t_ingredient_category`

```sql
CREATE TABLE IF NOT EXISTS `t_ingredient_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(32) NOT NULL,
  `emoji` varchar(8) DEFAULT '',
  `sort` int DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `family_id` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name_family` (`name`,`family_id`),
  KEY `idx_sort` (`sort`),
  KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `family_id = 0` 是"公共参考分类"的约定值（见 §19 `GET /api/public/ingredients/categories`）。
* 注意 `name` 只有 varchar(32)，比 `t_ingredient.name` 短。

### 1.5 `t_public_dish`

```sql
CREATE TABLE IF NOT EXISTS `t_public_dish` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint DEFAULT NULL,
  `image_emoji` varchar(16) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `spice_level` tinyint DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* **没有 `family_id`**（全局公共库）；**没有唯一键**（公共菜可重名）；`category_id` 可空（实体是 `Long`，可 null）；`image_emoji` 是 varchar(16)（`t_dish` 是 8）。

### 1.6 `t_public_ingredient`

```sql
CREATE TABLE IF NOT EXISTS `t_public_ingredient` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `category_id` bigint NOT NULL DEFAULT '0',
  `unit` varchar(16) NOT NULL DEFAULT '克',
  `price` decimal(8,2) NOT NULL DEFAULT '0.00',
  `emoji` varchar(8) DEFAULT '',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cat` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `unit` NOT NULL **DEFAULT '克'**（与 `t_ingredient` 不同！）；`category_id` NOT NULL DEFAULT 0；**无唯一键**。

### 1.7 `t_category`

```sql
CREATE TABLE IF NOT EXISTS `t_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `sort` int DEFAULT '0',
  `status` tinyint DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* **没有 `family_id`**：菜品分类是全局共享的（种子数据在 `init.sql` 末尾插入：🍳 热菜/🥒 凉菜/🍚 主食/🥣 汤品/🥤 饮品）。
* 实体 `Category` **没有 `createdAt` 字段** → `created_at` 不会被读出，也不出现在 JSON 里。

### 1.8 `t_family`

```sql
CREATE TABLE IF NOT EXISTS `t_family` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL,
  `code` varchar(8) NOT NULL,
  `owner_user_id` varchar(64) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_owner` (`owner_user_id`),
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* **没有 `updated_at`** → 改家庭名不会产生时间戳。
* `code` UNIQUE，实际是 6 位（字符集 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`，去掉了 I/O/0/1），列宽 8。
* `owner_user_id` 是 **openid 字符串**，不是数字。

### 1.9 `t_family_member`

```sql
CREATE TABLE IF NOT EXISTS `t_family_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `user_id` varchar(64) NOT NULL,
  `role` varchar(16) NOT NULL DEFAULT 'MEMBER',
  `nickname` varchar(64) DEFAULT NULL,
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_family_user` (`family_id`,`user_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_chef` (`family_id`,`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `role` 无 DB 级枚举校验，应用层只用 `'OWNER' | 'CHEF' | 'MEMBER'`（大写）。
* `uk_family_user` → 同一用户在同一家庭只能一行。
* `nickname` 是 **快照列**（见 §0.5 / §24）。

### 1.10 `t_order`

```sql
CREATE TABLE IF NOT EXISTS `t_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `family_id` bigint NOT NULL,
  `user_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_nickname` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `item_count` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `confirmed_by` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_family_created` (`family_id`,`created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
```

* `status` NOT NULL DEFAULT 0。**`confirmed_by` 在订单维度实际上永远不会被写**（见 §11 / §24）。
* `total_amount` / `item_count` 是**创建时算好的冗余列**，之后任何单品状态变更都不会重算。

### 1.11 `t_menu_item`

```sql
CREATE TABLE IF NOT EXISTS `t_menu_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `family_id` bigint NOT NULL DEFAULT '0',
  `order_id` bigint DEFAULT NULL,
  `user_nickname` varchar(64) DEFAULT '',
  `dish_id` bigint DEFAULT NULL,
  `dish_name` varchar(64) NOT NULL,
  `dish_emoji` varchar(8) DEFAULT '',
  `custom_ings` text,
  `spice_level` tinyint DEFAULT '0',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00',
  `remark` varchar(255) DEFAULT NULL,
  `status` tinyint DEFAULT '0',
  `confirmed_by` varchar(64) DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_status_date` (`user_id`,`status`,`created_at`),
  KEY `idx_family_date` (`family_id`,`created_at`),
  KEY `idx_created_date` ((cast(`created_at` as date)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `order_id` **可空**：老的"单品下单"（`POST /api/menu/items`）不写 order_id。
* `version` 是乐观锁计数器，**只有两条手写 SQL 会 `version=version+1`**（见 §11）。
* `dish_id` 可空 = 自定义菜；`custom_ings` 是 JSON 文本快照。

### 1.12 `t_user`

```sql
CREATE TABLE IF NOT EXISTS `t_user` (
  `open_id` varchar(64) NOT NULL,
  `nickname` varchar(64) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_chef` tinyint NOT NULL DEFAULT '0',
  `allow_push` tinyint NOT NULL DEFAULT '0',
  `chef_pin` varchar(32) DEFAULT '123456',
  `active_family_id` bigint DEFAULT NULL,
  `token_version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`open_id`),
  KEY `idx_openid` (`open_id`),
  KEY `idx_chef` (`is_chef`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* **主键是 `open_id`（openid 字符串），没有自增 id**。所有 `user_id` / `owner_user_id` / `confirmed_by` 都是 varchar(64) 的 openid。
* `token_version`：JWT 撤销版本号，签发时写入 token 的 `tv` claim，请求时与库中值比对。**直连 SQL 服务若想踢人下线，唯一办法就是 `UPDATE t_user SET token_version = token_version + 1`**；反之，任何"更新用户"的 SQL 都**绝不能顺手把 token_version 重置为 0**。
* `is_chef` 是 `t_family_member.role` 的**全局镜像**（详见 §10、§24）。

### 1.13 `t_cart`

```sql
CREATE TABLE IF NOT EXISTS `t_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `family_id` bigint NOT NULL DEFAULT '0',
  `dish_id` bigint DEFAULT NULL,
  `dish_name` varchar(64) NOT NULL,
  `dish_emoji` varchar(8) DEFAULT '',
  `custom_ings` text,
  `spice_level` tinyint DEFAULT '0',
  `remark` varchar(255) DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_dish` (`dish_id`),
  KEY `idx_family_user` (`family_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

* `dish_id` 与 `dish_name`/`price`/`dish_emoji` 是**下单前的价格快照**（`CartService.addToCart` 用 `dishService.computeDishPrice()` 算当前价写入）。
* **没有唯一键**：同一 `(user_id, family_id, dish_id)` 可以有多行，累加逻辑在应用层。

---

## 2. 操作 1 — `POST /api/dishes`（新建菜谱）

`DishController.create` → `DishService.create`（`@Transactional`）。

### 请求体校验（`DishReq`）

```java
@NotBlank(message = "菜名不能为空")   private String name;
@NotNull(message = "分类ID不能为空")  private Long categoryId;
private String imageEmoji; private String description;
private Integer spiceLevel = 0;
private List<DishIngredientReq> ingredients;   // {ingId @NotNull, amount @NotNull BigDecimal, unit}
```

### 写入

**表 `t_dish`（1 行 INSERT）**

| 列 | 值 | 来源 |
|---|---|---|
| `name` | `req.name`（**未 trim**） | controller |
| `category_id` | `req.categoryId` | controller |
| `image_emoji` | `req.imageEmoji`（可 null → 落 DEFAULT `''`） | controller |
| `description` | `req.description` | controller |
| `spice_level` | `req.spiceLevel`（默认 0） | controller |
| `status` | **强制 `1`** | controller `dish.setStatus(1)` |
| `price` | 有配菜 → `sum(t_ingredient.price × amount)` `HALF_UP` 2 位；**无配菜 → `BigDecimal.ZERO`（写 0.00）** | service |
| `family_id` | **强制 = 当前家庭**（`dish.setFamilyId(familyId)`） | service |
| `created_at` / `updated_at` | 不写 → MySQL `DEFAULT CURRENT_TIMESTAMP` | — |

```java
// DishService.create
dish.setFamilyId(familyId);
if (ingredients != null && !ingredients.isEmpty()) {
    dish.setPrice(computeFromList(ingredients));
}
dishMapper.insert(dish);
saveIngredients(dish.getId(), familyId, ingredients);
```

**表 `t_dish_ingredient`（N 行 INSERT）**——**是的，会写**：

```java
private void saveIngredients(Long dishId, Long familyId, List<Map<String, Object>> ingredients) {
    if (ingredients == null) return;
    for (Map<String, Object> m : ingredients) {
        DishIngredient di = new DishIngredient();
        di.setFamilyId(familyId);
        di.setDishId(dishId);
        di.setIngId(((Number) m.get("ingId")).longValue());
        di.setAmount(new BigDecimal(m.get("amount").toString()));
        di.setUnit(m.getOrDefault("unit", "").toString());
        diMapper.insert(di);
    }
}
```

写入列：`dish_id`、`ing_id`、`amount`、`unit`（缺省 `""`）、`family_id`（= 当前家庭）。

### 事务

`@Transactional`（`DishService.create`）：`t_dish` + `t_dish_ingredient` 同事务。

### 响应

`Result<Dish>` → `data` 是完整 `Dish` 实体（含自增 `id`、算出的 `price`、`status=1`、`familyId`；`createdAt/updatedAt` 为 **null**，因为实体没回读）。

### 容易被漏掉

* 入库**不做重名检查**，靠 `uk_family_name` 兜底；重名 → 1062 → 统一 500。
* `name` 不 trim（`batchSave` 才 trim，见 §3 批处理）。
* **`t_dish.price` 是冗余列**，直连实现必须复刻 `sum(ingredient.price * amount)`，否则小程序首页价格会是 0（读接口虽有兜底回填，见 §14）。

---

## 3. 操作 2 — `PUT /api/dishes/{id}`（更新菜谱）

`DishController.update` 先**用 `listAll()`（按当前家庭过滤）把实体读出来**，再覆盖入参字段，然后 `DishService.update`。

```java
Dish dish = dishService.listAll().getData().stream()
        .filter(d -> d.getId().equals(id)).findFirst().orElse(null);
if (dish == null) return Result.error(404, "菜谱不存在");
dish.setName(req.getName());
dish.setCategoryId(req.getCategoryId());
dish.setImageEmoji(req.getImageEmoji());   // ← 注意：直接覆盖，可被置 null
dish.setDescription(req.getDescription());
dish.setSpiceLevel(req.getSpiceLevel());
```

```java
// DishService.update（@Transactional）
Dish existing = dishMapper.selectById(id);
if (existing == null) return Result.error(404, "菜谱不存在");
if (ingredients != null && !ingredients.isEmpty()) {
    dish.setPrice(computeFromList(ingredients));
}
dishMapper.updateById(dish);
// 删除旧关联，插入新关联
diMapper.delete(new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getDishId, id));
if (ingredients != null) saveIngredients(id, familyId, ingredients);
```

### 写入

**表 `t_dish`**：`UPDATE ... WHERE id=?`，写回的非 null 列 = `name, category_id, image_emoji, description, spice_level, status(沿用读出的旧值), price, family_id, created_at, id`。省略 `status`（没有带 `status` 的 DTO 字段）→ **PUT 不会改上下架状态**。

**表 `t_dish_ingredient`**：**先全删再全插**（不是 diff）：

```sql
DELETE FROM t_dish_ingredient WHERE dish_id = ?;
-- 然后按请求的 ingredients 逐行 INSERT
```

* 请求带 `ingredients: []`（空数组）→ `ingredients != null` 但 `isEmpty()` → **price 保持原值**，`saveIngredients` 空转 → 结果 = **该菜所有配方被清空**。
* 请求完全不带 `ingredients`（null）→ **配方保持原样**（不删不插）。
* 这是最容易写错的语义：`null` = 不动，`[]` = 清空。

### 事务

`@Transactional`。

### 响应

`Result<Void>` → `{"code":0,"message":"ok","data":null}`。

### 容易被漏掉

* 值都是"读-改-写"整行覆盖，两处 `selectById` 之间无锁 → 并发下会丢更新。
* **不会清理 `t_cart` 里引用该菜的 `price` 快照**（购物车里已添加的菜价格不变，下单时用购物车里的旧价，见 §11 checkout）。

---

## 4. 操作 3 — `PUT /api/dishes/{id}/status`（上下架）

**没有请求体**，是纯 toggle：

```java
// DishService.toggleStatus
Dish dish = dishMapper.selectById(id);
if (dish == null) return Result.error(404, "菜谱不存在");
Result<?> check = checkFamilyChefOrOwner(dish.getFamilyId());
if (check.getCode() != 0) return Result.error(check.getCode(), check.getMessage());
int newStatus = dish.getStatus() == 1 ? 0 : 1;
dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
        .eq(Dish::getId, id)
        .set(Dish::getStatus, newStatus));
```

### 写入

```sql
UPDATE t_dish SET status = ? WHERE id = ?;
```

### 状态取值映射（`t_dish.status`）

| 值 | 含义 | 代码依据 |
|---|---|---|
| `1` | **上架 / 启用**（默认值） | `list()` 里 `.eq(Dish::getStatus, 1)`；`toggleStatus` 的 `dish.getStatus() == 1 ? 0 : 1` |
| `0` | **下架 / 停用** | 同上 |

`Dish` 实体默认 `private Integer status = 1;`，DDL `DEFAULT '1'`。

### 事务

**无 `@Transactional`**（单条 UPDATE，本身原子）。

### 容易被漏掉

* **非 1 即 0**：如果 `status` 是 `NULL`，`dish.getStatus() == 1` 拆箱会 NPE → 500；`status = 3` 也会被 toggle 成 `1`。直连实现要决定是否严格复制这个行为。
* 权限依据的是 **`dish.family_id`**（不是当前家庭），即对任意家庭的主厨 token 都可 toggle 任何一个家庭的菜——后端这里**没有校验 dish 属于当前家庭**。
* 不会连带把该菜从任何人的 `t_cart` 里移除。

---

## 5. 操作 4 — `DELETE /api/dishes/{id}`（删除菜谱）

```java
// DishService.delete（@Transactional）
Dish dish = dishMapper.selectById(id);
if (dish == null) return Result.error(404, "菜谱不存在");
Result<?> check = checkFamilyChefOrOwner(dish.getFamilyId());
if (check.getCode() != 0) return Result.error(check.getCode(), check.getMessage());
// 删除关联的配菜记录
diMapper.delete(new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getDishId, id));
// 删除菜谱本身
dishMapper.deleteById(id);
```

### 写入（**硬删除**，无软删除、无 `deleted` 列）

```sql
DELETE FROM t_dish_ingredient WHERE dish_id = ?;
DELETE FROM t_dish          WHERE id = ?;
```

### 级联范围（很重要）

| 表 | 是否被清理 | 说明 |
|---|---|---|
| `t_dish_ingredient` | ✅ | 按 `dish_id` |
| `t_dish` | ✅ | — |
| `t_cart` | ❌ | **不清理**。`t_cart.dish_id` 会变成悬挂引用；`t_cart.dish_name/price` 是快照，下单时仍能用 |
| `t_dish_blacklist` | ❌ | **不清理**（对比：`AdminUserService.deleteFamily` 会按 `dish_id` 清，见 §17） |
| `t_menu_item` | ❌ | 不清理；历史点单保留 `dish_id` / `dish_name` / `price` 快照 |
| `t_menu_item_rating` | ❌ | 不清理 |

### 事务

`@Transactional`。

### 容易被漏掉

* 后端**没有任何"该菜被历史订单引用则拒绝删除"的检查**（对比配菜删除有引用检查，见 §6）。
* `t_dish_ingredient` 的删除按 `dish_id`，不按 `family_id`。

---

## 6. 操作 5 — 配菜（`t_ingredient`）四件套

### 6.1 `POST /api/ingredients`

`IngredientController.create(@Valid Ingredient ing)` → `IngredientService.create`。

```java
public Result<Ingredient> create(Ingredient ing) {
    Long familyId = com.example.order.common.UserContext.getFamily();
    if (familyId == null) return Result.error(400, "请先选择家庭");
    ing.setFamilyId(familyId);
    mapper.insert(ing);
    return Result.ok(ing);
}
```

写入 `t_ingredient`：`name, category_id, unit, price, emoji, status, family_id(强制当前家庭)`；`created_at/updated_at` 由 MySQL DEFAULT 填。
`Ingredient` 实体默认值：`price = BigDecimal.ZERO`、`status = 1` → 即使前端不传也会写 `0.00` / `1`。
**`unit` 没有默认值**：不传 → MP 不生成该列 → MySQL 严格模式 `Error 1364 Field 'unit' doesn't have a default value` → 统一 500。
`@Valid` 但 `Ingredient` 实体**没有任何校验注解**（不像 `DishReq`），所以 `name` 为空也会走到 INSERT → `name` NOT NULL 无默认 → 1064/1364 → 500。
`uk_name_family` 冲突 → 1062 → 500。**无事务**（单表）。

### 6.2 `PUT /api/ingredients/{id}`

```java
@PutMapping("/{id}")
public Result<Void> update(@PathVariable Long id, @RequestBody Ingredient ing) {
    ing.setId(id);
    return service.update(ing);
}
// IngredientService.update
public Result<Void> update(Ingredient ing) {
    mapper.updateById(ing);
    return Result.ok();
}
```

* `UPDATE t_ingredient SET <请求里非 null 的列> WHERE id = ?`。**没有 404 检查**（id 不存在时返回成功）。
* **没有家庭归属校验**：任何 CHEF token 都能改任意家庭的配菜。
* **请求体里的 `family_id` 会被写进去** → 可以把配菜"搬"到别的家庭（后端不拦）。
* 实体没有 `id` 以外的主键保护，`UpdateWrapper` 只带 `id`。

### 6.3 `PUT /api/ingredients/{id}/toggle`

```java
public Result<Void> toggleStatus(Long id) {
    Ingredient ing = mapper.selectById(id);
    if (ing == null) return Result.error(404, "配菜不存在");
    ing.setStatus(ing.getStatus() == 1 ? 0 : 1);
    mapper.updateById(ing);
    return Result.ok();
}
```

* `SELECT` 整行 → 翻转 → **整行回写**（写回所有非 null 列，含 `name/category_id/unit/price/emoji/family_id/created_at/updated_at`）。
* **没有家庭校验**。
* `t_ingredient.status` 映射：`1` = 启用（`listPublic` 过滤 `status=1`），`0` = 停用。同 `t_dish`，非 1 即 0。

### 6.4 `DELETE /api/ingredients/{id}`

```java
public Result<Void> delete(Long id) {
    long refCount = diMapper.selectCount(
            new LambdaQueryWrapper<DishIngredient>().eq(DishIngredient::getIngId, id));
    if (refCount > 0) {
        return Result.error(400, "该配菜已被 " + refCount + " 个菜谱引用，无法删除");
    }
    mapper.deleteById(id);
    return Result.ok();
}
```

* 引用检查是 **全局**的（`WHERE ing_id = ?`，**不带 family_id**）→ 只要**任何家庭**的菜谱引用了它就不能删。
* 通过后：`DELETE FROM t_ingredient WHERE id = ?`（**硬删除**，不清理 `t_cart` / `t_menu_item.custom_ings` JSON 里的引用）。
* **无事务**（select + delete 两步）。

---

## 7. 操作 6 — 配菜分类（`t_ingredient_category`）

### 7.1 `POST /api/ingredient-categories`

```java
public Result<IngredientCategory> create(IngredientCategory c) {
    Long familyId = com.example.order.common.UserContext.getFamily();
    if (familyId == null) return Result.error(400, "请先选择家庭");
    c.setFamilyId(familyId);
    mapper.insert(c);
    return Result.ok(c);
}
```

写入：`name, emoji, sort`（实体默认 `sort = 0`）, `family_id`（强制当前家庭）；`created_at` 由 DB 默认。
`uk_name_family (name, family_id)` 冲突 → 1062 → 500。**无事务**。

### 7.2 `PUT /api/ingredient-categories/{id}`

`c.setId(id); mapper.updateById(c);` → `UPDATE t_ingredient_category SET <非 null 列> WHERE id=?`。
**没有任何归属校验，也不修改 `family_id`（除非请求体显式传）**。id 不存在也返回成功。

### 7.3 `DELETE /api/ingredient-categories/{id}`

`mapper.deleteById(id);` → `DELETE FROM t_ingredient_category WHERE id = ?`。

* **硬删除**，**没有引用检查**：`t_ingredient.category_id` 会留下悬挂 id。分类下还有配菜也照删。

> 只读接口 `GET /api/ingredient-categories` 见 §18。

---

## 8. 操作 7 — 公共菜品库（`t_public_dish`）

路由前缀 `/api/public/dishes`。`JwtInterceptor` 规定 **非 GET 一律需要 ADMIN token**：

```java
if (path.startsWith("/api/admin")
        || (path.startsWith("/api/public/dishes") && !"GET".equals(method))
        || (path.startsWith("/api/public/ingredients") && !"GET".equals(method))) {
    return requireAdmin(request, response);
}
```

### 8.1 `POST /api/public/dishes`

```java
@Transactional
public Result<PublicDish> create(PublicDish dish) {
    dish.setStatus(1);                          // 强制上架，忽略入参 status
    dish.setCreatedAt(LocalDateTime.now());
    dish.setUpdatedAt(LocalDateTime.now());
    publicDishMapper.insert(dish);
    return Result.ok(dish);
}
```

写入 `t_public_dish`：`name, category_id(可 null), image_emoji, description, spice_level, status=1, created_at, updated_at`。
无 `family_id`（全局表）；无唯一键；`@RequestBody` **没有 `@Valid`**，也没有 service 层校验 → `name` 为 null 时由 DB NOT NULL 报错 → 500。

### 8.2 `PUT /api/public/dishes/{id}`

```java
PublicDish existing = publicDishMapper.selectById(id);
if (existing == null) return Result.error(404, "菜品不存在");
if (upd.getName() != null) existing.setName(upd.getName());
if (upd.getCategoryId() != null) existing.setCategoryId(upd.getCategoryId());
if (upd.getImageEmoji() != null) existing.setImageEmoji(upd.getImageEmoji());
if (upd.getDescription() != null) existing.setDescription(upd.getDescription());
if (upd.getSpiceLevel() != null) existing.setSpiceLevel(upd.getSpiceLevel());
if (upd.getStatus() != null) existing.setStatus(upd.getStatus());
existing.setUpdatedAt(LocalDateTime.now());
publicDishMapper.updateById(existing);
```

**真正的 PATCH 语义**：`null` = 不动；`status` 可被显式改成 0（下架）。`updated_at` 强制刷新为 `now()`。
唯一的 404 检查点。

### 8.3 `DELETE /api/public/dishes/{id}`

```java
@Transactional
public Result<Void> delete(Long id) {
    publicDishMapper.deleteById(id);
    return Result.ok();
}
```

`DELETE FROM t_public_dish WHERE id = ?`（硬删除），**无存在性检查**（id 不存在也返回 ok）。
不会影响已"加入家庭"产生的 `t_dish` 拷贝（那是独立数据）。

### 8.4 相关：`POST /api/public/dishes` 之外的"加入家庭"

`PublicDishService.addToFamily(familyId, publicDishIds)`（`POST /api/family/dishes/from-public`、`POST /api/admin/public-dishes/to-family`）：

* 只取 `status = 1` 的公共菜；
* 已有同名菜（按 `t_dish.name` 精确匹配该家庭全部菜）则跳过；
* 否则 `INSERT INTO t_dish (name, category_id, image_emoji, description, spice_level, status=1, family_id, created_at, updated_at)`，**`price` 不写** → 落到 `DEFAULT '0.00'`（等 `/api/dishes` 读取时回填，见 §14）；
* 返回 `{addedCount, skippedCount, addedNames[], skippedNames[]}`。

---

## 9. 操作 8 — 公共配菜库（`t_public_ingredient`）

### 9.1 `POST /api/public/ingredients`

```java
@Transactional
public Result<PublicIngredient> create(PublicIngredient ing) {
    if (ing.getStatus() == null) ing.setStatus(1);
    ing.setCreatedAt(LocalDateTime.now());
    ing.setUpdatedAt(LocalDateTime.now());
    publicIngredientMapper.insert(ing);
    return Result.ok(ing);
}
```

写入：`name, category_id(不传→DEFAULT 0), unit(不传→DEFAULT '克'), price(实体默认 0.00), emoji, status(不传→1，可显式传 0), created_at, updated_at`。

### 9.2 `PUT /api/public/ingredients/{id}`

与公共菜同款 PATCH 合并语义（`name / category_id / unit / price / emoji / status` 各自 `!= null` 才覆盖），`updated_at = now()`，`selectById == null` → 404。

### 9.3 `DELETE /api/public/ingredients/{id}`

`DELETE FROM t_public_ingredient WHERE id = ?`（硬删除，无存在性检查）。
**不会**影响已 `to-family` 复制出来的 `t_ingredient` 行。

### 9.4 相关：`POST /api/public/ingredients/to-family`

`PublicIngredientService.addToFamily` 有一个**分类名映射**逻辑，值得复刻：

```java
// 公共分类名（family_id=0） → id
Map<Long, String> publicCatName = ... .eq(IngredientCategory::getFamilyId, 0L) ...;
// 目标家庭已有分类 → name → id
Map<String, Long> familyCatByName = ... .eq(IngredientCategory::getFamilyId, familyId) ...;
...
Long targetCatId = null;
String catName = publicCatName.get(pi.getCategoryId());
if (catName != null) targetCatId = familyCatByName.get(catName);
if (targetCatId == null) targetCatId = pi.getCategoryId(); // 找不到映射则保留原ID
```

即：把公共分类 **按名字**换成本家庭的同名分类 id；找不到同名分类就**原样保留公共分类 id**（会产生"跨家庭分类 id"）。
跳过策略：目标家庭已有同名 `t_ingredient.name` → skip。插入时 `status = 1`、`unit` 缺省 `'克'`、`price` 缺省 0、`family_id` = 目标家庭。
返回 `{addedCount, skippedCount, addedNames[], skippedNames[]}`。

---

## 10. 操作 9 — 家庭（`t_family` / `t_family_member`）

### 10.1 `POST /api/admin/families`（`{name, ownerUserId?}`）

`AdminUserService.createFamily`（`@Transactional`）：

```java
if (name == null || name.isBlank()) return Result.error(400, "家庭名称不能为空");
if (name.length() > 32) return Result.error(400, "名称不能超过 32 字符");
String userId = ownerUserId != null ? ownerUserId : UserContext.get();
User owner = userMapper.selectById(userId);
if (owner == null) return Result.error(404, "用户不存在");

Family f = new Family();
f.setName(name.trim());            // ← 注意 trim
f.setCode(generateCode());
f.setOwnerUserId(userId);
f.setCreatedAt(LocalDateTime.now());
familyMapper.insert(f);

// 全局管理员（is_chef=1，即后台 admin）创建家庭时，不作为家庭成员加入
boolean isGlobalAdmin = owner.getIsChef() != null && owner.getIsChef() == 1;
if (!isGlobalAdmin) {
    FamilyMember ownerMember = new FamilyMember();
    ownerMember.setFamilyId(f.getId());
    ownerMember.setUserId(userId);
    ownerMember.setRole("OWNER");
    ownerMember.setNickname(owner.getNickname());   // ← 昵称快照
    ownerMember.setJoinedAt(LocalDateTime.now());
    memberMapper.insert(ownerMember);

    if (owner.getActiveFamilyId() == null) {        // 仅在无活跃家庭时接管
        User u2 = new User(); u2.setOpenId(userId); u2.setActiveFamilyId(f.getId());
        userMapper.updateById(u2);
    }
}
```

**写入**
1. `INSERT INTO t_family (name[trim], code, owner_user_id, created_at)` — `code` 6 位，取自 `"ABCDEFGHJKLMNPQRSTUVWXYZ23456789"`，`selectCount` 查重后重试（`generateCode()` 递归）。
2. 若 owner **不是** `is_chef=1`：`INSERT INTO t_family_member (family_id, user_id, role='OWNER', nickname=t_user.nickname, joined_at=now)`。
3. 若 owner 的 `active_family_id IS NULL`：`UPDATE t_user SET active_family_id = 新家庭 id WHERE open_id = ?`（只写这一列，MyBatis-Plus `NOT_NULL` 策略不会动别列）。

**关键分支**：**当 `ownerUserId` 指向一个 `is_chef=1` 的全局管理员时，不会创建任何 `t_family_member` 行，也不会改 `active_family_id`** → 家庭创建后是"无成员、无 OWNER"的裸家庭。后台批量建家庭后必须再调 `add-family`。

**响应**：`data = {familyId, name, code}`（`LinkedHashMap`，键序固定）。

### 10.2 `PUT /api/admin/families/{familyId}`（`{name}`）

```java
public Result<Void> updateFamily(Long familyId, String name) {
    if (familyId == null) return Result.error(400, "familyId 不能为空");
    if (name == null || name.isBlank()) return Result.error(400, "家庭名称不能为空");
    if (name.length() > 32) return Result.error(400, "名称不能超过 32 字符");
    Family f = familyMapper.selectById(familyId);
    if (f == null) return Result.error(404, "家庭不存在");
    f.setName(name.trim());
    familyMapper.updateById(f);
    return Result.ok();
}
```

`UPDATE t_family SET name = ?, code = ?, owner_user_id = ?, created_at = ? WHERE id = ?`（整行回写非 null 列）。
**无 `@Transactional`**（单条）。`t_family` 没有 `updated_at`。

### 10.3 `DELETE /api/admin/families/{familyId}` — 级联清理（`@Transactional`）

顺序严格如下（`AdminUserService.deleteFamily`）：

```java
// 1. 收集待清理的 user 与 dish
List<FamilyMember> members = ... eq(familyId);
List<String> memberUserIds = ...;
List<Dish> dishes = ... eq(Dish::getFamilyId, familyId);
List<Long> dishIds = ...;

// 2. 删 dish_ingredient（按 familyId 或 dishId）
if (!dishIds.isEmpty()) {
    dishIngredientMapper.delete(... in(DishIngredient::getDishId, dishIds));
    dishBlacklistMapper.delete(... in(DishBlacklist::getDishId, dishIds));
}
dishIngredientMapper.delete(... eq(DishIngredient::getFamilyId, familyId));
// 3. 删 dish
dishMapper.delete(... eq(Dish::getFamilyId, familyId));
// 4. 删 menu_item
menuItemMapper.delete(... eq(MenuItem::getFamilyId, familyId));
// 5. 删 cart
cartMapper.delete(... eq(Cart::getFamilyId, familyId));
// 6. 删 ingredients & categories
ingredientMapper.delete(... eq(Ingredient::getFamilyId, familyId));
ingredientCategoryMapper.delete(... eq(IngredientCategory::getFamilyId, familyId));
// 7. 删家庭成员
memberMapper.delete(... eq(FamilyMember::getFamilyId, familyId));
// 8. 修复被删除成员的 activeFamilyId 与 isChef
for (String uid : memberUserIds) {
    List<FamilyMember> remaining = ... eq(userId, uid).orderByAsc(joinedAt);
    Long newActive = remaining.isEmpty() ? null : remaining.get(0).getFamilyId();
    User u = userMapper.selectById(uid);
    if (u != null) {
        if (!Objects.equals(u.getActiveFamilyId(), newActive)) { /* UPDATE t_user SET active_family_id */ }
        syncGlobalChef(uid);                       // 重算 is_chef
    }
}
// 9. 删家庭本体
familyMapper.deleteById(familyId);
```

**等价 SQL 序列**

```sql
-- (a) 若该家庭有菜
DELETE FROM t_dish_ingredient   WHERE dish_id IN (:dishIds);
DELETE FROM t_dish_blacklist    WHERE dish_id IN (:dishIds);
-- (b) 无条件
DELETE FROM t_dish_ingredient      WHERE family_id = :fid;
DELETE FROM t_dish                 WHERE family_id = :fid;
DELETE FROM t_menu_item            WHERE family_id = :fid;
DELETE FROM t_cart                 WHERE family_id = :fid;
DELETE FROM t_ingredient           WHERE family_id = :fid;
DELETE FROM t_ingredient_category  WHERE family_id = :fid;
DELETE FROM t_family_member        WHERE family_id = :fid;
-- (c) 对每个原成员 uid
UPDATE t_user SET active_family_id = <剩余最早加入的家庭或 NULL> WHERE open_id = :uid;
UPDATE t_user SET is_chef = (SELECT COUNT(*) FROM t_family_member WHERE user_id=:uid AND role='CHEF' > 0) WHERE open_id = :uid;
-- (d)
DELETE FROM t_family WHERE id = :fid;
```

**⚠️ 未被清理的表（直连实现要决定是否补上）**：`t_order`（按 `family_id`）、`t_menu_item_rating`（按 `family_id` 或 `menu_item_id`）、`t_push_log`、`t_push_pending`、`t_dish_blacklist`（按 `family_id`，只按 dishId 清过）。
→ 删家庭后**会留下孤儿订单**，`GET /api/orders` 因为按 family 过滤看不到，但数据仍在库里。

---

## 11. 操作 10 — 用户管理（`/api/admin/users/*`）

### 11.1 `POST /api/admin/users/{userId}/add-family`（`{familyId, role?}`）

```java
String targetRole = normalizeRole(role);       // null/blank → "MEMBER"；只接受 MEMBER/CHEF，否则 400
FamilyMember existing = findMember(familyId, userId);
if (existing != null) {
    return changeRole(familyId, userId, targetRole, false);   // 已存在 → 改角色（OWNER 不可改）
}
FamilyMember m = new FamilyMember();
m.setFamilyId(familyId);
m.setUserId(userId);
m.setRole(targetRole);
m.setNickname(u.getNickname());                // ← 快照
m.setJoinedAt(LocalDateTime.now());
memberMapper.insert(m);

if ("CHEF".equals(targetRole)) {
    demoteOtherChef(familyId, userId);         // 一家只能一个 CHEF
}
if (u.getActiveFamilyId() == null) {           // 仅当用户还没有活跃家庭
    /* UPDATE t_user SET active_family_id = familyId */
}
syncGlobalChef(userId);
return Result.ok(Map.of("familyId", familyId, "role", targetRole));
```

```java
/** 家庭只能有一个主厨：把其它 CHEF 降为 MEMBER */
private void demoteOtherChef(Long familyId, String keepUserId) {
    List<FamilyMember> chefs = memberMapper.selectList(... eq(familyId).eq(role,"CHEF").ne(userId, keepUserId));
    for (FamilyMember c : chefs) {
        c.setRole("MEMBER");
        memberMapper.updateById(c);
        syncGlobalChef(c.getUserId());
    }
}

/** 同步 user.is_chef 全局镜像：只要用户在任一家庭是 CHEF 即 1 */
private void syncGlobalChef(String userId) {
    Long cnt = memberMapper.selectCount(... eq(userId).eq(role,"CHEF"));
    User upd = new User();
    upd.setOpenId(userId);
    upd.setIsChef(cnt != null && cnt > 0 ? 1 : 0);
    userMapper.updateById(upd);
}
```

**写入**：`INSERT t_family_member`（可能）；`UPDATE t_family_member SET role='MEMBER' WHERE family_id=? AND role='CHEF' AND user_id<>?`（可能，多行）；`UPDATE t_user SET active_family_id=?`（条件性）；`UPDATE t_user SET is_chef=?`（**对被操作的 user 以及被降级的前主厨都会执行**）。

**校验**：400 参数不完整 / 角色非法；404 用户或家庭不存在；`normalizeRole` 只认 `MEMBER`/`CHEF`（大小写不敏感，`trim` 后 `toUpperCase`）。

### 11.2 `POST /api/admin/users/{userId}/remove-family`（`{familyId}`）

```java
FamilyMember m = findMember(familyId, userId);
if (m == null) return Result.error(404, "该用户不在该家庭");
if ("OWNER".equals(m.getRole())) return Result.error(400, "创建者不能移出，请先转移所有权");

boolean wasChef = "CHEF".equals(m.getRole());
memberMapper.deleteById(m.getId());            // 按 t_family_member.id 删

if (wasChef) syncGlobalChef(userId);           // 重算 is_chef

User u = userMapper.selectById(userId);
if (u != null && familyId.equals(u.getActiveFamilyId())) {
    List<FamilyMember> remaining = ... eq(userId).orderByAsc(joinedAt);
    /* UPDATE t_user SET active_family_id = remaining.isEmpty() ? NULL : remaining.get(0).familyId */
}
```

**写入**：`DELETE FROM t_family_member WHERE id = ?`（该行）；条件性 `UPDATE t_user SET is_chef = ?`；条件性 `UPDATE t_user SET active_family_id = ?`（可能置 **NULL**）。

### 11.3 `POST /api/admin/users/{userId}/set-role`（`{familyId, role}`）

```java
private Result<Map<String, Object>> changeRole(Long familyId, String userId, String targetRole, boolean requireMember) {
    FamilyMember m = findMember(familyId, userId);
    if (m == null) {
        if (requireMember) return Result.error(404, "该用户不在该家庭");
        return addFamily(...) ...;
    }
    if ("OWNER".equals(m.getRole()) && !"OWNER".equals(targetRole)) {
        return Result.error(400, "创建者角色不可修改");
    }
    if (m.getRole().equals(targetRole)) {
        return Result.ok(Map.of("familyId", familyId, "role", targetRole));   // 幂等 no-op
    }
    m.setRole(targetRole);
    memberMapper.updateById(m);               // ← 整行回写（含 nickname/joined_at）
    if ("CHEF".equals(targetRole)) demoteOtherChef(familyId, userId);
    syncGlobalChef(userId);
    return Result.ok(Map.of("familyId", familyId, "role", targetRole));
}
```

**写入**：`UPDATE t_family_member SET family_id, user_id, role, nickname, joined_at WHERE id = ?`；条件性批量降级；`UPDATE t_user SET is_chef = ?`。
**`OWNER` 只能"保持"，不能设置也不能取消**（`normalizeRole` 根本不接受 `"OWNER"` → 传 OWNER 会 400"角色只能是 MEMBER 或 CHEF"）。

### 11.4 `DELETE /api/admin/users/{userId}`

```java
// 不允许删除自己（admin）
String adminId = UserContext.get();
if (adminId != null && userId.equals(adminId)) return Result.error(400, "不能删除自己");

// 1. 家庭成员关系
memberMapper.delete(... eq(FamilyMember::getUserId, userId));
// 2. 购物车
cartMapper.delete(... eq(Cart::getUserId, userId));
// 3. 菜单项（按用户查）
menuItemMapper.delete(... eq(MenuItem::getUserId, userId));
// 4. 删除用户
userMapper.deleteById(userId);
```

**等价 SQL**

```sql
DELETE FROM t_family_member WHERE user_id = :uid;
DELETE FROM t_cart          WHERE user_id = :uid;
DELETE FROM t_menu_item     WHERE user_id = :uid;
DELETE FROM t_user          WHERE open_id = :uid;
```

**⚠️ 注释与实现不一致**：方法注释写"清理顺序：家庭成员 → 购物车 → 菜单项 → **订单** → 用户本身"，但**代码里没有删任何订单**。同样未清理：`t_order`、`t_menu_item_rating`、`t_push_log`、`t_push_pending`、`t_dish_blacklist`；也**不会**：
* 重算其他受影响用户的 `active_family_id` / `is_chef`；
* 处理该用户创建的 `t_family`（`owner_user_id` 悬挂）或 `t_family.owner_user_id` 的转移；
* 清理该用户在别人家庭里留下的快照昵称。

**校验**：400 userId 为空 / 删除自己；404 用户不存在。

### 11.5 `POST /api/admin/users/{userId}/revoke`

```java
public Result<Void> revoke(String openId) {
    if (openId == null || openId.isBlank()) return Result.error(400, "userId 不能为空");
    User u = userMapper.selectById(openId);
    if (u == null) return Result.error(404, "用户不存在");
    int cur = u.getTokenVersion() == null ? 0 : u.getTokenVersion();
    u.setTokenVersion(cur + 1);
    userMapper.updateById(u);
    return Result.ok();
}
```

等价 SQL：`UPDATE t_user SET token_version = token_version + 1 WHERE open_id = ?;`
（实际是整行回写，但只有这一列变化。）

**有效性依据**（`JwtInterceptor.loadAndVerify`）：

```java
int dbVer = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
if (dbVer != info.tokenVersion()) {
    throw new RevokedException("token version mismatch");   // → 401 "登录已失效，请重新登录"
}
```

> **这是直连 SQL 服务最容易破坏的一点**：任何 `UPDATE t_user ...` 若把 `token_version` 写回 `NULL`/`0`，会让此前所有被撤销的 token 复活。安全写法：要么不碰该列，要么 `token_version = token_version + 1`。

---

## 12. 操作 11 — 订单写入（`/api/orders/*`）

### 12.1 `POST /api/orders/{id}/confirm`（整单确认，`@Transactional`）

```java
// 必须本家庭成员，且必须是 OWNER/CHEF —— 注意：没有全局管理员 is_chef 旁路
FamilyMember me = memberMapper.selectOne(... eq(order.familyId).eq(userId));
if (me == null) return Result.error(403, "无权操作");
if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
    return Result.error(403, "只有创建者或主厨才能确认");
}
if (order.getStatus() != null && order.getStatus() == 1) {
    return Result.error(400, "订单已确认");
}

LocalDateTime now = LocalDateTime.now();
menuItemMapper.update(null, new LambdaUpdateWrapper<MenuItem>()
        .eq(MenuItem::getOrderId, orderId)
        .notIn(MenuItem::getStatus, -1, 2)
        .set(MenuItem::getStatus, 1)
        .set(MenuItem::getConfirmedBy, userId)
        .set(MenuItem::getConfirmedAt, now));
syncOrderStatus(orderId);
```

**写入 1（批量）**

```sql
UPDATE t_menu_item
   SET status = 1, confirmed_by = :operatorOpenId, confirmed_at = :now
 WHERE order_id = :orderId AND status NOT IN (-1, 2);
```

**写入 2**：`syncOrderStatus(orderId)`（见 §12.5）。

**注意**：这条批量 UPDATE **不 `version = version + 1`**（与 `MenuItemMapper.confirmWithLock` 不同）。

### 12.2 `POST /api/orders/items/{id}/confirm`（单品确认，`@Transactional`）

```java
User operator = userMapper.selectById(userId);
boolean isGlobalAdmin = operator != null && operator.getIsChef() != null && operator.getIsChef() == 1;
if (!isGlobalAdmin) {
    FamilyMember me = memberMapper.selectOne(... item.familyId + userId);
    if (me == null) return Result.error(403, "无权操作");
    if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole())) {
        return Result.error(403, "只有创建者或主厨才能确认");
    }
}
if (item.getStatus() != null && item.getStatus() == -1) {
    return Result.error(400, "菜品已撤销，不能修改状态");     // -1 不可再改
}
item.setStatus(1);
item.setConfirmedBy(userId);
item.setConfirmedAt(LocalDateTime.now());
menuItemMapper.updateById(item);
syncOrderStatus(item.getOrderId());
```

**写入**：`UPDATE t_menu_item SET <所有非 null 列，含 status/confirmed_by/confirmed_at/user_id/dish_name/price/...> WHERE id = ?`。
`version` **不递增**（乐观锁字段在这里被绕过）。
**权限模型与 12.1 不同**：这里 `t_user.is_chef = 1` 的全局管理员可以确认**任意家庭**的菜品。

### 12.3 `POST /api/orders/items/{id}/reject`（单品驳回，`@Transactional`）

与 12.2 完全同构，只有一行不同：

```java
item.setStatus(2);             // -1=已撤销 0=已下单 1=已确认   →  2 = 已驳回
item.setConfirmedBy(userId);
item.setConfirmedAt(LocalDateTime.now());
menuItemMapper.updateById(item);
syncOrderStatus(item.getOrderId());
```

**`status = 2` 这个取值不在实体注释里**（`MenuItem` 注释只写了 `-1=已撤销 0=已下单 1=已确认`），但订单读接口把它算成 `rejectedCount`。见 §13、§21。

### 12.4 `PUT /api/orders/{id}`（管理端改单，`@Transactional`）

```java
if (body == null) return Result.error(400, "请求内容为空");

// 下单者：只能改为本家庭（订单所属家庭）内的成员
if (body.get("userId") != null) {
    String newUserId = body.get("userId").toString().trim();
    User newOwner = userMapper.selectById(newUserId);
    if (newOwner == null) return Result.error(404, "用户不存在");
    FamilyMember mem = memberMapper.selectOne(... order.familyId + newUserId);
    if (mem == null) return Result.error(403, "只能选择当前家庭内的成员作为下单者");
    String nickname = mem.getNickname() != null && !mem.getNickname().isBlank()
            ? mem.getNickname() : newOwner.getNickname();     // ← 优先 t_family_member.nickname
    order.setUserId(newUserId);
    order.setUserNickname(nickname);
    // 订单内的菜品一并同步为新的下单者
    menuItemMapper.update(null, new LambdaUpdateWrapper<MenuItem>()
            .eq(MenuItem::getOrderId, orderId)
            .set(MenuItem::getUserId, newUserId)
            .set(MenuItem::getUserNickname, nickname));
}

// 订单价格（总价）
if (body.get("totalAmount") != null) {
    BigDecimal amt = new BigDecimal(body.get("totalAmount").toString());
    if (amt.compareTo(BigDecimal.ZERO) < 0) return Result.error(400, "价格不能为负数");
    order.setTotalAmount(amt);
}

// 备注
if (body.containsKey("remark")) {
    Object r = body.get("remark");
    order.setRemark(r == null ? null : r.toString());
}

order.setUpdatedAt(LocalDateTime.now());
orderMapper.updateById(order);
```

**写入**（全部在一个事务里）

```sql
-- 若传 userId
UPDATE t_menu_item SET user_id = :newUid, user_nickname = :nickname WHERE order_id = :orderId;
-- 最终
UPDATE t_order SET family_id, user_id, user_nickname, total_amount, item_count, status,
                   remark, confirmed_by, confirmed_at, created_at, updated_at = :now
 WHERE id = :orderId;
```

**语义要点**
* `status` **不能手动改**（方法注释明确："订单状态由菜品状态自动同步，不允许手动改"）——body 里给 `status` 会被忽略。
* `item_count` **不重算**（即使改了 `totalAmount`）。
* `total_amount` 改了**不会**联动改任何 `t_menu_item.price`。
* `remark` 是"存在即覆盖"，可以置 `NULL`（`body.containsKey("remark")` 判断）。
* 错误码：400 orderId 空/body 空/价格格式错误/负数；404 订单或用户不存在；403 新下单者不是本家庭成员。

### 12.5 `syncOrderStatus(orderId)`（私有，被 12.1/12.2/12.3 调用）

```java
private void syncOrderStatus(Long orderId) {
    if (orderId == null) return;
    List<MenuItem> items = menuItemMapper.selectList(... eq(MenuItem::getOrderId, orderId));
    if (items.isEmpty()) return;                       // 空订单不动
    int pending = 0;
    for (MenuItem mi : items) {
        if (mi.getStatus() == null || mi.getStatus() == 0) pending++;
    }
    Order order = orderMapper.selectById(orderId);
    if (order == null) return;
    if (order.getStatus() != null && order.getStatus() == -1) return;   // 已撤销订单不再变

    LocalDateTime now = LocalDateTime.now();
    if (pending > 0) {
        if (order.getStatus() == null || order.getStatus() != 0) {
            order.setStatus(0);  order.setUpdatedAt(now);  orderMapper.updateById(order);
        }
    } else {
        if (order.getStatus() == null || order.getStatus() != 1) {
            order.setStatus(1);  order.setUpdatedAt(now);
            if (order.getConfirmedAt() == null) order.setConfirmedAt(now);
            orderMapper.updateById(order);
        }
    }
}
```

**规则（务必照抄）**
* `pending` = 状态为 `NULL` 或 `0` 的 item 条数。
* `status = 2`（驳回）和 `status = -1`（撤销）**都不算 pending**。
* 有 pending → 订单 `status = 0`；无 pending → 订单 `status = 1`。
* **订单 `confirmed_by` 永远不会被写**（`syncOrderStatus` 只 setStatus/setUpdatedAt/setConfirmedAt）。
* `confirmed_at` **只在订单从未有过 confirmed_at 时写一次**（`if (order.getConfirmedAt() == null)`）。
* `status = -1` 的订单是"冻结"的：任何 sync 都不动它。

### 12.6 `DELETE /api/orders/{id}`（`@Transactional`）

```java
menuItemMapper.delete(new LambdaQueryWrapper<MenuItem>().eq(MenuItem::getOrderId, orderId));
orderMapper.deleteById(orderId);
```

```sql
DELETE FROM t_menu_item WHERE order_id = :orderId;
DELETE FROM t_order      WHERE id = :orderId;
```

**硬删除**。未清理：`t_menu_item_rating`（`menu_item_id` 悬挂）、`t_push_log` / `t_push_pending`（`menu_item_id` 悬挂）。
校验：401 未登录；400 orderId 空；404 订单不存在。**没有家庭归属校验**（ADMIN/CHEF token 即可删任意订单）。

---

## 13. 操作 12 — `GET /api/dishes/manage/all`

```java
public Result<List<Dish>> listAll() {
    Long familyId = com.example.order.common.UserContext.getFamily();
    if (familyId == null) return Result.ok(new ArrayList<>());
    return Result.ok(dishMapper.selectList(
            new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getFamilyId, familyId)
                    .orderByAsc(Dish::getCategoryId)
                    .orderByAsc(Dish::getId)));
}
```

**SQL**

```sql
SELECT id, family_id, name, category_id, image_emoji, description, spice_level,
       price, status, created_at, updated_at
  FROM t_dish
 WHERE family_id = :familyId
 ORDER BY category_id ASC, id ASC;
```

**含全部 status（上架 + 下架）**。**没有价格回填副作用**（区别于 §14）。

**JSON `data` 是数组，每项字段（`Dish` 实体，`map-underscore-to-camel-case`）**

| JSON 字段 | 类型 | 可空 | 来源列 |
|---|---|---|---|
| `id` | number | 否 | `id` |
| `familyId` | number | 否 | `family_id` |
| `name` | string | 否 | `name` |
| `categoryId` | number | 否 | `category_id` |
| `imageEmoji` | string | **可 null**（列可空，默认 `''`） | `image_emoji` |
| `description` | string | 可 null | `description` |
| `spiceLevel` | number | 可 null | `spice_level` |
| `price` | number（decimal→JSON number） | 可 null | `price` |
| `status` | number | 可 null | `status` |
| `createdAt` | string（`LocalDateTime.toString()` → `2024-05-01T12:30:45`） | 可 null | `created_at` |
| `updatedAt` | string | 可 null | `updated_at` |

`familyId == null`（无活跃家庭）→ 返回 `[]`。

---

## 14. 操作 13 — `GET /api/dishes/{id}`（详情，**含配菜配方**）

```java
Long familyId = com.example.order.common.UserContext.getFamily();
Dish dish = dishMapper.selectById(id);
if (dish == null) return Result.error(404, "菜谱不存在");
if (familyId != null && dish.getFamilyId() != null && !dish.getFamilyId().equals(familyId)) {
    return Result.error(403, "无权访问其他家庭菜谱");
}

List<DishIngredient> dis = diMapper.selectList(... eq(DishIngredient::getDishId, id));
if (dis.isEmpty()) {
    return Result.ok(Map.of("dish", dish, "ingredients", List.of()));
}

List<Ingredient> ingredients = ingMapper.selectBatchIds(...);
Map<Long, Ingredient> map = ...;
List<Map<String, Object>> items = dis.stream().map(di -> {
    Ingredient ing = map.get(di.getIngId());
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("ingId", di.getIngId());
    item.put("name", ing != null ? ing.getName() : "?");
    item.put("amount", di.getAmount());
    item.put("unit", di.getUnit());
    item.put("price", ing != null ? ing.getPrice() : BigDecimal.ZERO);
    item.put("emoji", ing != null ? ing.getEmoji() : "");
    return item;
}).toList();

Map<String, Object> result = new LinkedHashMap<>();
result.put("dish", dish);
result.put("ingredients", items);
return Result.ok(result);
```

**SQL**

```sql
SELECT * FROM t_dish WHERE id = :id;
SELECT * FROM t_dish_ingredient WHERE dish_id = :id;                      -- 无 ORDER BY（顺序 = 主键物理序，不保证）
SELECT * FROM t_ingredient WHERE id IN (:ingIds);
```

**JSON**

```jsonc
{
  "code": 0, "message": "ok",
  "data": {
    "dish": { /* 同 §13 的 Dish 全部字段 */ },
    "ingredients": [
      {
        "ingId": 12,            // number
        "name": "土豆",          // string；配菜被删则 "?"
        "amount": 200.000,       // number（decimal(10,3)）
        "unit": "克",            // string；来自 t_dish_ingredient.unit（冗余列，可为 ""）
        "price": 6.00,           // number；来自 t_ingredient.price ⚠️ 当前价，非快照
        "emoji": "🥔"            // string；配菜不存在则 ""
      }
    ]
  }
}
```

**要点**
* **`ingredients` 是嵌套数组，`dish` 是嵌套对象**——不是把 ingredients 平铺进 dish。
* 没有配方时 `ingredients` 是 `[]`（`Map.of(...)` 的无序 Map，字段顺序不保证）。
* 校验：404 菜不存在；403 当 `dish.family_id != 当前家庭`（且当前家庭非 null）。
* **不做价格回填**（与 §14 的列表接口不同）。
* `price` / `emoji` 是**读时联查 `t_ingredient` 的实时值**，不是 `t_dish_ingredient` 上的快照（`t_dish_ingredient` 只冗余了 `unit`）。

---

## 15. 操作 14 — `GET /api/dishes`

```java
public Result<List<Dish>> list() {
    Long familyId = com.example.order.common.UserContext.getFamily();
    if (familyId == null) return Result.ok(new ArrayList<>());
    List<Dish> dishes = dishMapper.selectList(
            new LambdaQueryWrapper<Dish>()
                    .eq(Dish::getFamilyId, familyId)
                    .eq(Dish::getStatus, 1)
                    .orderByAsc(Dish::getCategoryId)
                    .orderByAsc(Dish::getId));
    // 补齐冗余价格
    for (Dish d : dishes) {
        if (d.getPrice() == null || d.getPrice().compareTo(BigDecimal.ZERO) == 0) {
            d.setPrice(computeDishPrice(d.getId()));
            // 更新冗余字段
            dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                    .eq(Dish::getId, d.getId())
                    .set(Dish::getPrice, d.getPrice()));
        }
    }
    return Result.ok(dishes);
}
```

**SQL**

```sql
SELECT ... FROM t_dish WHERE family_id = :fid AND status = 1 ORDER BY category_id ASC, id ASC;
-- 对每个 price IS NULL 或 price = 0 的菜：
SELECT * FROM t_dish_ingredient WHERE dish_id = :dishId;
SELECT * FROM t_ingredient WHERE id IN (...);
UPDATE t_dish SET price = :computed WHERE id = :dishId;    -- ⚠️ 读接口里的写操作
```

`computeDishPrice`：

```java
BigDecimal total = BigDecimal.ZERO;
for (DishIngredient di : dis) {
    Ingredient ing = map.get(di.getIngId());
    if (ing != null) total = total.add(ing.getPrice().multiply(di.getAmount()));
}
return total.setScale(2, java.math.RoundingMode.HALF_UP);
```

* **只返回 `status = 1` 的菜。**
* 返回字段与 §13 的 `Dish` 完全相同。
* ⚠️ **这是一个"读接口写库"的副作用**：`price` 为 `NULL` 或 `0` 的菜会被就地回填（`HALF_UP` 保留 2 位；无配方时 `computeDishPrice` 返回 `BigDecimal.ZERO`，**scale=0**，写回为 `0`）。
* ⚠️ **直连 SQL 服务若只读不写，就要自己算价格**，否则新加入的公共菜（价格 0）永远显示 0 元。

---

## 16. 操作 15 — `GET /api/categories`

```java
@GetMapping
public Result<List<Category>> list() {
    LambdaQueryWrapper<Category> q = new LambdaQueryWrapper<>();
    q.eq(Category::getStatus, 1).orderByAsc(Category::getSort);
    return Result.ok(categoryMapper.selectList(q));
}
```

```sql
SELECT id, name, sort, status FROM t_category WHERE status = 1 ORDER BY sort ASC;
```

* **纯全局表，无 family 过滤**，也**不需要登录**（`isPublic` 含 `/api/categories`）。
* 返回字段：`id`(number)、`name`(string)、`sort`(number|null)、`status`(number|null)。
  ⚠️ **实体 `Category` 没有 `createdAt` 字段 → JSON 里没有 `createdAt`**。
* `t_category.status`：`1` = 可见（过滤条件），其余（含 0）不返回。**代码里没有 second branch**，所以只有"等于 1"这一条规则。

---

## 17. 操作 16 — `GET /api/ingredients/public`

```java
/** 公开列表（仅显示当前家庭启用的配菜） */
public Result<List<Ingredient>> listPublic() {
    Long familyId = com.example.order.common.UserContext.getFamily();
    if (familyId == null) return Result.ok(java.util.Collections.emptyList());
    return Result.ok(mapper.selectList(
            new LambdaQueryWrapper<Ingredient>()
                    .eq(Ingredient::getFamilyId, familyId)
                    .eq(Ingredient::getStatus, 1)
                    .orderByAsc(Ingredient::getCategoryId)
                    .orderByAsc(Ingredient::getId)));
}
```

```sql
SELECT id, family_id, name, category_id, unit, price, emoji, status, created_at, updated_at
  FROM t_ingredient
 WHERE family_id = :fid AND status = 1
 ORDER BY category_id ASC, id ASC;
```

### ⚠️ "public" 在这里的含义

**不是**公共配菜库！它查的是 **`t_ingredient`**（家庭配菜表），过滤条件 = `family_id = 当前活跃家庭` AND `status = 1`。
"public" 指的是"**对家庭成员公开可见的启用配菜**"（相对于 `/manage/all` 含停用项的版本）。
真正的公共库是 `t_public_ingredient`，走 `/api/public/ingredients`（见 §9、§19）。

**JSON 字段**（`Ingredient` 实体）

| JSON | 类型 | 可空 |
|---|---|---|
| `id` | number | 否 |
| `familyId` | number | 否 |
| `name` | string | 否 |
| `categoryId` | number | 否 |
| `unit` | string | 否（DB NOT NULL） |
| `price` | number | 可 null |
| `emoji` | string | 可 null |
| `status` | number | 可 null |
| `createdAt` / `updatedAt` | string | 可 null |

无活跃家庭 → `[]`。

**相关接口**（同一个 service）

* `GET /api/ingredients?categoryId=` → `listByCategory`：
  ```java
  .eq(familyId != null, Ingredient::getFamilyId, familyId)
  .eq(categoryId != null, Ingredient::getCategoryId, categoryId)
  .eq(Ingredient::getStatus, 1)
  .orderByAsc(Ingredient::getId)
  ```
  ⚠️ **`familyId == null` 时 family 条件被整个跳过 → 会返回所有家庭的启用配菜**（数据泄漏点）。
* `GET /api/ingredients/{id}` → `selectById`，无归属校验，404 时 `Result.error(404,"配菜不存在")`。
* `GET /api/ingredients/manage/all` → 当前家庭**全部状态**，`ORDER BY category_id ASC, id ASC`。

---

## 18. 操作 17 — `GET /api/ingredient-categories`

```java
public Result<List<IngredientCategory>> list() {
    Long familyId = com.example.order.common.UserContext.getFamily();
    if (familyId == null) return Result.ok(java.util.Collections.emptyList());
    return Result.ok(mapper.selectList(
            new LambdaQueryWrapper<IngredientCategory>()
                    .eq(IngredientCategory::getFamilyId, familyId)
                    .orderByAsc(IngredientCategory::getSort)
                    .orderByAsc(IngredientCategory::getId)));
}
```

```sql
SELECT id, family_id, name, emoji, sort, created_at
  FROM t_ingredient_category
 WHERE family_id = :fid
 ORDER BY sort ASC, id ASC;
```

**JSON 字段**：`id`(number)、`familyId`(number)、`name`(string)、`emoji`(string|null)、`sort`(number|null)、`createdAt`(string|null)。
（`IngredientCategory` 实体**没有 `updatedAt`**，表也没有该列。）

---

## 19. 操作 18 — 公共库管理端读接口

### 19.1 `GET /api/public/dishes/admin/all`

```java
public Result<List<PublicDish>> listAll() {
    List<PublicDish> list = publicDishMapper.selectList(
        new LambdaQueryWrapper<PublicDish>().orderByDesc(PublicDish::getId));
    return Result.ok(list);
}
```

```sql
SELECT id, name, category_id, image_emoji, description, spice_level, status,
       created_at, updated_at
  FROM t_public_dish
 ORDER BY id DESC;
```

**含下架（status=0）**，无分页，无 family 概念。
字段：`id`(number)、`name`(string)、`categoryId`(number|null)、`imageEmoji`(string|null)、`description`(string|null)、`spiceLevel`(number|null)、`status`(number|null)、`createdAt`(string|null)、`updatedAt`(string|null)。
（对比 `GET /api/public/dishes`：`WHERE status = 1 ORDER BY category_id ASC, id DESC`。）

### 19.2 `GET /api/public/ingredients/admin/all`

```sql
SELECT id, name, category_id, unit, price, emoji, status, created_at, updated_at
  FROM t_public_ingredient
 ORDER BY category_id ASC, id ASC;
```

含下架。
（对比 `GET /api/public/ingredients`：`WHERE status = 1 ORDER BY category_id ASC, id ASC`。）

### 19.3 `GET /api/public/ingredients/categories`

```java
/** 公共配菜分类（family_id=0 的全局参考分类） */
public Result<List<IngredientCategory>> listCategories() {
    List<IngredientCategory> list = ingredientCategoryMapper.selectList(
        new LambdaQueryWrapper<IngredientCategory>()
            .eq(IngredientCategory::getFamilyId, 0L)
            .orderByAsc(IngredientCategory::getSort)
            .orderByAsc(IngredientCategory::getId));
    return Result.ok(list);
}
```

```sql
SELECT id, family_id, name, emoji, sort, created_at
  FROM t_ingredient_category
 WHERE family_id = 0
 ORDER BY sort ASC, id ASC;
```

⚠️ **`family_id = 0` 是"公共分类"这个约定的**唯一**实现方式**。直连 SQL 服务新增公共分类时必须写 `family_id = 0`，否则 `to-family` 的分类名映射会失效（见 §9.4）。
注意：这里的字段含 `familyId`（值恒为 0）。

---

## 20. 操作 19 — `GET /api/admin/users`

`AdminUserService.listUsers()`。三次全表扫描后在内存里拼装 + 中文拼音排序：

```java
List<User> users = userMapper.selectList(... orderByAsc(User::getOpenId));
List<Family> fams = familyMapper.selectList(... orderByAsc(Family::getId));
Map<Long, Family> famMap = ...;
Map<String, List<FamilyMember>> membersByUser = memberMapper.selectList(null).stream()
        .collect(Collectors.groupingBy(FamilyMember::getUserId));
```

**SQL**

```sql
SELECT * FROM t_user ORDER BY open_id ASC;
SELECT * FROM t_family ORDER BY id ASC;
SELECT * FROM t_family_member;                      -- 全表
```

拼装逻辑：

```java
entry.put("userId", u.getOpenId());
entry.put("nickname", u.getNickname());
entry.put("avatarUrl", u.getAvatarUrl());
entry.put("phone", u.getPhone());
entry.put("isChef", u.getIsChef() != null && u.getIsChef() == 1);          // ← boolean
entry.put("allowPush", u.getAllowPush() != null && u.getAllowPush() == 1); // ← boolean
entry.put("activeFamilyId", u.getActiveFamilyId());
entry.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");

List<FamilyMember> ms = membersByUser.getOrDefault(u.getOpenId(), new ArrayList<>());
ms.sort(Comparator.comparing(m -> m.getJoinedAt() == null ? LocalDateTime.MIN : m.getJoinedAt()));
for (FamilyMember m : ms) {
    Family f = famMap.get(m.getFamilyId());
    if (f == null) continue;                     // 家庭成员指向已删家庭 → 静默跳过
    fm.put("familyId", f.getId());
    fm.put("familyName", f.getName());
    fm.put("code", f.getCode());
    fm.put("role", m.getRole());
    fm.put("isOwner", "OWNER".equals(m.getRole()));
    fm.put("active", u.getActiveFamilyId() != null && u.getActiveFamilyId().equals(f.getId()));
}
// 主家庭：优先活跃家庭，其次最早加入的家庭
String primary = "";
Map<String, Object> activeFam = families.stream().filter(x -> Boolean.TRUE.equals(x.get("active")))
        .findFirst().orElse(null);
if (activeFam != null) primary = String.valueOf(activeFam.get("familyName"));
else if (!families.isEmpty()) primary = String.valueOf(families.get(0).get("familyName"));
entry.put("families", families);
entry.put("primaryFamilyName", primary);
```

最后按 `primaryFamilyName` 中文拼音（`Collator.getInstance(Locale.CHINA)`）排序，空串排最后，同家庭按 `userId` 字典序。

**JSON 精确形状**

```jsonc
{
  "userId": "mock_test1234",      // string（openid）
  "nickname": "张三",              // string | null
  "avatarUrl": "avatar-3",        // string | null（存的是 key，不是 URL）
  "phone": null,                  // string | null
  "isChef": true,                 // boolean（由 tinyint 转换！不是 0/1）
  "allowPush": false,             // boolean
  "activeFamilyId": 2,            // number | null
  "createdAt": "2024-05-01T12:30:45",  // string；为 null 时是空字符串 ""
  "families": [                   // 按 joinedAt 升序；最多可能为空数组
    { "familyId": 2, "familyName": "温馨小家", "code": "AB3K9P",
      "role": "CHEF", "isOwner": false, "active": true }
  ],
  "primaryFamilyName": "温馨小家"   // string；无家庭 → ""
}
```

**要点**
* `isChef` / `allowPush` 是**真 boolean**，不是数字——直连服务必须转换（`!!Number(v)`）。
* `createdAt` 是**字符串**，且 null 时是 `""`（不是 null）。
* `primaryFamilyName` 一定是 `string`（无家庭时为 `""`）。
* 每个用户的 `families[]` 里**没有 `joinedAt` 字段**（`GET /api/admin/families/overview` 的 members 里有）。
* 排序在**应用层**做（中文 Collator），SQL 直出的顺序不等于接口顺序。

---

## 21. 操作 20 — `GET /api/admin/families` 与 `/families/overview`

### 21.1 `GET /api/admin/families`

```java
List<Family> fams = familyMapper.selectList(... orderByAsc(Family::getId));
Map<Long, Long> memberCounts = ...;   // groupingBy(FamilyMember::getFamilyId, Collectors.counting())
e.put("familyId", f.getId());
e.put("name", f.getName());
e.put("code", f.getCode());
e.put("memberCount", memberCounts.getOrDefault(f.getId(), 0L));
```

```sql
SELECT * FROM t_family ORDER BY id ASC;
SELECT * FROM t_family_member WHERE family_id IN (:ids);
```

**JSON**：`[{familyId: number, name: string, code: string, memberCount: number}]`
（`memberCount` 是 `Long` → JSON number；无成员时是 `0`。**没有 `createdAt`、没有 ownerName**。）

### 21.2 `GET /api/admin/families/overview`

```java
e.put("familyId", f.getId());
e.put("name", f.getName());
e.put("code", f.getCode());
e.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : "");

List<FamilyMember> ms = membersByFamily.getOrDefault(f.getId(), new ArrayList<>());
ms.sort(Comparator.comparing(m -> m.getJoinedAt() == null ? LocalDateTime.MIN : m.getJoinedAt()));

String ownerName = "", chefName = "";
for (FamilyMember m : ms) {
    User u = userMap.get(m.getUserId());
    String nickname = m.getNickname() != null && !m.getNickname().isBlank()
            ? m.getNickname() : (u != null ? u.getNickname() : ("用户#" + m.getUserId()));
    mm.put("userId", m.getUserId());
    mm.put("nickname", nickname);
    mm.put("role", m.getRole());
    mm.put("joinedAt", m.getJoinedAt() != null ? m.getJoinedAt().toString() : "");
    if ("OWNER".equals(m.getRole()) && ownerName.isEmpty()) ownerName = nickname;
    if ("CHEF".equals(m.getRole()) && chefName.isEmpty()) chefName = nickname;
}
e.put("ownerName", ownerName);
e.put("chefName", chefName);
e.put("memberCount", ms.size());
e.put("members", members);
```

**SQL**

```sql
SELECT * FROM t_family ORDER BY id ASC;
SELECT * FROM t_family_member WHERE family_id IN (:ids);
SELECT * FROM t_user;                       -- 全表，用于昵称回退
```

**JSON**

```jsonc
{
  "familyId": 2,
  "name": "温馨小家",
  "code": "AB3K9P",
  "createdAt": "2024-05-01T12:30:45",       // string；null → ""
  "ownerName": "张三",                       // string；无 OWNER → ""
  "chefName": "李四",                        // string；无 CHEF → ""
  "memberCount": 3,                          // number（int）
  "members": [
    { "userId": "mock_abc", "nickname": "张三", "role": "OWNER",
      "joinedAt": "2024-05-01T12:30:45" }     // joinedAt null → ""
  ]
}
```

**昵称三级回退（重要）**：`t_family_member.nickname`（非 blank） → `t_user.nickname` → `"用户#" + userId`。
排序：家庭成员按 `joined_at` 升序（**注意这个接口是 CPU 排序，DB 无需 ORDER BY**）。

---

## 22. 操作 21 — 订单读接口

### 22.1 `GET /api/orders` / `GET /api/orders/today`

```java
private List<Map<String, Object>> listOrdersByFamily(Long familyId, LocalDate date) {
    LambdaQueryWrapper<Order> q = new LambdaQueryWrapper<Order>()
            .eq(Order::getFamilyId, familyId)
            .orderByDesc(Order::getCreatedAt);
    if (date != null) {
        q.ge(Order::getCreatedAt, date.atStartOfDay())
         .lt(Order::getCreatedAt, date.plusDays(1).atStartOfDay());
    }
    List<Order> orders = orderMapper.selectList(q);
    ...
    int confirmedCount = 0, rejectedCount = 0;
    for (MenuItem mi : items) {
        if (mi.getStatus() != null && mi.getStatus() == 1) confirmedCount++;
        else if (mi.getStatus() != null && mi.getStatus() == 2) rejectedCount++;
    }
```

```sql
-- GET /api/orders
SELECT * FROM t_order WHERE family_id = :fid ORDER BY created_at DESC;
-- GET /api/orders/today  (LocalDate.now() = 服务器本地时区)
SELECT * FROM t_order WHERE family_id = :fid
   AND created_at >= :today00:00 AND created_at < :tomorrow00:00
 ORDER BY created_at DESC;
-- 两者共同：
SELECT * FROM t_menu_item WHERE order_id IN (:orderIds);
```

**`confirmedCount` / `rejectedCount` 是读时计算，不是冗余列**：

* `confirmedCount` = 该订单下 `status == 1` 的 item 数
* `rejectedCount` = `status == 2` 的 item 数
* `status == -1`（已撤销）的 item **两个计数都不计**，也**不影响** `itemCount`（后者是下单时的快照 `t_order.item_count`）

**JSON（数组元素）**

```jsonc
{
  "orderId": 31,                 // number
  "familyId": 2,                 // number
  "userId": "mock_abc",          // string
  "userNickname": "张三",         // string | null
  "totalAmount": 88.50,          // number（冗余列 t_order.total_amount）
  "itemCount": 4,                // number（冗余列 t_order.item_count；注意 ≠ items.length 时说明有历史差异）
  "confirmedCount": 2,           // number（读时算）
  "rejectedCount": 1,            // number（读时算）
  "status": 0,                   // number（t_order.status）
  "remark": "不要辣",             // string | null
  "createdAt": "2024-05-01T12:30:45",  // string；null → ""
  "confirmedAt": "",                    // string；null → ""（注意是空串不是 null）
  "items": [
    {
      "itemId": 101,             // number
      "dishId": 7,               // number | null（null = 自定义菜）
      "dishName": "鱼香肉丝",     // string
      "dishEmoji": "🍲",         // string | null
      "spiceLevel": 1,           // number | null
      "price": 22.00,            // number | null
      "status": 1,               // number | null
      "userNickname": "张三",     // string | null
      "createdAt": "2024-05-01T12:30:45"   // string；null → ""
      // ⚠️ 列表接口的 items 里 **没有** customIngs / remark
    }
  ]
}
```

`familyId == null` → `[]`。

### 22.2 `GET /api/orders/{orderId}`（详情）

```java
Order order = orderMapper.selectById(orderId);
if (order == null) return Result.error(404, "订单不存在");
Long curFamily = UserContext.getFamily();
if (curFamily == null || !curFamily.equals(order.getFamilyId())) {
    return Result.error(403, "无权查看该订单");
}
List<MenuItem> items = menuItemMapper.selectList(... eq(MenuItem::getOrderId, orderId).orderByAsc(MenuItem::getId));
```

与列表项字段**完全相同**，外加：

| 额外字段 | 类型 | 说明 |
|---|---|---|
| `canConfirm` | **boolean** | 全局管理员（`t_user.is_chef == 1`）→ `true`；否则该家庭 `t_family_member.role ∈ {OWNER, CHEF}` |
| `items[].customIngs` | string \| null | 自定义菜配料 JSON 原文 |
| `items[].remark` | string \| null | 单品备注 |

```java
boolean canConfirm = false;
User curUser = userMapper.selectById(curUserId);
if (curUser != null && curUser.getIsChef() != null && curUser.getIsChef() == 1) {
    canConfirm = true;
} else {
    FamilyMember me = memberMapper.selectOne(... order.familyId + curUserId);
    canConfirm = me != null && ("OWNER".equals(me.getRole()) || "CHEF".equals(me.getRole()));
}
```

* items 按 `id ASC` 排序（列表接口**不排序**）。
* 403 条件：当前家庭为 null 或 ≠ 订单家庭。

---

## 23. 操作 22 — `GET /api/me/chef-status`

```java
@GetMapping("/me/chef-status")
public Result<?> chefStatus() {
    return chefService.chefStatus(UserContext.getFamily());
}

public Result<Map<String, Object>> chefStatus(Long familyId) {
    String userId = UserContext.get();
    User chef = null;
    if (familyId != null) {
        FamilyMember chefMember = familyMemberMapper.selectOne(... eq(familyId).eq(role, "CHEF"));
        if (chefMember != null) chef = userMapper.selectById(chefMember.getUserId());
    } else {
        // 回退：全局查询
        chef = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getIsChef, 1));
    }
    boolean hasChef = chef != null;
    boolean isMe = (userId != null && chef != null && chef.getOpenId().equals(userId));
    result.put("hasChef", hasChef);
    result.put("chefId", hasChef ? chef.getOpenId() : null);
    result.put("chefNickname", hasChef && chef.getNickname() != null ? chef.getNickname() : "");
    result.put("isMe", isMe);
    return Result.ok(result);
}
```

**SQL**

```sql
SELECT * FROM t_family_member WHERE family_id = :fid AND role = 'CHEF';   -- selectOne：>1 行会抛异常
SELECT * FROM t_user WHERE open_id = :chefUserId;
-- familyId 为 NULL 时的回退：
SELECT * FROM t_user WHERE is_chef = 1;      -- selectOne：>1 行 → TooManyResultsException → 500
```

**JSON**

```jsonc
{
  "hasChef": true,          // boolean
  "chefId": "mock_abc",     // string | null（openid；无主厨 → null）
  "chefNickname": "李四",    // string（null 时是 ""，永远不返回 null）
  "isMe": false             // boolean
}
```

**⚠️ 与登录态的关系**：该接口**在 `isPublic` 白名单里吗？——不在**（`/api/me/*` 未列入 `isPublic`），所以需要带 token（USER/CHEF 均可），`UserContext.get()` 才有值。
**⚠️ `hasChef` 决定能不能下单**：`MenuItemService.addItem` / `OrderService.createOrderFromCart` 都会：

```java
if (!chefService.hasChef(familyId)) {
    return Result.error(400, "当前家庭还没有主厨，请先让家庭成员认领主厨再下单");
}
// hasChef → SELECT COUNT(*) FROM t_family_member WHERE family_id=? AND role='CHEF'
```

---

## 24. 状态码总表（按代码实现，非注释推断）

| 列 | 取值 | 含义 | 代码依据（原文） |
|---|---|---|---|
| `t_dish.status` | `1` | 上架（默认） | `.eq(Dish::getStatus, 1)`；`int newStatus = dish.getStatus() == 1 ? 0 : 1;` |
| | `0` | 下架 | 同上（非 1 → 1） |
| `t_ingredient.status` | `1` | 启用（默认） | `ing.setStatus(ing.getStatus() == 1 ? 0 : 1);`；`listPublic` `.eq(Ingredient::getStatus, 1)` |
| | `0` | 停用 | 同上 |
| `t_public_dish.status` | `1` | 上架（create 强制） | `dish.setStatus(1);`；`// 0下架 1上架` |
| | `0` | 下架 | `listPublic()` `.eq(PublicDish::getStatus, 1)` |
| `t_public_ingredient.status` | `1` | 上架（默认） | `if (ing.getStatus() == null) ing.setStatus(1);` |
| | `0` | 下架 | `listPublic()` `.eq(PublicIngredient::getStatus, 1)` |
| `t_order.status` | `0` | **待确认**（DDL 默认） | `if (pending > 0) { ... order.setStatus(0) ... }`；`order.setStatus(0);`（创建） |
| | `1` | **已确认** | `if (order.getStatus() != null && order.getStatus() == 1) return Result.error(400, "订单已确认");`；`order.setStatus(1)`（sync） |
| | `-1` | **已撤销** | `if (order.getStatus() != null && order.getStatus() == -1) return;`（sync 冻结）。⚠️ 没有任何接口会把订单置 -1，只能手工 SQL |
| `t_menu_item.status` | `-1` | **已撤销** | 实体注释 `/** -1=已撤销 0=已下单 1=已确认 */`；`cancelWithLock` → `SET status=-1 ... AND status IN (0,1)` |
| | `0` | **已下单/待确认**（DDL 默认） | `item.setStatus(0); // 已下单` |
| | `1` | **已确认** | `item.setStatus(1);` + `confirmWithLock(..., 1, ...)` |
| | `2` | **已驳回** ⚠️ 实体注释里没写 | `rejectItem`: `item.setStatus(2);`；计数 `else if (mi.getStatus() != null && mi.getStatus() == 2) rejectedCount++;` |
| `t_category.status` | `1` | 可见 | `q.eq(Category::getStatus, 1)` |
| | 其他 | 隐藏 | 无第二个分支（只有"等于 1"） |
| `t_family_member.role` | `OWNER`/`CHEF`/`MEMBER` | 大写字符串，varchar(16) DEFAULT 'MEMBER' | `if (!"OWNER".equals(me.getRole()) && !"CHEF".equals(me.getRole()))` |
| `t_user.is_chef` | `1`/`0` | 全局主厨镜像（任一家庭为 CHEF 即 1） | `syncGlobalChef`: `upd.setIsChef(cnt != null && cnt > 0 ? 1 : 0);` |
| `t_user.allow_push` | `1`/`0` | 推送订阅授权 | `user.setAllowPush(allow ? 1 : 0);` |
| `t_push_pending.status` | `0`/`1`/`2` | pending / done / abandoned | 注释 `/** 0=pending 1=done 2=abandoned */`；`if (p.getAttempts() >= 3) { p.setStatus(2); }` |
| `t_push_log.type` | `NEW_ORDER`/`STATUS_CHANGED` | — | `saveLog(..., "NEW_ORDER", ...)` |
| `t_menu_item.version` / `t_order`（乐观锁） | int | 仅 `MenuItemMapper` 的两条手写 SQL 会 `version=version+1` | 见 §25 |

⚠️ **`statusText` 不认识 2**（`MenuItemService`）：

```java
private String statusText(Integer s) {
    if (s == null) return "-";
    return switch (s) {
        case -1 -> "已撤销";
        case 0 -> "已下单";
        case 1 -> "已确认";
        default -> "未知";        // ← status = 2（已驳回）会显示"未知"
    };
}
```

所以 `GET /api/menu/today` 里被驳回的菜 `statusText = "未知"`，而 `GET /api/orders` 里 `rejectedCount` 能正确统计。

---

## 25. 冗余列 / 快照列 与 读时计算 —— 完整清单

### 25.1 必须由写方维护的快照列

| 列 | 何时写 | 谁维护 | 直连实现必须做什么 |
|---|---|---|---|
| `t_dish.price` | 增/改菜时按配方重算 | `DishService.computeFromList` | 改了 `t_dish_ingredient` / `t_ingredient.price` 后必须重算，否则价格错 |
| `t_dish_ingredient.family_id` | 插入时 = 当前家庭 | `saveIngredients` | 必须写入正确的 family_id（`deleteFamily` 按它清理） |
| `t_dish_ingredient.unit` | 复制自入参 | `saveIngredients` | 冗余显示单位，可与 `t_ingredient.unit` 不一致 |
| `t_menu_item.price` | 下单时算一次 | `MenuItemService.addItem` / `OrderService.createOrderFromCart` | **价格快照**，之后再改配菜价不影响已下单 |
| `t_menu_item.dish_name` / `dish_emoji` / `custom_ings` | 下单时复制 | 同上 | 菜品改名/删除后历史订单仍显示旧名 |
| `t_menu_item.user_nickname` | 下单时 = `t_user.nickname` | 同上 | 昵称快照 |
| `t_order.user_nickname` | 下单时 = `t_user.nickname` | `createOrderFromCart` | 昵称快照 |
| `t_family_member.nickname` | 加入家庭时 = `t_user.nickname` | `addFamily` / `createFamily` / `FamilyService.join` | 昵称快照（**可为 NULL**） |
| `t_order.total_amount` / `item_count` | 下单时按购物车算 | `createOrderFromCart` | **之后永不重算**（改单也不重算） |
| `t_cart.price` / `dish_name` / `dish_emoji` | 加购时快照 | `CartService.addToCart` | 购物车里价格不会随菜品更新 |
| `t_user.is_chef` | 每次 `t_family_member.role` 变动后重算 | `syncGlobalChef` | **改 role 必须同步**，否则 `confirmItem/rejectItem` 的全局管理员旁路、`ChefService.isChef` 回退、`PushService` 找主厨回退都会错 |
| `t_user.active_family_id` | 创建/加入家庭、活跃家庭被删/被移出时 | 多处 | **所有家庭维度的读写都依赖它**；删除/移出后必须重算 |
| `t_user.token_version` | `revoke` / `logout` 时 +1 | `AdminUserService.revoke`、`UserController.logout` | **只增不减**，否则被撤销的 token 复活 |
| `t_menu_item.version` | 仅 `confirmWithLock` / `cancelWithLock` | `MenuItemMapper` | 走 `updateById` 的确认/驳回**不递增**——乐观锁只保护 `/api/menu/items/{id}/confirm` 与 `DELETE /api/menu/items/{id}` |

**唯一的"昵称全量同步"入口**（`NicknameSyncService.changeNicknameAndSync`，`@Transactional`）：

```java
// 1. 主表
u.setNickname(newNickname); u.setUpdatedAt(now); userMapper.updateById(u);
// 2. 家庭成员快照
familyMemberMapper.update(null, new LambdaUpdateWrapper<FamilyMember>()
        .eq(FamilyMember::getUserId, openId).set(FamilyMember::getNickname, newNickname));
// 3. 订单「下单者」快照
orderMapper.update(null, new LambdaUpdateWrapper<Order>()
        .eq(Order::getUserId, openId).set(Order::getUserNickname, newNickname));
// 4. 点菜单项「点单人」快照
menuItemMapper.update(null, new LambdaUpdateWrapper<MenuItem>()
        .eq(MenuItem::getUserId, openId).set(MenuItem::getUserNickname, newNickname));
```

对应 SQL：

```sql
UPDATE t_user        SET nickname = :nick, updated_at = NOW() WHERE open_id = :uid;
UPDATE t_family_member SET nickname = :nick WHERE user_id = :uid;
UPDATE t_order       SET user_nickname = :nick WHERE user_id = :uid;
UPDATE t_menu_item   SET user_nickname = :nick WHERE user_id = :uid;
```

> **直连 SQL 服务若"改用户昵称"，不做这 4 条 UPDATE，就会出现：家庭列表、订单、点菜单里显示的还是旧名字。**

### 25.2 读时才计算的值（**没有**对应列）

| 返回值 | 计算方式 | 位置 |
|---|---|---|
| `orders[].confirmedCount` | `COUNT(items WHERE status = 1)` | `OrderService` |
| `orders[].rejectedCount` | `COUNT(items WHERE status = 2)` | `OrderService` |
| `orders[].canConfirm` | `t_user.is_chef == 1` 或该家庭 role ∈ {OWNER,CHEF} | `OrderService.getOrderDetail` |
| `users[].isChef` / `allowPush` | tinyint → boolean | `AdminUserService.listUsers` |
| `users[].primaryFamilyName` | 活跃家庭名，否则最早加入的家庭名，否则 `""` | `AdminUserService.listUsers` |
| `users[].families[].active` | `t_user.active_family_id == family.id` | 同上 |
| `overview[].ownerName` / `chefName` | 第一个匹配 role 的成员昵称 | `listFamiliesOverview` |
| `menu/today[].statusText` | `-1/0/1` → 中文，其他 → `未知` | `MenuItemService.statusText` |
| `dish detail.ingredients[].name/price/emoji` | JOIN `t_ingredient` 实时值 | `DishService.getDetail` |
| `shopping-list` 聚合 | `t_menu_item`（status 0/1）+ `t_dish_ingredient` 或 `custom_ings` JSON 求和 | `ShoppingListService` |
| `t_menu_item` 的 `quantity` | **不存在**：`addItem`/`checkout` 把 quantity **拆成多行**（同一菜品 qty=3 → 3 行 `t_menu_item`） | `OrderService.createOrderFromCart` |

---

## 26. 事务边界清单

| 操作 | `@Transactional` | 涉及表 |
|---|---|---|
| `DishService.create` | ✅ | t_dish, t_dish_ingredient |
| `DishService.update` | ✅ | t_dish, t_dish_ingredient |
| `DishService.delete` | ✅ | t_dish, t_dish_ingredient |
| `DishService.batchSave` | ✅ | t_dish, t_dish_ingredient |
| `DishService.toggleStatus` | ❌ | t_dish（单条） |
| `IngredientService.create/update/delete/toggleStatus` | ❌ | t_ingredient（delete 还有一次 select count，**非原子**） |
| `IngredientService.batchSave` | ✅ | t_ingredient |
| `IngredientCategoryService.*` | ❌ | t_ingredient_category |
| `PublicDishService.create/update/delete/addToFamily` | ✅ | t_public_dish / t_dish |
| `PublicIngredientService.create/update/delete/addToFamily` | ✅ | t_public_ingredient / t_ingredient / t_ingredient_category |
| `AdminUserService.createFamily` | ✅ | t_family, t_family_member, t_user |
| `AdminUserService.updateFamily` | ❌ | t_family（单条） |
| `AdminUserService.deleteFamily` | ✅ | 9 张表（见 §10.3） |
| `AdminUserService.addFamily/setRole/removeFamily` | ✅ | t_family_member, t_user |
| `AdminUserService.deleteUser` | ✅ | t_family_member, t_cart, t_menu_item, t_user |
| `AdminUserService.revoke` | ❌ | t_user（单条） |
| `OrderService.createOrderFromCart` | ✅ | t_order, t_menu_item |
| `OrderService.confirmOrder/confirmItem/rejectItem` | ✅ | t_menu_item（批量/单行）, t_order |
| `OrderService.updateOrder` | ✅ | t_order, t_menu_item |
| `OrderService.deleteOrder` | ✅ | t_menu_item, t_order |
| `MenuItemService.addItem/confirm/cancel` | ✅ | t_menu_item |
| `FamilyService.create/join/leave/claimChef/resignChef/kick/loginFamilies` | ✅ | t_family, t_family_member, t_user |
| `CartService.addToCart/updateQuantity/checkout` | ✅ | t_cart, t_menu_item |
| `NicknameSyncService.changeNicknameAndSync` | ✅ | t_user, t_family_member, t_order, t_menu_item |
| `PushService.bindPushAuth` | ❌ | t_user |
| `RatingService.rate` | ❌ | t_menu_item_rating |

> Node 直连实现应把上表 ✅ 的操作放进同一个 `connection.beginTransaction()`，否则中途失败会留下半截数据（尤其 `deleteFamily`、`deleteUser`、`OrderService.updateOrder`）。

---

## 27. 下单链路（虽然不在 22 项内，但直连服务若要做"清空购物车/重算订单"必须知道）

`POST /api/cart/checkout`（`CartService.checkout`，对照 `POST /api/orders/checkout` = `createOrderFromCart`）：

```java
// CartService.checkout：每个购物车项按 quantity 拆成多条 menu item，然后删除整条 cart
for (int i = 0; i < qty; i++) { menuItemService.addItem(...); }
cartMapper.deleteById(cartId);
```

`OrderService.createOrderFromCart`（`@Transactional`）：

```java
BigDecimal total = BigDecimal.ZERO; int totalCount = 0;
for (Cart c : cartItems) {
    int qty = c.getQuantity() != null ? c.getQuantity() : 1;
    total = total.add(c.getPrice().multiply(BigDecimal.valueOf(qty)));
    totalCount += qty;
}
order.setTotalAmount(total);
order.setItemCount(totalCount);
order.setStatus(0);
...
for (Cart cart : cartItems) {
    for (int i = 0; i < qty; i++) { /* INSERT t_menu_item，status=0, version=0, price=cart.price */ }
}
new Thread(() -> pushService.pushNewOrderToChef(firstItemId)).start();
```

而 `OrderController.checkout` 在 service 成功后**才**删购物车（`cartService.removeFromCart(c.getId())`），**不在同一事务**。
`t_order.item_count` = **所有 quantity 之和**（不是行数）。

**推送副作用（异步线程，事务外）**：

* `MenuItemService.addItem`（`POST /api/menu/items`）→ `new Thread(() -> pushService.pushNewOrderToChef(itemId)).start()` → 写 `t_push_log`（`type='NEW_ORDER'`），失败时写 `t_push_pending`。
* `PUT /api/menu/items/{id}/confirm` 与 `DELETE /api/menu/items/{id}` → `pushStatusChanged(id)` → `t_push_log`（`type='STATUS_CHANGED'`）。
* **订单接口（`/api/orders/*/confirm`、`items/*/reject`）不触发任何推送。**
* `t_push_log` 的 INSERT 只写 `user_id, openid, menu_item_id, type, success, error_msg, created_at`，**`family_id` 走 DB 默认 0**。

---

## 28. 直连 SQL 实现最容易漏掉的副作用 —— 汇总检查表

1. **`GET /api/dishes` 会写库**：`price` 为 NULL/0 的菜会被就地 `UPDATE t_dish SET price = ...`。只读的直连实现要么复刻这个回填，要么自己算价。
2. **改配菜价格后必须重算 `t_dish.price`**（`DishService` 只在有 `ingredients` 时重算）。
3. **`t_user.is_chef` 是 `t_family_member.role` 的全局镜像**：任何 role 变动后必须 `UPDATE t_user SET is_chef = (是否任一家庭为 CHEF)`。漏了会让"全局管理员旁路"（`confirmItem` / `rejectItem` / `X-Family-Id` 切换 / `getOrderDetail.canConfirm`）失效。
4. **`t_user.active_family_id`**：删家庭、移出家庭、加入家庭、创建家庭都要重算；置 `NULL` 时 `/api/dishes`、`/api/orders` 等接口会返回空数组，`/api/me/chef-status` 会走全局回退分支。
5. **`t_user.token_version` 只能增**。改用户资料时若整行覆盖成 0/NULL，所有被撤销的 token 会复活。
6. **昵称 4 处快照**：`t_family_member.nickname`、`t_order.user_nickname`、`t_menu_item.user_nickname`（+ `t_user.nickname` 本尊）。改昵称不做全量 UPDATE，全站显示会不一致。
7. **`t_order.total_amount` / `item_count` 永不重算**：改订单金额、确认/驳回菜品都不动它们；`confirmedCount/rejectedCount` 是读时算的。
8. **`t_order.confirmed_by` 永远不会被后端写入**（`syncOrderStatus` 只写 `confirmed_at`）。
9. **`t_menu_item.status = 2` 是"已驳回"**，不在实体注释里，且 `statusText()` 会渲染成"未知"。
10. **`version` 乐观锁只在两条手写 SQL 里递增**（`confirmWithLock` / `cancelWithLock`）；订单维度的 confirm/reject 走 `updateById`，**不递增 version**。
11. **订单确认有两套权限模型**：`/api/orders/{id}/confirm` 只认家庭 OWNER/CHEF（无全局管理员旁路）；`/api/orders/items/{id}/confirm|reject` 有 `is_chef=1` 旁路。
12. **删菜不清理 `t_cart`**：购物车里会残留指向已删 `dish_id` 的行（价格是快照，仍能下单 → 创建出 `dish_id` 悬挂的 `t_menu_item`）。
13. **删家庭不清理 `t_order` / `t_menu_item_rating` / `t_push_log` / `t_push_pending`** → 孤儿数据。
14. **删用户不删订单**（注释声称删，代码没删），也不修 `t_family.owner_user_id`、不重算其他用户的 `active_family_id`/`is_chef`。
15. **配菜删除有全局引用检查**（`SELECT COUNT(*) FROM t_dish_ingredient WHERE ing_id=?`，**跨家庭**）；菜删除、分类删除、公共库删除**都没有**。
16. **`PUT /api/dishes/{id}` 里 `ingredients: null` = 不动配方，`ingredients: []` = 清空配方**；且 `imageEmoji/description` 直接覆盖（可被置 null）。
17. **`t_dish.uk_family_name (family_id, name)`、`t_ingredient.uk_name_family (name, family_id)`、`t_ingredient_category.uk_name_family (name, family_id)`、`t_dish_ingredient.uk_dish_ing (dish_id, ing_id)`、`t_family.code`、`t_family_member.uk_family_user`** 是唯一的 DB 级硬约束；冲突会变成 `{"code":500,"message":"服务器异常，请稍后重试"}`。直连实现建议先 `SELECT` 查重再写。
18. **`t_ingredient.unit` 没有 DEFAULT**（NOT NULL）→ 直连 INSERT 必须给值；`t_public_ingredient.unit` 有 DEFAULT `'克'`。
19. **`t_public_dish.category_id` 可空，`t_public_ingredient.category_id` NOT NULL DEFAULT 0** —— 两张公共表的 category 语义不同。
20. **`family_id = 0` 是"公共配菜分类"的魔数约定**（`GET /api/public/ingredients/categories`）。
21. **"public" 有歧义**：`GET /api/ingredients/public` = 本家庭启用配菜（`t_ingredient`）；`GET /api/public/ingredients` = 全局公共库（`t_public_ingredient`）。
22. **`GET /api/ingredients?categoryId=` 在无活跃家庭时会跨家庭返回**（family 条件被 `familyId != null` 短路掉）。
23. **推送是异步线程 + 事务外**：`t_push_log`（`type` = `NEW_ORDER` / `STATUS_CHANGED`）与失败入队 `t_push_pending`（`status=0`，`attempts` 每次重试 +1，≥3 → `status=2`）。订单级 confirm/reject 不推送。
24. **`t_menu_item` 没有 quantity 列**：数量被展开成多行；`t_order.item_count` 是数量之和。
25. **`t_category` 是全局表**（无 `family_id`），实体无 `createdAt`；种子数据只有 5 条，删空会让小程序首页无分类。
26. **时间戳在 `updateById` 整行覆盖时可能被"顶掉"**：后端普遍靠 MySQL `ON UPDATE CURRENT_TIMESTAMP` 而非显式赋值，但 `batchSave` / `PublicDishService` / `PublicIngredientService` / `OrderService.updateOrder` / `NicknameSyncService` 会**显式**写 `updated_at = now()`。直连实现统一显式写 `updated_at = NOW()` 最安全。
27. **旧的 `t_product` / `t_order_item` 表**（`ProductController`、`OrderItemMapper`）不在 `init.sql` 的 17 张表里，属于遗留代码，**不要为它们建表或读写**。

---

## 29. 附：本次未覆盖但可能相关的接口

| 接口 | 写入的表 |
|---|---|
| `POST /api/wx/login` | `t_user`（首次登录 INSERT，`nickname = "用户" + openid前6位`，`avatar_url` 保持 NULL） |
| `PUT /api/me/nickname` | 4 张表昵称快照（见 §25.1） |
| `PUT /api/me/avatar` | `t_user.avatar_url`（存 `avatar-1..6` key）+ 触发昵称同步 |
| `POST /api/auth/logout` | `t_user.token_version += 1` |
| `POST /api/push/auth` | `t_user.allow_push` |
| `POST /api/family/create` `/join` `/switch` `/{id}/leave` `/{id}/claim-chef` `/{id}/resign-chef` `/{id}/kick` | `t_family`、`t_family_member`、`t_user(active_family_id, is_chef)` |
| `POST /api/cart/add`、`PUT /api/cart/{id}`、`DELETE /api/cart/{id}`、`POST /api/cart/checkout` | `t_cart`、`t_menu_item` |
| `POST /api/menu/items`、`PUT /api/menu/items/{id}/confirm`、`DELETE /api/menu/items/{id}` | `t_menu_item`（`version` 递增）+ `t_push_log` |
| `POST /api/rate/{menuItemId}` | `t_menu_item_rating`（`uk_menu_user` 唯一，同用户重复评 = UPDATE） |
| `GET /api/menu/today`、`/today/by-user`、`/today/total`、`/shopping-list` | 只读 `t_menu_item`（+ `t_dish_ingredient` / `custom_ings` JSON） |

