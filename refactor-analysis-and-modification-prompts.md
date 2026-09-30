# 点餐小程序源码分析与改造提示词

> 项目根目录：`点餐小程序/`
> 三层架构：`miniprogram/`（uni-app + Vue3 用户端）、`admin-web/`（Vue3 + Element Plus 后台）、`backend/`（Spring Boot 3 + MyBatis-Plus + MySQL + JWT）。

---

## 一、系统整体架构

```
用户微信端(miniprogram)  ──/api/*──▶  backend(Spring Boot:8006)  ──▶ MySQL(order_app)
后台管理端(admin-web)    ──/api/admin/*─▶      ▲                        ▲
    (Vue3+ElementPlus)                       JWT 鉴权                docker-compose 一键部署
                                                                    cloudflared 隧道对外
```

- **技术栈**：用户端 uni-app + Vue3；后台 Vue3 + Element Plus + Pinia + Nginx(8007)；后端 Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + JWT(jjwt) + BCrypt；Docker Compose 部署，Cloudflare Tunnel 公网。
- **数据表**：`t_user`、`t_category`、`t_product`、`t_order`、`t_order_item`、`t_admin`。
- **订单状态机**：`0=待付款 / 1=已下单 / 2=已完成 / 3=已取消`。

---

## 二、各端功能清单

### 1. 用户端（miniprogram，6 个页面）
| 页面 | 功能 |
|---|---|
| index 首页 | 横幅、分类宫格、今日菜品列表，下拉刷新 |
| category 菜单 | 左分类右商品联动、加入购物车 |
| product 商品详情 | 数量选择、加入购物车、立即购买 |
| cart 购物车 | 加减数量、删除、合计 |
| order 确认订单 | 商品清单、备注、提交订单 |
| mine 我的 | 用户信息、我的订单列表 |

### 2. 后台管理端（admin-web，4 个页面）
- **Login**：管理员登录（admin/123456，BCrypt 校验）。
- **Orders**：订单列表（按状态筛选）、改状态、回显详情。
- **Categories**：分类增删改查。
- **Products**：商品增删改查、按分类筛选。
- 路由守卫（meta.auth + localStorage token）。

### 3. 后端 API
- 公共：`/hello`、`/api/wx/login`、`/api/categories`、`/api/products`、`/api/products/{id}`。
- 需鉴权（JWT）：`/api/orders`(POST/GET)、`/api/orders/{id}`。
- 管理端：`/api/admin/login`、`/api/admin/orders`、`/api/admin/orders/{id}/status`、`/api/admin/categories`(CRUD)、`/api/admin/products`(CRUD)。

---

## 三、从代码角度的分析（问题与隐患）

### A. 安全类（重要，改造优先级最高）
1. **登录是"假登录"，openId 可被伪造**（`WxLoginController`）
   - 直接把前端传来的 `code` 当 openId 用（`openId = "mock_" + code`），完全没有调用微信 `jscode2session`。
   - 任何人只要传任意字符串就能注册/登录并拿到合法 JWT，可冒用他人身份下单。
   - 根因：小程序端 `App.vue` 是 `uni.login()`，H5 端用 `mock_code_+时间戳`。
2. **鉴权角色未区分**（`JwtInterceptor`）
   - 用户 token 与 admin token 用的是**同一套 JWT、同一个 secret、同一种 subject(userId)**。
   - 管理接口 `/api/admin/**` 判定"token 有效"即放行，**没有任何"必须是管理员"的校验**。拿着用户 token 可调所有后台写接口。
   - 更糟：`AdminController` 的管理接口没有显式声明需要 admin 角色，只要 header 带有效 token 就能增删商品、改任何订单状态。
3. **JWT secret 硬编码在 application.yml**，且接近默认值，极不安全。生产必须注入环境变量。
4. **默认管理员密码 admin/123456**，README 虽提醒，但未强制修改机制。
5. **CORS 全开放**（`allowedOriginPatterns("*")` + `allowCredentials(true)`），配合"任意 token 可当 admin"问题，后台可直接被恶意调用。

### B. 业务/功能性缺口
1. **没有支付**：`status=0 待付款` 存在但整个系统无法付款，订单直接到"已下单"。
2. **下单接口无库存/价格一致性校验**：
   - `OrderController.create` 用数据库中的**实时价格**重新计价（这点是对的）；但**没有校验 `quantity > stock`，也没有扣减库存**（stock 字段形同虚设）。
   - 统计销量时**先读再写（`selectById` + `updateById`）不是原子的**，高并发会丢销量。
