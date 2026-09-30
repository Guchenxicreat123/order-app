# 点餐小程序（V2 安全增强版）

> 基于 V1 改造，增加了真实微信登录、JWT 角色鉴权、库存原子扣减、订单分页等。

> ⚠️ **本仓库是公开脱敏版本**：所有真实域名 / IP / 密码已替换为占位符（`your-domain.example.com` / `192.168.x.x` / `<ROOT_PASSWORD>`）。**clone 后必须先按下方"部署前必改项"修改后才能跑起来**。

---

## 🚨 部署前必改项（按文件定位）

下面所有 `<占位符>` 都是从代码里复制的实际字符串，逐项替换即可。

### 1. 后端 Spring Boot — `backend/src/main/resources/application.yml`

| 行 | 占位符 | 说明 |
|---|---|---|
| 26 | `dev-secret-change-in-prod` | JWT 签名密钥，**生产环境必须用 64+ 随机字符串**（建议 `openssl rand -hex 32`）|
| 34 | `${WECHAT_APPID:}` | 微信小程序 AppID（`wx...` 开头的字符串，微信公众平台获取）|
| 35 | `${WECHAT_SECRET:}` | 微信小程序 AppSecret（重置后只显示一次，妥善保存）|
| 79–80 | 同上（小程序登录用） | 同上 |

### 2. Node 后台 API — `admin-api/src/auth.js`

| 行 | 占位符 | 说明 |
|---|---|---|
| 17 | `order-admin-api-dev-secret-change-me` | admin-api 的 JWT 密钥，**与上面 backend 的密钥不同** |
| 20 | `123456` | admin-api 默认管理员密码，**必须改** |

### 3. 小程序前端 — `miniprogram/src/utils/request.js`

| 行 | 占位符 | 说明 |
|---|---|---|
| 7 | `https://your-domain.example.com` | 后端 API 的公网域名（HTTPS，**不要用 IP 也不要 http**）|

### 4. 小程序前端 — `miniprogram/vite.config.js`

| 行 | 占位符 | 说明 |
|---|---|---|
| 7 | `http://192.168.x.x:8006` | 仅 H5 平台 dev 用的本地后端地址，本机开发才需要改 |

### 5. 小程序工程配置 — `miniprogram/src/manifest.json` + `project.config.json`

| 字段 | 占位符 | 说明 |
|---|---|---|
| `appid` | `__UNI__XXXXXXX` / `""` | 改成你自己申请的小程序 AppID |
| `mp-weixin.appid` | `wxd3c79b126b6ba442` | 同上 |

### 6. 管理后台 — `admin-web/src/api/index.js`

| 行 | 占位符 | 说明 |
|---|---|---|
| 10 | `http://192.168.x.x:8006/api` | 默认 API 地址，**生产环境必须通过 `.env.production` 设置 `VITE_API_URL` 覆盖** |

### 7. Docker Compose — `docker-compose.yml`（4 处 `${MYSQL_*:...}`）

| 行 | 占位符 | 说明 |
|---|---|---|
| 9 | `<ROOT_PASSWORD>` | MySQL root 密码（**不要留默认值**）|
| 12 / 42 | `<APP_PASSWORD>` | MySQL 应用用户密码 |
| 25 | 同 root 密码 | healthcheck 用的密码，必须和 9 行一致 |
| 43 | `dev-secret-change-in-prod` | 后端 JWT 密钥（与 application.yml 26 行保持一致）|
| 61 | `mock_test1234` | admin-api 默认管理员 openid 注释，**真上线后这行可以删** |
| 88 | `<APP_PASSWORD>` | backend 连接数据库的密码 |
| 91 | `order-admin-api-secret-change-in-production` | admin-api JWT 密钥（与 auth.js 第 17 行保持一致）|

> 💡 建议：把上述密码都放进仓库根目录的 `.env` 文件（**必须加进 `.gitignore`**），用 `docker compose --env-file .env up` 启动。

### 8. 部署脚本 — `deploy.sh`

| 行 | 占位符 | 说明 |
|---|---|---|
| 49 | `<ROOT_PASSWORD>` | MySQL root 密码 fallback |

---

## ✅ 改完后跑一遍的检查清单

- [ ] 所有 `<...>` 占位符都已替换
- [ ] `application.yml` 的 `JWT_SECRET` ≠ `admin-api/auth.js` 的 `ADMIN_JWT_SECRET`（两个密钥**必须不同**）
- [ ] `MYSQL_ROOT_PASSWORD` = `deploy.sh` 第 49 行 = `docker-compose.yml` 第 25 行
- [ ] 微信 `appid` / `secret` 已替换成你自己申请的
- [ ] 小程序 `manifest.json` 的 `appid` 已替换
- [ ] 生产环境的 `BASE_URL` 用 HTTPS 域名（不用 IP 也不用 HTTP）
- [ ] `.env` / `application.yml` 已加进 `.gitignore`，**不要 commit 真实密码**

