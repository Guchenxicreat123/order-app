# UI 改造复核 · Bug 清单 + 体验优化建议

> 复核范围：miniprogram/ + admin-web/（后端 backend/ 未触碰）。整体方向正确——配色统一、动效丰富、玻璃拟态到位。但出现了**几处会直接跑不起来的硬伤**，以及**一些可以更上层楼的细节**。

---

## 一、🔴 跑不起来的 Bug（必须先修）

### B1. admin 后台分页功能 — 前端调用了后端没实现的接口

**位置**：`admin-web/src/views/Orders.vue`、`admin-web/src/api/index.js`
**现象**：前端期望后端返回 `{ records, total }` 格式，且 `listOrders({status, page, size})`。
**实际**：`backend/.../AdminController.java` 的 `/api/admin/orders` 只接收 `Integer status`，返回 `List<Order>`，**没有 page/size/records/total**。
**后果**：打开后台订单页 → axios 取到数组 → `orders.value = res.records` 时 **res 是数组，没有 .records → undefined** → 表格空 + 报错；el-pagination total=0。
新增的 `getOrderDetail(row.id)` 接口也不存在（后端只有用户端的 `/api/orders/{id}`，没有 `/api/admin/orders/{id}`）→ 点击详情 → 404/报错。

**修法**（二选一）：
- A. **后端改造**（推荐）：`AdminController` 新增/改造分页接口 `orders(Integer status, Integer page, Integer size)` 返回 `Page<Order>`（MyBatis-Plus `Page<T>` 自动转 records/total），并新增 `/api/admin/orders/{id}` 返回 `OrderVO`（含 items）。
- B. 前端回退：去掉分页与 getOrderDetail，恢复 `listOrders(status)` 写法。

### B2. 后端 Order 实体没有 mealType/address 字段

**位置**：`Orders.vue` 表格列、详情弹窗
**现象**：前端显示 `row.mealType` / `row.address`，但后端 `Order` 实体没有这两个字段（`t_order` 表也没有对应列）。
**后果**：永远显示 '-'，下单时传的 mealType/address 也被丢弃。
**修法**：后端 `init.sql` 加两列：`ALTER TABLE t_order ADD COLUMN meal_type VARCHAR(16), ADD COLUMN address VARCHAR(255);`；`Order` 实体加字段；`OrderController.create` 入参对象接收；详情/列表 VO 返回这两个字段。

### B3. `request.js` 的 `VITE_API_URL` 没生效

**位置**：`miniprogram/src/utils/request.js`

```js
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8006'
```

**问题**：`miniprogram/vite.config.js` 里**没有**配置 `define` / `loadEnv`，所以 `import.meta.env.VITE_API_URL` 是 undefined → 永远 fallback 到 localhost:8006。
**后果**：H5 端走 /api proxy 能跑通；小程序端直连 localhost 在真机/开发者工具里失败。

**修法**：二选一
- A. 在 vite.config.js 暴露变量：

```js
import { defineConfig, loadEnv } from 'vite'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [uni()],
    define: { 'import.meta.env.VITE_API_URL': JSON.stringify(env.VITE_API_URL || '') },
    server: { port: 5173, proxy: { '/api': { target: 'http://localhost:8006', changeOrigin: true } } }
  }
})
```
- B. 直接在 request.js 顶部写 `const BASE_URL = 'http://你的IP:8006'`，删除 env 写法。

### B4. admin 后台分页 size-change 改坏了 page 字段

**位置**：`admin-web/src/views/Orders.vue`

```js
const handlePageChange = (p) => { page.value = p; loadData() }
```

`el-pagination` 同时绑了 `@current-change` 和 `@size-change` 到同一个函数。size-change 触发时，`p` 是新的 size，赋给 page 是错的。
**修法**：

```js
const handleCurrentChange = (p) => { page.value = p; loadData() }
const handleSizeChange = (s) => { size.value = s; page.value = 1; loadData() }
```

模板 `@current-change="handleCurrentChange" @size-change="handleSizeChange"`

---

## 二、🟡 视觉/逻辑小 Bug

### B5. 购物车数量变化「弹跳动画」不会触发

**位置**：`cart.vue`
`items.value = getCart().map(i => ({ ...i, bounce: false }))`
模板 `:class="{ bounce: i.bounce }"`，但 `changeQty` 不会把 `i.bounce` 改为 true，所以弹跳动画永远不播放。
**修法**：

