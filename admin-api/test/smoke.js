'use strict';
/**
 * 后台管理服务端到端自测。
 *
 * 在 admin-api 容器内运行（能同时访问 admin-api:8008 和 mysql:3306）：
 *   docker cp admin-api/test/smoke.js order-admin-api:/tmp/smoke.js
 *   docker exec order-admin-api node /tmp/smoke.js
 *
 * 覆盖：登录/改密鉴权、全部读取接口的结构断言、以及
 *      家庭/用户/菜品/配菜/公共库/订单 的完整增删改生命周期。
 * 所有写入都用临时数据（名字带 __smoke_ 前缀），结束时清理干净。
 */
const mysql = require('mysql2/promise');

const BASE = process.env.SMOKE_BASE || 'http://127.0.0.1:8008/api';
const USERNAME = process.env.SMOKE_USER || 'admin';
const PASSWORD = process.env.SMOKE_PASS || '123456';

let pass = 0;
let failCount = 0;
const failures = [];

function ok(cond, label, extra) {
  if (cond) {
    pass++;
  } else {
    failCount++;
    failures.push(label + (extra === undefined ? '' : ` → ${JSON.stringify(extra)}`));
    console.log(`  ✗ ${label}`, extra === undefined ? '' : extra);
  }
}

async function api(method, path, body, headers = {}, token = TOKEN) {
  const h = { 'Content-Type': 'application/json', ...headers };
  if (token) h.Authorization = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method,
    headers: h,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  const text = await res.text();
  try { json = JSON.parse(text); } catch (_) { json = { raw: text.slice(0, 120) }; }
  return { status: res.status, body: json, data: json && json.data };
}

let TOKEN = '';

