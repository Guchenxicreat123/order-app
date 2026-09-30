# admin-api —— 独立的后台管理服务

后台管理端**不再走小程序的后端**。它是一个独立容器、独立端口、自己连 MySQL 的服务，
只做两件事：**校验管理员账号密码** + **直接读写数据库**。

## 为什么单独拆出来

原来的后台是借用小程序后端的接口，于是继承了一堆和"后台管理"无关的限制：

| 旧限制 | 后果 |
|---|---|
| 必须 `APP_ADMIN_OPENID` 指向的用户 `is_chef=1` | 换管理员账号就登录不了（报"该账号不是主厨"） |
| token 要分 USER / CHEF / ADMIN 角色 | 后台拿 ADMIN token 调主厨接口一律 403（比如 `菜单管理` 页直接空白） |
| 接口按家庭/成员角色鉴权 | 后台想改别的家庭的数据要先"成为那个家庭的成员" |
| 复用小程序业务方法 | 改一条数据要连带触发订阅消息、购物车、token 撤销等副作用 |

现在这些限制**全部去掉**：能打开后台、能登录，就能改任何数据。

## 认证

- 账号密码存在 `order_app.t_admin_user`（bcrypt 哈希），与 `t_user` 完全无关。
- 首次启动自动建表；表为空时创建默认管理员 **admin / 123456**（可用环境变量覆盖）。
- 令牌是无状态 JWT（`ADMIN_JWT_SECRET` 签名，默认 12h）。
- 表里的 `token_epoch` 在**改密时自增**，所以改完密码旧令牌立即失效。
- 后台页面右上/侧边栏有「修改密码」入口，改完自动退出要求重新登录。

## 部署形态

```
浏览器 ──► admin-web 容器 :8007   （静态页面 + 反向代理 /api）
                 │
                 └──► admin-api 容器 :8008  （只处理 /api，不对外发布端口）
                            │
                            └──► mysql 容器 :3306 （库 order_app）
```

- `admin-api` **不在宿主机发布端口**，只能由 `admin-web` 容器经 `order-net` 内网访问。
- 前端所有请求都是同源 `/api/...`：不写死内网 IP、不受网段影响、没有跨域、https 下也能用。
- 小程序后端（`order-app`，8006）与后台互不影响，可以各自单独重启。

启动：

```bash
docker compose build admin-api admin-web
docker compose up -d admin-api admin-web
curl http://127.0.0.1:8007/api/health          # {"code":0,...,"data":{"db":true}}
```

## 家庭范围（唯一的"过滤"，不是权限）

菜品、配菜、配菜分类、订单这些数据在库里是**按家庭分表存**的（`family_id`）。
后台侧边栏有「家庭切换器」，选哪个家庭就看/改哪个家庭的数据：

- 请求头带 `X-Family-Id` → 用这个家庭；
- 没带（刷新页面时前端可能还没带上）→ 用 `DEFAULT_FAMILY_ID`，再没有就取第一个家庭。

服务端**不校验**你是不是这个家庭的成员、是不是主厨 —— 任何家庭都能直接看和改。

`/admin/users`、`/admin/families*`、`/categories`、`/public/*` 是平台级数据，不受家庭影响。

## 接口一览

认证（唯一门槛就是账号密码）：

| 方法 | 路径 |
|---|---|
| POST | `/api/admin/login` （兼容旧路径 `/api/chef/login`） |
| POST | `/api/admin/logout` |
| GET | `/api/admin/me` |
| POST | `/api/admin/change-password` |
| GET | `/api/health` |

数据（全部需要 Bearer token）：

| 域 | 接口 |
|---|---|
| 家庭 | `GET /api/admin/families`、`GET /api/admin/families/overview`、`POST /api/admin/families`、`PUT|DELETE /api/admin/families/{id}` |
| 用户 | `GET /api/admin/users`、`DELETE /api/admin/users/{userId}`、`POST /api/admin/users/{userId}/{add-family,remove-family,set-role,revoke}` |
| 订单 | `GET /api/orders`、`GET /api/orders/today`、`GET /api/orders/{id}`、`POST /api/orders/{id}/confirm`、`POST /api/orders/items/{id}/{confirm,reject}`、`PUT|DELETE /api/orders/{id}` |
| 菜品 | `GET /api/dishes/manage/all`、`GET /api/dishes/{id}`、`POST /api/dishes`、`PUT /api/dishes/{id}`、`PUT /api/dishes/{id}/status`、`DELETE /api/dishes/{id}` |
| 分类/配菜 | `GET /api/categories`、`GET|POST /api/ingredient-categories`、`PUT|DELETE /api/ingredient-categories/{id}`、`GET /api/ingredients/public`、`POST /api/ingredients`、`PUT|DELETE /api/ingredients/{id}`、`PUT /api/ingredients/{id}/toggle` |
| 公共库 | `GET /api/public/dishes/admin/all`、`POST /api/public/dishes`、`PUT|DELETE /api/public/dishes/{id}`、`GET /api/public/ingredients/admin/all`、`GET /api/public/ingredients/categories`、`POST /api/public/ingredients`、`PUT|DELETE /api/public/ingredients/{id}` |
| 统计 | `GET /api/admin/stats/overview` |

响应统一是 `{"code":0,"message":"ok","data":...}`；失败用**非 2xx** + `message`
（前端拦截器靠 HTTP 状态码弹提示，业务失败必须是 4xx/5xx，不能 200+code!=0）。

## 直接改库时保留的业务一致性

去掉的是"权限限制"，不是"数据一致性"。这些连带维护仍然会做：

- 改 `t_family_member.role` 后重算 `t_user.is_chef`（小程序端仍读这个字段）；
- 家庭解散/成员移出后重算 `t_user.active_family_id`；
- 菜品配方变更后按 `Σ(配料单价 × 用量)` 重算 `t_dish.price`（与小程序端一致）；
- 删除配菜会先清掉菜品配方里的引用，并重算受影响菜品的价格；
- 改订单下单者会同步订单内菜品的下单者快照；
- 菜品状态/订单状态联动沿用小程序端规则（订单：有待确认菜品=0，否则=1；菜品：0待确认/1已确认/2已驳回/-1已撤销）；
- 唯一键冲突等数据库约束错误会翻译成人话返回 400，而不是抛 SQL 原文。

## 自测

覆盖认证、全部读取接口的结构断言，以及家庭/用户/菜品/配菜/公共库/订单的完整增删改生命周期，
全部用 `__smoke_` 临时数据并在结束时清理：

```bash
docker cp admin-api/test/smoke.js order-admin-api:/app/smoke.js
docker exec -w /app order-admin-api node /app/smoke.js
```

预期输出：`通过 92 项，失败 0 项`。

## 环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `mysql` / `3306` / `order_app` | 数据库连接 |
| `DB_USER` / `DB_PASSWORD` | `myapp` / `<APP_PASSWORD>` | 需要 `order_app` 的建表权限 |
| `PORT` | `8008` | 监听端口 |
| `ADMIN_JWT_SECRET` | dev 占位值 | 上线必须改 |
| `ADMIN_TOKEN_TTL` | `12h` | 令牌有效期 |
| `ADMIN_DEFAULT_USERNAME` / `ADMIN_DEFAULT_PASSWORD` | `admin` / `123456` | 仅在表为空时用于初始化 |
| `DEFAULT_FAMILY_ID` | 空 | 前端没带 `X-Family-Id` 时的兜底家庭 |
