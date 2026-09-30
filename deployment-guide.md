# 部署说明 · 本次修复 + 重启

> 本次修改 7 个 bug + 1 个 schema 增量，admin-web dist 已重新构建。
> 当前会话无 docker daemon 权限（`permission denied while trying to connect to the Docker daemon socket`），
> 无法直接拉起容器。请在**拥有 docker 权限**的本机/服务器上运行下面的命令完成部署。

---

## 一、本次修复清单

### 后端 / 数据库
- `backend/init.sql` — `t_order` 加 `meal_type` / `address` 两列 + `ALTER ... IF NOT EXISTS` 兼容老库

### 用户端 (miniprogram/)
- `pages/cart/cart.vue` — B5：数量 +/- 后 `i.bounce=true`，300ms 后还原，弹跳动画生效
- `pages/order/order.vue` — B6：用 `selectedChips` 数组管 chips，textarea 与 chips 互不误删
- `pages/mine/mine.vue` — B7：KPI / 进度条改用 `totalOrders`（不受筛选影响）
- `pages/index/index.vue` — B8：`goCategory(c)` 用 `setStorageSync` 把目标分类 id 传给分类页
- `pages/category/category.vue` — B8：onShow 读取 `target_category_id` 后定位 + 清除
- `pages/product/product.vue` — B9：`ingredientTags` 改为 `computed`，根据商品名称 + 描述动态生成

### 管理后台 (admin-web/)
- `views/Orders.vue` — B4：`@size-change` 和 `@current-change` 拆成两个函数
- `views/Orders.vue` — B10：`loadData` 用 `Promise.all` 并发查「当前页 + 全量」，回填 4 个状态卡的 count
- `dist/` — 已重新构建（`npm run build` 跑通）

### 新增工具
- `deploy.sh` — 一键构建 + 跑数据库迁移 + 重启服务（含健康检查）

---

## 二、部署步骤（在有 docker 权限的机器上执行）

```bash
# 进入项目目录
cd /vol2/1000/File/aiwork/程序（工具）开发/点餐小程序

# 一键构建 + 部署（推荐）
bash deploy.sh

# 或者只用 docker compose 分步执行：
# 1) 手动跑一次数据库迁移（仅当 MySQL 容器已存在旧库时）
docker compose exec mysql mysql -uroot -p"\$MYSQL_ROOT_PASSWORD" order_app < backend/migrations/V2__add_meal_type_and_version.sql

# 2) 重新构建镜像（应用后端新代码 + admin-web 新 dist）
docker compose build --no-cache app admin-web

# 3) 重启容器
docker compose up -d

# 4) 查看日志确认启动正常
docker compose logs -f app
```

---

## 三、验证清单

部署后请依次验证：

### 后端 API
```bash
# 健康检查
curl http://localhost:8006/hello
# 期望: {"code":0,"message":"ok","data":...}

# 分类列表（无需登录）
curl http://localhost:8006/api/categories
# 期望: {"code":0,"message":"ok","data":[...5个分类]}

# 管理员登录
curl -X POST http://localhost:8006/api/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
# 期望: {"code":0,"message":"ok","data":{"token":"eyJ...","username":"admin"}}

# 用返回的 token 查订单列表（注意新格式 records/total）
TOKEN="<上面返回的token>"
curl -H "Authorization: Bearer \$TOKEN" 'http://localhost:8006/api/admin/orders?page=1&size=10'
# 期望: {"code":0,"message":"ok","data":{"records":[...],"total":N,"page":1,"size":10}}

# 用户端登录（mock code）
curl -X POST http://localhost:8006/api/wx/login \
  -H 'Content-Type: application/json' \
  -d '{"code":"test_curl"}'
# 期望: {"code":0,"message":"ok","data":{"token":"eyJ...","userId":N,...}}
```

### 数据库 schema
```bash
docker compose exec mysql mysql -uroot -p"\$MYSQL_ROOT_PASSWORD" order_app -e 'DESC t_order;'
# 期望能看到: meal_type VARCHAR(16) 和 address VARCHAR(255) 两列

docker compose exec mysql mysql -uroot -p"\$MYSQL_ROOT_PASSWORD" order_app -e 'DESC t_product;'
# 期望能看到: version INT (用于乐观锁)
```

### 前端
1. 后台 http://localhost:8007
   - admin / 123456 登录
   - 进入「订单管理」：表格能正常加载 + 分页能切换 + 4 个状态卡显示真实 count
   - 改订单状态 / 查看详情 / 改就餐方式等列显示正常
2. 用户端 http://localhost:5173（dev）/ 用微信开发者工具打开 `miniprogram/`
   - 首页点「凉菜」/「主食」宫格 → 切到分类页应自动定位到对应分类（B8 修复）
   - 购物车 +/- 数字弹跳 + 金额闪烁
   - 订单页输入「请打包一下」+ 点「打包」chip → 备注保留「请打包一下」（B6 修复）
   - 「我的」切筛选 → KPI 进度条不动（B7 修复）
   - 商品详情页食材标签：根据商品名变化（B9 修复）

---

## 四、回滚方案

如果新版本有问题：

```bash
# 看下历史镜像
docker images | grep order-app
# 回滚 app 镜像（如果有打过 tag）
docker compose down app admin-web
docker compose up -d
```

如果没有打过 tag，本地不能直接回滚，需要用 git reset 回退代码后重新构建：

```bash
git log --oneline -5   # 找上一次正常版本
git reset --hard <commit>
bash deploy.sh
```

---

## 五、注意事项

1. **MySQL 数据卷已存在**：旧库不会自动跑 init.sql；必须手动跑 migrations 目录下的 SQL 或用 `deploy.sh`（自动检测并执行未跑过的迁移）。
2. **`.env` 不要入库**：包含数据库密码、Cloudflare Token 等敏感信息。
3. **第一次部署** vs **后续升级**：
   - 第一次：`docker compose up -d` 会自动跑 init.sql 建表 + 插入测试数据 + 默认管理员账号 admin/123456
   - 后续升级：`bash deploy.sh` 自动检测迁移 + 重建镜像 + 重启
4. **微信小程序端**：用户端不参与 docker 部署，开发者用微信开发者工具「导入项目」选择 `miniprogram/`，填入自己的小程序 appid 后即可真机调试。
5. **真机访问后端**：局域网 IP 或 Cloudflare Tunnel 域名；`request.js` 用 `VITE_API_URL` 环境变量控制，记得在 `miniprogram/.env.production` 里设置实际地址。