```js
const changeQty = (i, delta) => {
  // ... 改完数量后
  const idx = items.value.findIndex(x => x.productId === i.productId)
  if (idx >= 0) {
    items.value[idx] = { ...items.value[idx], bounce: true }
    setTimeout(() => { items.value[idx] = { ...items.value[idx], bounce: false } }, 300)
  }
  refresh()
}
```

### B6. `order.vue` 备注 chips 误删文字

**位置**：`order.vue` 的 `toggleChip`
`remark.value.includes(chip) → replace(chip, '')`
**问题**：用户输入「请打包一下」，再点「打包」chip → 触发误删 → 「请 一下」。
**修法**：用数组结构 `selectedChips: string[]` 管理，textarea 与 chips 共用一个最终字符串拼接。

### B7. mine.vue 的 KPI 用的是筛选后的数量

**位置**：`mine.vue`
`kpiPercent = displayOrders.length / 10`
**问题**：当用户切到「已完成」筛选，KPI 显示基于「已完成」单状态的进度，意义错乱。
**修法**：

```js
const totalOrders = computed(() => orders.value.length)
const kpiPercent = computed(() => Math.min(100, Math.round(totalOrders.value / 10 * 100)))
```

### B8. 首页 goCategory(c) 没有传参到分类页

**位置**：`index.vue`
`const goCategory = (c) => { uni.switchTab({ url: '/pages/category/category' }) }`
**问题**：上一轮已指出此 bug——点击「凉菜」宫格，仍然默认显示「热菜」。
**修法**：

```js
// index.vue
const goCategory = (c) => {
  uni.setStorageSync('target_category_id', c.id)
  uni.switchTab({ url: '/pages/category/category' })
}
// category.vue onShow 里读取
  const target = uni.getStorageSync('target_category_id')
  if (target) { current.value = target; uni.removeStorageSync('target_category_id') }
```

### B9. 商品详情页「食材标签」对所有商品都一样

**位置**：`product.vue`
`const ingredientTags = ['🥚 鸡蛋', '🧈 豆腐', '🌶️ 辣椒', '🍃 香葱']`
**问题**：无论什么商品都显示同样 4 个标签，误导。
**修法**：根据 `product.description` 关键词动态生成，或根据 `product.categoryId` 分类映射，或直接删掉这行展示。

### B10. admin 后台 status-card 的 count 永远 = 0

**位置**：`admin-web/src/views/Orders.vue`
`statusFilters = [...{ count: 0 }...]`，loadData 里 `(filterStatus.value === null || filterStatus.value === 1) { 重新查各状态数量（简化处理） }` — 这块是 TODO 没实现。
**修法**：调一次全量接口或加 `/api/admin/orders/statistics` 一次性查各状态总数。

---

## 三、🎨 体验优化建议（外观/易用性）

### O1. 首页加购「无感」— 加浮动购物车栏 / 飘字动画
现状：首页点 + 只 toast，没浮动购物车栏，看不到反馈。
建议：复用 category 的玻璃购物车栏逻辑到首页（或飘「+1」字到 tab bar 购物车图标）。

### O2. category 页切换分类时商品应重新入场动画
现状：filteredProducts 变化时 v-for diff 更新，animate-fadeUp 不会重播。
建议：`<view v-if="!loading" class="product-list" :key="current">` 重新挂载。

### O3. mine.vue 状态筛选后增加「当前显示 X 单」提示
现状：filterStatus !== null 时只显示「清除筛选 ×」，没显示当前列表数量。

### O4. 订单详情（用户端）页面缺失
现状：mine 页订单卡片不能点击进详情，只能看列表。
建议：新增 `pages/order-detail/order-detail.vue` 复用我的订单样式 + items 明细 + 状态时间线；点击订单卡 `uni.navigateTo` 进去。

### O5. product 页右上角加「已收藏 ❤️」切换

### O6. 订单提交成功后弹个「成就」卡片
现状：toast 后跳走，平淡。
建议：弹出满屏「🎉 下单成功」浮层，2 秒后跳走；体验「成交感」更强。

### O7. admin 后台加「新订单声音提醒」
现状：管理员需手动刷新。
建议：Orders.vue onMounted 内 setInterval 10s 拉一次，新订单时 `new Audio('/ding.mp3').play()` + 右下角弹通知卡片。

### O8. admin 后台商品/分类管理加拖拽排序
现状：sort 字段只能改数字，没视觉排序。
建议：用 `vuedraggable` 或 `@sortablejs` 拖拽改 sort 字段。

### O9. mine.vue 用户等级徽章写死为 LV.1
建议：根据订单数动态算等级 `Math.floor(totalOrders/10)+1`。