---

## 技术栈

| 层 | 技术 |
|---|---|
| 小程序前端 | uni-app + Vue 3 |
| 后台管理端 | Vue 3 + Element Plus + Nginx |
| 后端 | Spring Boot 3 + MyBatis-Plus + MySQL 8 + JWT |
| 部署 | Docker Compose（飞牛 OS / Linux） |
| 隧道 | Cloudflare Tunnel |

## 目录结构

```
.
├── backend/              Spring Boot 后端
│   ├── init.sql         数据库脚本（含测试数据 + 默认管理员）
│   ├── pom.xml
│   └── src/main/...
├── admin-web/            后台管理 Web（Vue3 + Element Plus）
│   ├── src/views/        Login.vue / Orders.vue
│   ├── src/api/          axios 封装
│   ├── nginx/            nginx 配置（静态 + /api 反代）
│   └── Dockerfile
├── miniprogram/          uni-app 前端
│   ├── App.vue
│   ├── main.js
│   ├── pages/            页面（index/category/product/cart/order/mine）
│   ├── api/              接口封装（user/product/order）
│   ├── utils/            工具（request/cart）
│   ├── manifest.json
│   └── pages.json
├── docker-compose.yml    一键启动
├── Dockerfile
└── .env.example
```

## 当前实现的功能

- [x] 小程序登录（V1 简化版，直接用 code 作 openId）
- [x] 商品分类 + 商品列表 + 商品详情
- [x] 购物车（本地存储）
- [x] 提交订单
- [x] 我的订单列表
- [x] 管理员登录（admin/123456）
- [x] 后台订单管理（看单、改状态）—— **有 Web 界面**
- [x] 后台管理 Web 已部署（Nginx 8007 端口）

## 一、本地开发（推荐先用这个）

### 1. 启动后端

```bash
cd backend

# 1) 起 MySQL（任选一种）
#    A. 用本机已安装的 MySQL
#    B. Docker 起一个：
docker run -d --name mysql-dev \
  -e MYSQL_ROOT_PASSWORD=<ROOT_PASSWORD> \
  -e MYSQL_DATABASE=order_app \
  -e MYSQL_USER=myapp \
  -e MYSQL_PASSWORD=<APP_PASSWORD> \
  -p 3306:3306 \
  -v $PWD/init.sql:/docker-entrypoint-initdb.d/01-init.sql \
  mysql:8.0

# 2) 修改 application.yml 中的 dev profile 数据库地址
#    默认连 localhost:3306，账号 myapp / <APP_PASSWORD>

# 3) 启动 Spring Boot
mvn spring-boot:run
# 或先打包再跑
mvn clean package -DskipTests
java -jar target/order-app.jar
```

启动成功后访问 `http://localhost:8006/hello` 应返回：
```json
{"code":0,"message":"ok","data":{"msg":"hello from order-app","time":"...","version":"1.0.0"}}
```

### 2. 启动小程序