(async () => {
  const db = await mysql.createConnection({
    host: process.env.DB_HOST || 'mysql',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || 'changeme',
    database: process.env.DB_NAME || 'order_app',
    dateStrings: true,
  });

  console.log('\n=== 1. 认证 ===');
  const bad = await api('POST', '/admin/login', { username: USERNAME, password: 'definitely-wrong' }, {}, null);
  ok(bad.status === 401, '错误密码必须返回非 2xx（否则前端静默失败）', bad.status);
  ok(bad.body && typeof bad.body.message === 'string', '错误密码返回 message 字段', bad.body);

  const login = await api('POST', '/admin/login', { username: USERNAME, password: PASSWORD }, {}, null);
  ok(login.status === 200 && login.body.code === 0, '登录成功返回 code=0', login.body);
  ok(login.data && typeof login.data.token === 'string' && login.data.token.split('.').length === 3,
    '返回三段式 JWT', login.data && login.data.token && login.data.token.slice(0, 12));
  ok(login.data && login.data.username === USERNAME, '返回 username');
  TOKEN = login.data.token;

  const noAuth = await api('GET', '/admin/families', undefined, {}, null);
  ok(noAuth.status === 401, '无 token 访问业务接口 → 401', noAuth.status);

  // 旧小程序的 /chef/login 路径要兼容
  const legacy = await api('POST', '/chef/login', { username: USERNAME, password: PASSWORD }, {}, null);
  ok(legacy.status === 200 && legacy.body.code === 0, '兼容旧的 /chef/login 路径', legacy.status);

  console.log('\n=== 2. 读取接口结构 ===');
  const fams = await api('GET', '/admin/families');
  ok(Array.isArray(fams.data), '/admin/families 是裸数组', fams.data && typeof fams.data);
  if (Array.isArray(fams.data) && fams.data.length) {
    const f = fams.data[0];
    ok(typeof f.familyId === 'number', 'familyId 是 number', typeof f.familyId);
    ok(typeof f.memberCount === 'number', 'memberCount 是 number（前端无兜底渲染）', f.memberCount);
    ok(typeof f.name === 'string' && typeof f.code === 'string', 'name/code 是字符串');
  }

  const overview = await api('GET', '/admin/families/overview');
  ok(Array.isArray(overview.data), '/admin/families/overview 是裸数组');
  if (Array.isArray(overview.data) && overview.data.length) {
    const f = overview.data[0];
    ok(Array.isArray(f.members), 'overview[].members 是数组', f.members && typeof f.members);
    ok(typeof f.memberCount === 'number', 'overview[].memberCount 是 number', f.memberCount);
    ok(f.chefName === null || typeof f.chefName === 'string', 'chefName 是 string 或 null', f.chefName);
    if (f.members.length) {
      const m = f.members[0];
      ok(typeof m.userId === 'string', 'members[].userId 是字符串（与 users[].userId 同型）', typeof m.userId);
      ok(['OWNER', 'CHEF', 'MEMBER'].includes(m.role), 'members[].role 是 OWNER/CHEF/MEMBER', m.role);
    }
  }

  const users = await api('GET', '/admin/users');
  ok(Array.isArray(users.data), '/admin/users 是裸数组');
  if (Array.isArray(users.data) && users.data.length) {
    const u = users.data[0];
    ok(typeof u.userId === 'string', 'users[].userId 是字符串', typeof u.userId);
    ok(typeof u.isChef === 'boolean', 'users[].isChef 是布尔', typeof u.isChef);
    ok(typeof u.allowPush === 'boolean', 'users[].allowPush 是布尔', typeof u.allowPush);
    ok(Array.isArray(u.families), 'users[].families 是数组');
    if (Array.isArray(u.families) && u.families.length) {
      ok(typeof u.families[0].familyId === 'number', 'users[].families[].familyId 是 number', typeof u.families[0].familyId);
    }
  }

  const orders = await api('GET', '/orders');
  ok(Array.isArray(orders.data), '/orders 是裸数组');
  if (Array.isArray(orders.data) && orders.data.length) {
    const o = orders.data[0];
    ok(typeof o.createdAt === 'string' && o.createdAt.length >= 16, 'orders[].createdAt 是 ≥16 位字符串', o.createdAt);
    ok(typeof o.itemCount === 'number', 'orders[].itemCount 是 number', typeof o.itemCount);
    ok(typeof o.confirmedCount === 'number' && typeof o.rejectedCount === 'number', 'confirmedCount/rejectedCount 是 number');
    ok(Array.isArray(o.items), 'orders[].items 是数组');
  }

  const today = await api('GET', '/orders/today');
  ok(Array.isArray(today.data), '/orders/today 是裸数组');

  const cat = await api('GET', '/categories');
  ok(Array.isArray(cat.data) && cat.data.every((c) => typeof c.id === 'number'), '/categories 是数组且 id 为 number');

  const dishes = await api('GET', '/dishes/manage/all');
  ok(dishes.status === 200 && Array.isArray(dishes.data),
    '/dishes/manage/all 可访问（旧后端对 ADMIN token 返回 403）', dishes.status);
  if (Array.isArray(dishes.data) && dishes.data.length) {
    const d = dishes.data[0];
    ok(typeof d.status === 'number', 'dishes[].status 是 number', d.status);
    ok(typeof d.price === 'number', 'dishes[].price 是 number', typeof d.price);
    const detail = await api('GET', `/dishes/${d.id}`);
    ok(Array.isArray(detail.data.ingredients), 'dishes/{id}.ingredients 是数组');
    ok(detail.data.categoryId === null || typeof detail.data.categoryId === 'number', 'detail.categoryId 类型正确');
  }

  const ings = await api('GET', '/ingredients/public');
  ok(Array.isArray(ings.data) && ings.data.length > 0, '/ingredients/public 返回本家庭配菜', ings.data && ings.data.length);
  if (Array.isArray(ings.data) && ings.data.length) {
    const i = ings.data[0];
    ok(typeof i.id === 'number' && typeof i.unit === 'string', 'ingredients[].id/unit 类型正确');
    ok(typeof i.categoryId === 'number', 'ingredients[].categoryId 与分类 id 同为 number', typeof i.categoryId);
  }

  const ingCats = await api('GET', '/ingredient-categories');
  ok(Array.isArray(ingCats.data), '/ingredient-categories 是数组');
  ok(Array.isArray((await api('GET', '/public/dishes/admin/all')).data), '/public/dishes/admin/all 是数组');
  ok(Array.isArray((await api('GET', '/public/ingredients/admin/all')).data), '/public/ingredients/admin/all 是数组');
  const pubCats = await api('GET', '/public/ingredients/categories');
  ok(Array.isArray(pubCats.data), '/public/ingredients/categories 是数组');

  console.log('\n=== 3. 家庭增删改（含级联）===');
  const createdFam = await api('POST', '/admin/families', { name: '__smoke_家庭' });
  ok(createdFam.status === 200 && createdFam.body.code === 0, '创建家庭成功', createdFam.body);
  const testFamilyId = createdFam.data.familyId;
  const FH = { 'X-Family-Id': String(testFamilyId) };

  const renamed = await api('PUT', `/admin/families/${testFamilyId}`, { name: '__smoke_家庭改' });
  ok(renamed.status === 200 && renamed.body.code === 0, '重命名家庭成功', renamed.body);
  const checkFam = await api('GET', '/admin/families');
  ok(checkFam.data.some((f) => f.familyId === testFamilyId && f.name === '__smoke_家庭改'), '新家庭立刻出现在列表里');

  const dupFam = await api('POST', '/admin/families', { name: '' });
  ok(dupFam.status === 400, '空家庭名 → 400 + 可读提示', dupFam.body && dupFam.body.message);

  console.log('\n=== 4. 配菜分类 / 配菜生命周期（测试家庭内）===');
  const newCat = await api('POST', '/ingredient-categories', { name: '__smoke_分类', emoji: '🧪', sort: 9 }, FH);
  ok(newCat.status === 200 && newCat.body.code === 0, '创建配菜分类成功', newCat.body);
  const testCatId = newCat.data.id;

  const newIng = await api('POST', '/ingredients', {
    name: '__smoke_配料', categoryId: testCatId, unit: '克', price: 12.5, emoji: '🧂', status: 1,
  }, FH);
  ok(newIng.status === 200 && newIng.body.code === 0, '创建配菜成功', newIng.body);
  const testIngId = newIng.data.id;

  const dupIng = await api('POST', '/ingredients', {
    name: '__smoke_配料', categoryId: testCatId, unit: '克', price: 1, emoji: '', status: 1,
  }, FH);
  ok(dupIng.status === 400 && /同名/.test(dupIng.body.message || ''),
    '同家庭同名配菜 → 唯一键冲突被翻译成可读提示', dupIng.body);

  const updIng = await api('PUT', `/ingredients/${testIngId}`, {
    name: '__smoke_配料改', categoryId: testCatId, unit: '克', price: 20, emoji: '🧂', status: 1,
  }, FH);
  ok(updIng.status === 200, '更新配菜成功', updIng.body);

  const listIng = await api('GET', '/ingredients/public', undefined, FH);
  const found = (listIng.data || []).find((i) => i.id === testIngId);
  ok(found && found.name === '__smoke_配料改' && Number(found.price) === 20, '更新后列表可见且字段正确', found);

  const toggled = await api('PUT', `/ingredients/${testIngId}/toggle`, undefined, FH);
  ok(toggled.status === 200, '切换配菜状态成功', toggled.body);
  const listIng2 = await api('GET', '/ingredients/public', undefined, FH);
  const found2 = (listIng2.data || []).find((i) => i.id === testIngId);
  ok(found2 && found2.status === 0, '停用后仍出现在后台列表里（含下架数据）', found2 && found2.status);

  console.log('\n=== 5. 菜品生命周期（含配方与自动算价）===');
  const createDish = await api('POST', '/dishes', {
    name: '__smoke_菜品', imageEmoji: '🍜', categoryId: 1, spiceLevel: 1,
    ingredients: [{ ingId: testIngId, amount: 200, unit: '克' }],
  }, FH);
  ok(createDish.status === 200 && createDish.body.code === 0, '创建菜品成功', createDish.body);
  const testDishId = createDish.data.id;

  const dishDetail = await api('GET', `/dishes/${testDishId}`);
  ok(dishDetail.data.ingredients.length === 1 && dishDetail.data.ingredients[0].ingId === testIngId,
    '配方写入正确', dishDetail.data.ingredients);
  ok(Number(dishDetail.data.price) === 4000,
    '价格 = Σ(单价×用量) = 20×200 = 4000（与旧后端一致）', dishDetail.data.price);

  const updDish = await api('PUT', `/dishes/${testDishId}`, {
    name: '__smoke_菜品改', imageEmoji: '🍲', categoryId: 2, spiceLevel: 2,
    ingredients: [{ ingId: testIngId, amount: 100, unit: '克' }],
  }, FH);
  ok(updDish.status === 200, '更新菜品成功', updDish.body);
  const dishDetail2 = await api('GET', `/dishes/${testDishId}`);
  ok(Number(dishDetail2.data.price) === 2000, '改用量后价格重算为 2000', dishDetail2.data.price);
  ok(dishDetail2.data.ingredients.length === 1, '配方为全量替换（无残留）', dishDetail2.data.ingredients.length);

  const dupPayload = await api('PUT', `/dishes/${testDishId}`, {
    name: '__smoke_菜品改', ingredients: [
      { ingId: testIngId, amount: 10, unit: '克' },
      { ingId: testIngId, amount: 20, unit: '克' },
    ],
  }, FH);
  ok(dupPayload.status === 200, '同一配料重复提交不会触发唯一键 500', dupPayload.body);

  const toggleDish = await api('PUT', `/dishes/${testDishId}/status`, undefined, FH);
  ok(toggleDish.status === 200 && toggleDish.data.status === 0, '菜品上下架切换成功', toggleDish.data);

  console.log('\n=== 6. 公共菜品库 / 公共配菜库 ===');
  const pubDish = await api('POST', '/public/dishes', {
    name: '__smoke_公共菜', imageEmoji: '🍱', categoryId: 1, spiceLevel: 0, description: '测试', status: 1,
  });
  ok(pubDish.status === 200 && pubDish.body.code === 0, '创建公共菜品成功', pubDish.body);
  // 前端会把整行回传（含 id/createdAt 等只读字段），必须容忍
  const echoed = await api('PUT', `/public/dishes/${pubDish.data.id}`, {
    id: pubDish.data.id, name: '__smoke_公共菜改', imageEmoji: '🍱', categoryId: 1,
    spiceLevel: 0, description: '测试改', status: 0,
    createdAt: '2020-01-01 00:00:00', updatedAt: null, somethingUnknown: 1,
  });
  ok(echoed.status === 200, '公共菜品更新容忍整行回传与未知字段', echoed.body);

  const pubIng = await api('POST', '/public/ingredients', {
    name: '__smoke_公共配料', categoryId: 64, unit: '克', price: 3.5, emoji: '🥬', status: 1,
  });
  ok(pubIng.status === 200, '创建公共配菜成功', pubIng.body);

  console.log('\n=== 7. 订单：合成一单走完确认/驳回/改/删 ===');
  const [uRow] = await db.execute('SELECT open_id, nickname FROM t_user LIMIT 1');
  const testUser = uRow[0];
  const [ordIns] = await db.execute(
    `INSERT INTO t_order (family_id, user_id, user_nickname, total_amount, item_count, status, remark, created_at, updated_at)
     VALUES (?, ?, ?, 30.00, 2, 0, '__smoke', NOW(), NOW())`,
    [testFamilyId, testUser.open_id, testUser.nickname]
  );
  const testOrderId = ordIns.insertId;
  const [itemA] = await db.execute(
    `INSERT INTO t_menu_item (user_id, family_id, order_id, user_nickname, dish_id, dish_name, dish_emoji, spice_level, price, status, version, created_at, updated_at)
     VALUES (?, ?, ?, ?, NULL, '__smoke_自定义菜', '🍳', 0, 10.00, 0, 0, NOW(), NOW())`,
    [testUser.open_id, testFamilyId, testOrderId, testUser.nickname]
  );
  const [itemB] = await db.execute(
    `INSERT INTO t_menu_item (user_id, family_id, order_id, user_nickname, dish_id, dish_name, dish_emoji, spice_level, price, status, version, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, '__smoke_已点菜', '🍱', 1, 20.00, 0, 0, NOW(), NOW())`,
    [testUser.open_id, testFamilyId, testOrderId, testUser.nickname, testDishId]
  );

  const detail = await api('GET', `/orders/${testOrderId}`);
  ok(detail.data.canConfirm === true, 'canConfirm 是字面量 true（严格布尔门槛）', detail.data.canConfirm);
  ok(Array.isArray(detail.data.items) && detail.data.items.length === 2, '详情 items 是数组且完整', detail.data.items && detail.data.items.length);
  const custom = detail.data.items.find((i) => i.itemId === itemA.insertId);
  ok(custom.dishId === null, '自定义菜 dishId 为 null（前端据此显示"自定义"）', custom.dishId);

  const rj = await api('POST', `/orders/items/${itemA.insertId}/reject`);
  ok(rj.status === 200, '驳回单个菜品成功', rj.body);
  const afterReject = await api('GET', `/orders/${testOrderId}`);
  ok(afterReject.data.items.find((i) => i.itemId === itemA.insertId).status === 2, '驳回后 status=2', null);
  ok(afterReject.data.rejectedCount === 1 && afterReject.data.confirmedCount === 0, 'rejectedCount 读取时计算正确', afterReject.data.rejectedCount);

  const cf = await api('POST', `/orders/items/${itemB.insertId}/confirm`);
  ok(cf.status === 200, '确认单个菜品成功', cf.body);
  const afterConfirm = await api('GET', `/orders/${testOrderId}`);
  ok(afterConfirm.data.status === 1, '全部结案后订单自动升为已确认(1)', afterConfirm.data.status);

  const updOrder = await api('PUT', `/orders/${testOrderId}`, {
    userId: testUser.open_id, totalAmount: 88.88, remark: '__smoke 改',
  });
  ok(updOrder.status === 200, '修改订单成功', updOrder.body);
  const afterUpd = await api('GET', `/orders/${testOrderId}`);
  ok(Number(afterUpd.data.totalAmount) === 88.88 && afterUpd.data.remark === '__smoke 改', '金额/备注已更新', afterUpd.data.totalAmount);

  const delOrder = await api('DELETE', `/orders/${testOrderId}`);
  ok(delOrder.status === 200, '删除订单成功', delOrder.body);
  const [goneItems] = await db.execute('SELECT COUNT(*) c FROM t_menu_item WHERE order_id = ?', [testOrderId]);
  ok(Number(goneItems[0].c) === 0, '删除订单时连带删除菜品行', goneItems[0].c);

  console.log('\n=== 8. 用户：加入/角色/移出/踢下线/删除 ===');
  const [tmpIns] = await db.execute(
    `INSERT INTO t_user (open_id, nickname, is_chef, allow_push, token_version, created_at, updated_at)
     VALUES ('__smoke_user', '__smoke_用户', 0, 0, 0, NOW(), NOW())`
  );
  ok(tmpIns.insertId >= 0, '合成测试用户创建成功');

  const addFam = await api('POST', `/admin/users/__smoke_user/add-family`, { familyId: testFamilyId, role: 'CHEF' });
  ok(addFam.status === 200 && addFam.data.role === 'CHEF', '加入家庭并设为主厨成功', addFam.body);
  const [chefFlag] = await db.execute('SELECT is_chef FROM t_user WHERE open_id = ?', ['__smoke_user']);
  ok(Number(chefFlag[0].is_chef) === 1, 'is_chef 全局镜像已同步为 1', chefFlag[0].is_chef);

  const setRole = await api('POST', `/admin/users/__smoke_user/set-role`, { familyId: testFamilyId, role: 'MEMBER' });
  ok(setRole.status === 200, '调整角色成功', setRole.body);
  const [chefFlag2] = await db.execute('SELECT is_chef FROM t_user WHERE open_id = ?', ['__smoke_user']);
  ok(Number(chefFlag2[0].is_chef) === 0, '降为成员后 is_chef 回到 0', chefFlag2[0].is_chef);

  const rmFam = await api('POST', `/admin/users/__smoke_user/remove-family`, { familyId: testFamilyId });
  ok(rmFam.status === 200, '移出家庭成功', rmFam.body);

  const revoke = await api('POST', `/admin/users/__smoke_user/revoke`);
  ok(revoke.status === 200, '踢下线成功', revoke.body);
  const [tv] = await db.execute('SELECT token_version FROM t_user WHERE open_id = ?', ['__smoke_user']);
  ok(Number(tv[0].token_version) === 1, 'token_version 递增为 1', tv[0].token_version);

  const delUser = await api('DELETE', `/admin/users/__smoke_user`);
  ok(delUser.status === 200, '删除用户成功', delUser.body);

  console.log('\n=== 8a. 删用户不能掏空他的历史订单 ===');
  // 造：一个用户 + 一条未成单的菜单残留 + 一个订单 + 订单里的菜
  await db.execute(
    `INSERT INTO t_user (open_id, nickname, is_chef, allow_push, token_version, created_at, updated_at)
     VALUES ('__smoke_owner', '__smoke_下单人', 0, 0, 0, NOW(), NOW())`
  );
  const [keepOrd] = await db.execute(
    `INSERT INTO t_order (family_id, user_id, user_nickname, total_amount, item_count, status, remark, created_at, updated_at)
     VALUES (?, '__smoke_owner', '__smoke_下单人', 10.00, 1, 0, '__smoke_keep', NOW(), NOW())`,
    [testFamilyId]
  );
  await db.execute(
    `INSERT INTO t_menu_item (user_id, family_id, order_id, user_nickname, dish_id, dish_name, dish_emoji, spice_level, price, status, version, created_at, updated_at)
     VALUES ('__smoke_owner', ?, ?, '__smoke_下单人', NULL, '__smoke_订单里的菜', '🍚', 0, 10.00, 0, 0, NOW(), NOW())`,
    [testFamilyId, keepOrd.insertId]
  );
  await db.execute(
    `INSERT INTO t_menu_item (user_id, family_id, order_id, user_nickname, dish_id, dish_name, dish_emoji, spice_level, price, status, version, created_at, updated_at)
     VALUES ('__smoke_owner', ?, NULL, '__smoke_下单人', NULL, '__smoke_未成单残留', '🥢', 0, 5.00, 0, 0, NOW(), NOW())`,
    [testFamilyId]
  );

  const delOwner = await api('DELETE', '/admin/users/__smoke_owner');
  ok(delOwner.status === 200, '删除该用户成功', delOwner.body);
  ok(delOwner.data && delOwner.data.keptOrderItemRows === 1,
    '返回里标明保留了 1 行订单菜品', delOwner.data);
  ok(delOwner.data && delOwner.data.removedDraftMenuItemRows === 1,
    '只清掉了 1 行未成单的菜单残留', delOwner.data && delOwner.data.removedDraftMenuItemRows);

  const [ordLeft] = await db.execute('SELECT COUNT(*) c FROM t_order WHERE id = ?', [keepOrd.insertId]);
  ok(Number(ordLeft[0].c) === 1, '订单本体还在', ordLeft[0].c);
  const [itemsLeft] = await db.execute(
    'SELECT dish_name FROM t_menu_item WHERE order_id = ?', [keepOrd.insertId]
  );
  ok(itemsLeft.length === 1 && itemsLeft[0].dish_name === '__smoke_订单里的菜',
    '订单里的菜还在（订单详情不会变空）', itemsLeft);
  const [draftLeft] = await db.execute(
    "SELECT COUNT(*) c FROM t_menu_item WHERE user_id = '__smoke_owner' AND order_id IS NULL"
  );
  ok(Number(draftLeft[0].c) === 0, '未成单的菜单残留已清掉', draftLeft[0].c);

  const ordDetail = await api('GET', `/orders/${keepOrd.insertId}`);
  ok(ordDetail.data && ordDetail.data.items.length === 1,
    '接口层面：订单详情仍能返回这道菜', ordDetail.data && ordDetail.data.items.length);
  ok((await api('GET', `/admin/users`)).data.every(u => u.userId !== '__smoke_owner'),
    '该用户已从用户列表消失');

  await db.execute('DELETE FROM t_menu_item WHERE order_id = ?', [keepOrd.insertId]);
  await db.execute('DELETE FROM t_order WHERE id = ?', [keepOrd.insertId]);

  console.log('\n=== 8b. 幽灵成员（只有成员行、没有账号行）也能删 ===');
  // 场景：家庭成员列表读的是 t_family_member 的昵称快照，能正常显示；
  //       但 t_user 里已经没有这行账号，删除时不能报"用户不存在"。
  await db.execute(
    'INSERT INTO t_family_member (family_id, user_id, role, nickname, joined_at) VALUES (?, ?, ?, ?, NOW())',
    [testFamilyId, '__smoke_ghost', 'MEMBER', '__smoke_幽灵']
  );
  const ghostShown = await api('GET', '/admin/families/overview');
  const ghostFam = (ghostShown.data || []).find((f) => f.familyId === testFamilyId);
  ok(ghostFam && ghostFam.members.some((m) => m.userId === '__smoke_ghost'),
    '幽灵成员在家庭列表里正常显示（来源是成员表快照）', ghostFam && ghostFam.members.map((m) => m.userId));

  const delGhost = await api('DELETE', '/admin/users/__smoke_ghost');
  ok(delGhost.status === 200, '幽灵成员可以删除（不再报"用户不存在"）', delGhost.body);
  ok(delGhost.data && delGhost.data.hadAccount === false, '返回里标明该 id 本来就没有账号行', delGhost.data);

  const [ghostLeft] = await db.execute('SELECT COUNT(*) c FROM t_family_member WHERE user_id = ?', ['__smoke_ghost']);
  ok(Number(ghostLeft[0].c) === 0, '幽灵成员的成员行已被清掉', ghostLeft[0].c);

  const delUnknown = await api('DELETE', '/admin/users/__definitely_not_exist__');
  ok(delUnknown.status === 404, '完全查不到的 id 仍然返回 404', delUnknown.status);

  // 真实用户：删完账号行和成员行都要消失
  const [realLeft] = await db.execute(
    'SELECT (SELECT COUNT(*) FROM t_user WHERE open_id = ?) u, (SELECT COUNT(*) FROM t_family_member WHERE user_id = ?) m',
    ['__smoke_user', '__smoke_user']
  );
  ok(Number(realLeft[0].u) === 0 && Number(realLeft[0].m) === 0, '真实用户的账号行与成员行都已删除', realLeft[0]);

  console.log('\n=== 9. 修改密码（旧令牌应立即失效）===');
  const changed = await api('POST', '/admin/change-password', { oldPassword: PASSWORD, newPassword: '__smoke_pw123' });
  ok(changed.status === 200, '改密成功', changed.body);
  const oldTokenUse = await api('GET', '/admin/families');
  ok(oldTokenUse.status === 401, '改密后旧令牌立即失效（401）', oldTokenUse.status);

  const newLogin = await api('POST', '/admin/login', { username: USERNAME, password: '__smoke_pw123' }, {}, null);
  ok(newLogin.status === 200 && newLogin.body.code === 0, '新密码可以登录', newLogin.body && newLogin.body.message);
  TOKEN = newLogin.data.token;

  const back = await api('POST', '/admin/change-password', { oldPassword: '__smoke_pw123', newPassword: PASSWORD });
  ok(back.status === 200, '改回原密码成功', back.body);
  const finalLogin = await api('POST', '/admin/login', { username: USERNAME, password: PASSWORD }, {}, null);
  TOKEN = finalLogin.data.token;
  ok(finalLogin.status === 200, '原密码恢复可用');

  console.log('\n=== 10. 清理测试数据 ===');
  await api('DELETE', `/public/dishes/${pubDish.data.id}`);
  await api('DELETE', `/public/ingredients/${pubIng.data.id}`);
  await api('DELETE', `/dishes/${testDishId}`, undefined, FH);
  await api('DELETE', `/ingredients/${testIngId}`, undefined, FH);
  await api('DELETE', `/ingredient-categories/${testCatId}`, undefined, FH);
  const delFam = await api('DELETE', `/admin/families/${testFamilyId}`);
  ok(delFam.status === 200, '删除测试家庭成功（级联）', delFam.body);

  const [leftover] = await db.execute(
    `SELECT
       (SELECT COUNT(*) FROM t_family WHERE name LIKE '__smoke%') fam,
       (SELECT COUNT(*) FROM t_dish WHERE name LIKE '__smoke%') dish,
       (SELECT COUNT(*) FROM t_ingredient WHERE name LIKE '__smoke%') ing,
       (SELECT COUNT(*) FROM t_ingredient_category WHERE name LIKE '__smoke%') ingcat,
       (SELECT COUNT(*) FROM t_public_dish WHERE name LIKE '__smoke%') pdish,
       (SELECT COUNT(*) FROM t_public_ingredient WHERE name LIKE '__smoke%') ping,
       (SELECT COUNT(*) FROM t_user WHERE open_id LIKE '__smoke%') usr,
       (SELECT COUNT(*) FROM t_family_member WHERE user_id LIKE '__smoke%') ghost,
       (SELECT COUNT(*) FROM t_user WHERE open_id LIKE '__smoke%') ghost_user,
       (SELECT COUNT(*) FROM t_order WHERE remark LIKE '__smoke%') smoke_order,
       (SELECT COUNT(*) FROM t_menu_item WHERE dish_name LIKE '__smoke%') smoke_item,
       (SELECT COUNT(*) FROM t_order WHERE remark = '__smoke') ord`
  );
  const l = leftover[0];
  ok(Object.values(l).every((v) => Number(v) === 0), '测试数据已全部清理', l);

  await db.end();

  console.log(`\n================ 结果 ================`);
  console.log(`通过 ${pass} 项，失败 ${failCount} 项`);
  if (failCount) {
    console.log('失败明细：');
    for (const f of failures) console.log('  - ' + f);
    process.exit(1);
  }
  console.log('全部通过 ✅');
  process.exit(0);
})().catch((e) => {
  console.error('自测脚本异常：', e);
  process.exit(2);
});