3. **订单号生成**：`yyyyMMddHHmmss + 4位随机`，同一秒内并发可能撞号，且随机性弱。
4. **后台订单列表无分页、详情不展示商品明细**：后台 `Orders.vue` 的"详情"只回显字段，**没有展示该订单的商品条目**（后端 `AdminController.orders` 也没查 order_items）。
5. **用户端"我的订单"无分页**，订单多了会一次性拉全量。
6. **后台没有数据统计/报表**（README 也列入待办）。
7. **用户无收货地址/桌号/取餐信息**：没有 address 字段，"点餐"没有配送或取餐方式，业务闭环不完整。
8. **商品/分类没有图片上传**：`imageUrl` 字段存在，但后台 `Products.vue` 没有上传控件，前端一律用 emoji 占位。

### C. 代码质量问题
1. **N+1 查询**：`OrderController.myOrders` 每单查一次 order_items。
2. **无统一异常处理**：无 `@RestControllerAdvice`，参数校验失败、业务异常直接 500，前端只能弹"请求失败"。
3. **mybatis-plus 配置了逻辑删除字段 `deleted`，但表里没有该字段**（`logic-delete-field: deleted` 已开启而 schema 无 deleted 列），配置与 schema 不一致，属隐患。
4. **库存/销量非原子更新**（见上）。
5. **`mine` 页 `onMounted` 拉到订单后不刷新**：下单成功跳回 mine 时，若页面已存在不会重新加载（应改用 onShow）。
6. **前端购物车刷新机制脆弱**：用 `__cart_ver` 全局号，但 cart 页实际用 `onShow` 重新 `getCart()`，`__cart_ver` 未真正被消费，属冗余/半成品。
7. **`product` 页从 `getCurrentPages()` 取 id**，不如直接读 `onLoad(options)`，多端兼容性差。
8. **前端 BASE_URL 硬编码** `http://192.168.x.x:8006`，无多环境配置（dev/prod）管理。

### D. 部署/配置类
1. **`project.config.json` `compileType: "game"`**：项目本身是普通小程序，编译类型配错（且根目录与 miniprogram/ 下各有一份，值不一致）。
2. **manifest.json 里 `appid` 为空**，`mp-weixin.appid` 也为空，无法直接发布。
3. **`.env` 含明文数据库密码**，且 `.env.example` 与 `.env` 内容相同（未改默认值），若仓库公开会泄露凭据。
4. **没有 CI/CD、没有测试**（无后端单测、无前端 lint）。

---

## 四、从点餐业务角度的分析

### 1. 现有业务闭环（已跑通）
浏览菜单 → 选品 → 购物车 → 确认订单（备注）→ 提交 → 后台看单/改状态 → 用户看订单。

### 2. 业务闭环的缺失与不合理
- **无支付，无"待付款→付款"环节**：状态机定义了 `0 待付款` 却无付款入口，实际下单即 `1 已下单`，等于"下单=占坑"。
- **无取餐/配送方式**：菜品都是家常菜，更像"店内/食堂/外卖"场景，但没有桌号、自取、配送地址任一选项，无法承接真实经营。
- **无库存概念的市场验证**：`stock` 字段未参与下单逻辑，点了也不会减，爆款/售罄无法体现。
- **后台只能管订单状态，没有经营视图**：看不到今日营业额、热销榜、订单趋势，管理员"只能看单不能决策"。
- **无用户维度的运营**：无收藏、无历史常点、无会员/积分、无优惠券、无评价。

### 3. 建议的业务演进路线（分三期）
- **P0（先让闭环真实可用）**：真实微信登录、支付或"货到/自取付款"、库存扣减与校验、角色权限、后台订单明细与分页。
- **P1（提升经营能力）**：商品/分类图片上传、数据统计看板、订单状态自动流转（接单/出餐/完成）、桌号或配送地址。
- **P2（运营增长）**：优惠券、会员积分、评价、收藏、推荐排序、消息通知。

---

## 五、可用于"下一轮 AI 改造"的修改提示词

> 下面是**可直接交给 AI 编码代理**的提示词，把上面的问题转成明确、可验收的任务。建议按 P0→P2 顺序分批执行，每批提交一次。

### 提示词（推荐直接复制）