**方式 A：HBuilderX（推荐新手）**
1. 下载 [HBuilderX](https://www.dcloud.io/hbuilderx.html)
2. 文件 → 打开目录 → 选择 `miniprogram/`
3. 运行 → 运行到浏览器 → Chrome

**方式 B：CLI**
```bash
cd miniprogram
npm install -g @dcloudio/uvm   # 全局装 uvm
npx degit dcloudio/uni-preset-vue#vite my-project
# 把代码拷过去后
npm install
npm run dev:h5
```

浏览器打开 `http://localhost:5173` 即可。

**方式 C：微信开发者工具**
1. 用 HBuilderX 打包：发行 → 小程序-微信
2. 在 `unpackage/dist/dev/mp-weixin/` 会生成微信小程序代码
3. 微信开发者工具 → 导入项目 → 选这个目录

### 3. 修改后端地址

在 `miniprogram/` 目录创建 `.env` 文件：

```bash
# 本地开发
VITE_API_URL=http://localhost:8006

# 真机调试（手机扫码，同一局域网）
VITE_API_URL=http://192.168.x.x:8006

# 部署上线
VITE_API_URL=https://api.xxx.cn
```

编译时会在 `src/utils/request.js` 中替换 `import.meta.env.VITE_API_URL`。

## 二、生产部署（Docker Compose）

### 1. 服务器准备
- 一台 Linux（飞牛 OS / Ubuntu / Debian）
- 安装了 Docker 和 Docker Compose

### 2. 上传项目 + 启动（Docker 多阶段构建，服务器无需装 JDK/Maven）
```bash
# 在项目根目录
cp .env.example .env
# 编辑 .env，填入 Cloudflare 隧道 token

# 一键构建并启动（自动编译 jar）
docker compose up -d --build
```

### 3. Cloudflare 后台配置
- Zero Trust → Networks → Tunnels → 你的 tunnel → Public Hostname
- 新增一条：
  - Subdomain: `api`
  - Domain: 你的域名
  - Service: `http://order-app:8006`

### 4. 微信小程序后台配置
- 开发 → 开发设置 → 服务器域名
- request 合法域名：填你的域名（不带 `https://`）

## 三、接口文档

### 用户端 `/api/*`

| Method | URL | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/hello` | ❌ | 健康检查 |
| POST | `/api/wx/login` | ❌ | 小程序登录（真实微信登录或 mock） |
| GET | `/api/categories` | ❌ | 分类列表 |
| GET | `/api/products?categoryId=` | ❌ | 商品列表 |
| GET | `/api/products/{id}` | ❌ | 商品详情 |
| POST | `/api/orders` | ✅ | 提交订单（含库存原子扣减） |
| GET | `/api/orders` | ✅ | 我的订单（一次查出 items，无 N+1） |
| GET | `/api/orders/{id}` | ✅ | 订单详情（含商品明细） |

> 提交订单请求新增字段：`mealType`（dine_in/takeout/delivery）、`address`（配送地址）

### 管理端 `/api/admin/*`

| Method | URL | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/admin/login` | ❌ | 管理员登录 |
| GET | `/api/admin/orders?status=&page=&size=` | ✅ | 订单分页列表（含 items） |
| GET | `/api/admin/orders/{id}` | ✅ | 订单详情（含商品明细） |
| PUT | `/api/admin/orders/{id}/status` | ✅ | 修改订单状态 |
| GET | `/api/admin/categories` | ✅ | 分类列表 |
| POST | `/api/admin/categories` | ✅ | 新增分类 |
| PUT | `/api/admin/categories/{id}` | ✅ | 修改分类 |
| DELETE | `/api/admin/categories/{id}` | ✅ | 删除分类 |
| GET | `/api/admin/products?categoryId=` | ✅ | 商品列表 |
| POST | `/api/admin/products` | ✅ | 新增商品 |
| PUT | `/api/admin/products/{id}` | ✅ | 修改商品 |
| DELETE | `/api/admin/products/{id}` | ✅ | 删除商品 |

### 环境变量说明

| 变量 | 说明 | 默认值 |
|---|---|---|
| `JWT_SECRET` | JWT 签名密钥（生产必须改，至少 32 字符） | - |
| `WECHAT_APPID` | 微信小程序 appid（留空走 mock 登录） | - |
| `WECHAT_SECRET` | 微信小程序 secret | - |
| `WECHAT_FORCE_REAL` | 强制真实微信登录（mock 失败时是否报错） | false |
| `CORS_ALLOWED_ORIGINS` | CORS 允许的来源（多个逗号分隔） | - |
| `MYSQL_USER/PASSWORD` | 数据库账号密码 | myapp/<APP_PASSWORD> |

所有响应统一格式：
```json
{ "code": 0, "message": "ok", "data": {...} }
```

## 四、已完成的改造（P0+P1+P2）

- [x] 真实微信登录（调 jscode2session，未配置 appid 时走 mock）
- [x] JWT 区分角色（USER / ADMIN 两套 token，admin 接口必须 ADMIN 角色）
- [x] JWT secret 从环境变量注入（生产必须修改默认 secret）
- [x] CORS 收紧（指定 allowedOrigins，credentials 与 * 不共存）
- [x] 订单库存原子扣减（乐观锁：`stock = stock - N where stock >= N`）
- [x] 订单销量原子累加（`sales = sales + N`，去除先读后写）
- [x] 订单号改为时间戳+UUID 后6位（基本不撞）
- [x] 后台订单分页（page/size）
- [x] 后台订单详情展示商品明细（items）
- [x] @RestControllerAdvice 统一异常处理
- [x] MyBatis-Plus logic-delete 配置修正（已移除不存在的 deleted 字段引用）
- [x] myOrders 消除 N+1（一次查出所有 items）
- [x] 订单增加就餐方式（dine_in/takeout/delivery）和地址字段
- [x] project.config.json compileType 修正（game → miniprogram）
- [x] mine 页面 onShow 刷新（替代 onMounted）
- [x] product 页面 onLoad 读参（替代 getCurrentPages）
- [x] 前端 BASE_URL 通过 VITE_API_URL 环境变量管理

## 五、下一步可以加的功能

- [ ] 微信支付（接入 wxpay）
- [ ] 后台数据统计（今日营业额、订单趋势、热销榜）
- [ ] 商品图片上传（后台 + OSS/本地存储）
- [ ] 订单状态完整流转（待接单→制作中→待取餐→已完成）
- [ ] 用户地址管理
- [ ] 订单评价
- [ ] 优惠券/会员积分

## 六、默认账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 管理员 | admin | 123456 |

⚠️ **生产前必须修改 JWT_SECRET 和管理员密码**。