### O10. 首页产品图分类渐变底映射可能错位
`background: ${productGradients[p.categoryId % productGradients.length]}`
categoryId 模 5 可能与分类语义不对应（热菜=橙，凉菜=青...）。
建议：建映射表 `{1:0, 2:1, 3:2, 4:3, 5:4}` 或 `(p.categoryId - 1) % len`。

### O11. 首页「已售」加火焰等级感
sales >= 100 显示「🔥🔥🔥」，>= 50 「🔥🔥」，>= 10 「🔥」，制造稀缺感。

### O12. admin 后台登录页加加载状态过渡

### O13. admin 后台表格行高/字体偏小
加 `.el-table td { padding: 14px 0 !important; font-size: 14px; }`，更舒展。

### O14. 全局加「减少动效」开关（可访问性）

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

### O15. 首页 hero 移动端空白调整
H5 端用 `@media (min-width: 768px)` 调整。

### O16. 首页加「今日推荐」/「新品上市」角标
根据 `product.sales` 或 `createdAt` 自动打标签，「新品」青绿色、「招牌」金色。

### O17. 类别图标映射仅 9 个
新建分类若不匹配就 fallback 🍴。
建议：数据库 category 表加 icon 字段，后台选 emoji，后端返回带 icon。

### O18. 购物车金额闪动太频繁
加 throttle 或 debounce，300ms 内多次按不重复触发。

### O19. 输入框在玻璃拟态下可能看不清
加深 placeholder 颜色。

### O20. 暗色模式（可选）
CSS 变量已就绪，可加 `prefers-color-scheme: dark` 的覆盖。

---

## 四、动效/性能小坑

### P1. onPullDownRefresh 与 scroll-view refresher 冲突
**位置**：`category.vue` `<scroll-view scroll-y :refresher-enabled="true" @refresherrefresh="onRefresh">`
pages.json 的 index.vue 已开启 enablePullDownRefresh，但 category 没开。scroll-view 内置 refresher 与页面级下拉冲突。
修法：要么关闭 category 的 enablePullDownRefresh，只用 scroll-view 的；要么只用页面级。

### P2. 多个 fixed 光斑在长列表滚动时重绘
多个 radial-gradient + animation 持续动画。
建议：低端机降级 `@media (max-width: 375px)` 时减小 blur 半径；或 `will-change: transform` 提示 GPU 合成。

### P3. admin 分页改完后状态统计接口需另写
Promise.all 并发即可。

---

## 五、优先级建议（按 ROI 排序）

| 优先级 | 项 | 难度 | 价值 |
|---|---|---|---|
| 🔴 P0 | 修 B1（后端分页 + admin/orders/{id}） | 中 | 后台完全用不了 |
| 🔴 P0 | 修 B2（后端 mealType/address 字段） | 中 | 功能不可用 |
| 🔴 P0 | 修 B3（VITE_API_URL 暴露） | 极低 | 真机跑不通 |
| 🟡 P1 | 修 B4 / B5 / B6 / B7 / B8 / B9 / B10 | 低~中 | 体验一致性 |
| 🟡 P1 | 加 O1（首页购物车栏）+ O4（订单详情页） | 中 | 主流程更顺 |
| 🟡 P1 | 加 O7（admin 新订单声音） | 低 | 后台实时性 |
| 🟢 P2 | O2 切换动画 / O3 数量提示 / O10 渐变映射 / O14 减少动效 | 低 | 锦上添花 |
| 🟢 P2 | O6 下单成功弹层 / O8 拖拽排序 / O9 等级动态 | 中 | 长期运营 |

---

## 附：最快 5 分钟内的修复

1. 修 `vite.config.js` 加 define（H5/小程序 都能用环境变量）
2. 修 `cart.vue` 的 bounce 类换成正确触发方式
3. 修 `index.vue` 的 `goCategory` 用 setStorageSync 记住点击的分类 id
4. 修 `product.vue` 删掉写死的 ingredientTags，改成 description 关键词匹配
5. 修 `order.vue` 改 toggleChip 用 selectedChips 数组
6. 修 `mine.vue` 的 KPI 用总 orders
7. 修 `admin-web/Orders.vue` 分页 size-change 与 current-change 拆分

整体评价：UI 改造的视觉效果比上一轮进步明显，玻璃拟态 + 渐变 + 动效的组合已经很有「浮夸好看」的味道；但**前端的改造有 3 处依赖了后端未实现的功能**（分页、getOrderDetail、mealType/address 字段），以及一些**环境变量和动画细节**没接通。把 P0 修完后，剩下就是体验打磨了。