```
你是这个「点餐小程序」项目的改进工程师。项目为三层架构：
miniprogram/（uni-app+Vue3 用户端）、admin-web/（Vue3+ElementPlus 后台）、
backend/（Spring Boot 3.2 + MyBatis-Plus + MySQL8 + JWT + BCrypt）。
请基于现有代码做「保留架构、补全业务闭环、修复安全与并发隐患」的改造。

【改造必须覆盖以下任务】

一、认证与鉴权（最高优先级）
1. 真实微信登录：后端新增配置 appid/appSecret（从环境变量读取），
   WxLoginController 改为调用 https://api.weixin.qq.com/sns/jscode2session
   换取真实 openId+session_key；失败返回明确错误；H5 本地开发可用可选的 mock 分组。
   严禁再把前端传来的 code 直接当作 openId（当前是伪造漏洞）。
2. 区分「用户」与「管理员」两套 JWT：
   - 新增 JwtInterceptor 支持 role 字段（USER / ADMIN），用户 token 用 userId，
     管理 token 用 adminId，subject 前缀区分（如 "u:1" / "a:1"）。
   - /api/admin/** 必须校验 role==ADMIN，否则 403。
   - 前端 admin-web 登录后保存 admin_token，miniprogram 保存 user_token。
3. JWT secret 与有效期改为从环境变量注入（.env / docker-compose），不要写死。
4. 移除/收紧全局 CORS：改为显式 allowedOrigin（admin-web 域名 + 本地调试），
   生产不开放 * + credentials。

二、下单核心业务
5. 下单时必须校验：商品存在且在售、quantity>=1、quantity<=stock；
   用乐观锁（@Version 或 update ... set stock=stock-? where stock>=?）原子扣减库存，
   库存不足返回明确提示；销量用 update set sales=sales+数量 原子累加，
   去除当前"先读后写"的非原子写法，并去掉每次下单对 sales 的重复读。
6. 订单号生成改为「时间戳+随机+自增/雪花」避免同秒撞号，或加唯一键冲突重试。

三、后台管理补充
7. /api/admin/orders 支持分页（page/size），并返回每单的 items 明细；
   Orders.vue 详情弹窗展示商品条目清单。
8. /api/admin/products 与 /api/admin/orders 支持按关键词/时间筛选。
9. 后台订单管理加「取餐状态」流转支持（如 待接单→制作中→待取餐→已完成），
   与用户端可见状态联动。

四、用户端体验与业务闭环
10. 增加「订单详情页」，用户可查看单条订单的商品明细与状态流转时间线，
    并支持下单后取消（仅允许待付款/已下单状态）。
11. mine 页改为 onShow 刷新（现在 onMounted 下单后返回不更新）。
12. 增加收货/取餐信息：订单增加 就餐方式（堂食/自取/配送）+ 桌号或地址字段。
13. product 页改为 onLoad(options) 读取 id，去除 getCurrentPages() 取参。
14. 前端 BASE_URL 按环境（H5 开发/小程序/生产）通过 VITE_ 变量管理，去掉硬编码 IP。

五、稳定性与工程质量
15. 新增统一异常处理 @RestControllerAdvice：参数校验、业务异常、未知异常
    返回统一 {code,message}，HttpStatus 与业务码对应。
16. 修正 mybatis-plus 逻辑删除配置与 schema 不一致：要么建 deleted 字段，
    要么去掉 logic-delete-field 配置（当前表无 deleted 列属隐患）。
17. myOrders/后台列表消除 N+1（一次查出 items 后内存组装）。
18. 修正 project.config.json 的 compileType（当前误配为 "game"），
    补全 manifest.json 的 appid。
19. 补后端基础单测（登录、建单、库存扣减边界、权限校验）。

【约束】
- 保持现有技术栈与目录结构，不引入重型框架。
- 全部修改需能通过 mvn clean package -DskipTests 编译、前后端可联调。
- 每条改动说明文件路径，并更新 README 的接口文档与"下一步"勾选状态。
- 完成后给出：改了什么、哪些文件、如何验证（含接口 curl 示例）。
```

### 分阶段执行建议
- **第一批（安全+闭环，任务 1–9）**：做完即可视为"能真实运营"的最小可用版。
- **第二批（体验+工程，任务 10–19）**：用户体验与质量兜底。
- **第三批（可选运营增强）**：优惠券、积分、评价、统计报表——可另行开提示词，本次未列入。

---

## 附：最容易踩的 3 个隐患（改造时优先看）
1. **权限洞**：用户 token 可当 admin 用（`JwtInterceptor` 未区分角色）→ 必须先修。
2. **登录伪造**：code 直接当 openId → 身份可冒充，涉及所有订单归属。
3. **库存不扣减**：下单不加库存校验和扣减 → 超卖 + 未来做支付会出大问题。
