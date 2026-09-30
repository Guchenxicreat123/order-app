'use strict';
/**
 * 订单管理：直连数据库，无任何权限限制。
 *
 * 状态约定（与小程序端一致）：
 *   订单 t_order.status     : 0=待确认, 1=已确认(全部结案), -1=已撤销
 *   菜品 t_menu_item.status : 0=待确认, 1=已确认, 2=已驳回, -1=已撤销
 *
 * 家庭范围：优先用请求头 X-Family-Id（后台的"家庭切换器"），
 *          没有该头时返回全部家庭的数据（不做任何权限校验）。
 */
const express = require('express');
const { ok, fail, httpError, failFrom } = require('../auth');
const { q, one, tx } = require('../db');

const router = express.Router();

/**
 * 解析后台当前选中的家庭。
 * 后台前端只有在"切换家庭"后才一定会带 X-Family-Id（刷新时可能还没带上），
 * 所以这里必须有服务端兜底：优先用请求头，其次 DEFAULT_FAMILY_ID，最后取第一个家庭。
 * 注意：这里不做任何权限校验 —— 任何家庭都能被查看和编辑。
 */
async function familyScope(req) {
  const raw = req.headers['x-family-id'];
  if (raw != null && String(raw).trim() !== '') {
    const n = Number(String(raw).trim());
    if (Number.isFinite(n) && n > 0) return n;
  }
  const envId = Number(process.env.DEFAULT_FAMILY_ID);
  if (Number.isFinite(envId) && envId > 0) return envId;
  const f = await one('SELECT id FROM t_family ORDER BY id ASC LIMIT 1');
  return f ? Number(f.id) : null;
}

/** DATETIME 统一转字符串（旧后端对 NULL 返回 ""，前端有 ts.length 之类的不设防读取） */
function dt(v) {
  return v == null ? '' : String(v);
}

function itemRow(mi) {
  return {
    itemId: Number(mi.id),
    dishId: mi.dish_id == null ? null : Number(mi.dish_id),
    dishName: mi.dish_name,
    dishEmoji: mi.dish_emoji,
    spiceLevel: mi.spice_level == null ? 0 : Number(mi.spice_level),
    price: Number(mi.price),
    status: mi.status == null ? 0 : Number(mi.status),
    userNickname: mi.user_nickname,
    remark: mi.remark,
    createdAt: dt(mi.created_at),
  };
}

async function itemsByOrder(orderIds) {
  if (!orderIds.length) return new Map();
  const ph = orderIds.map(() => '?').join(',');
  const rows = await q(
    `SELECT * FROM t_menu_item WHERE order_id IN (${ph}) ORDER BY id ASC`,
    orderIds
  );
  const map = new Map();
  for (const r of rows) {
    const k = Number(r.order_id);
    if (!map.has(k)) map.set(k, []);
    map.get(k).push(r);
  }
  return map;
}

async function listOrders(req, res, todayOnly) {
  const scope = await familyScope(req);
  const where = [];
  const args = [];
  if (scope != null) {
    where.push('family_id = ?');
    args.push(scope);
  }
  if (todayOnly) {
    where.push('created_at >= CURDATE() AND created_at < DATE_ADD(CURDATE(), INTERVAL 1 DAY)');
  }
  const sql = `SELECT * FROM t_order ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY created_at DESC, id DESC`;
  const orders = await q(sql, args);
  const map = await itemsByOrder(orders.map((o) => o.id));

  return ok(res, orders.map((o) => {
    const items = map.get(Number(o.id)) || [];
    let confirmedCount = 0;
    let rejectedCount = 0;
    for (const mi of items) {
      if (Number(mi.status) === 1) confirmedCount++;
      else if (Number(mi.status) === 2) rejectedCount++;
    }
    return {
      orderId: Number(o.id),
      familyId: Number(o.family_id),
      userId: o.user_id,
      userNickname: o.user_nickname,
      totalAmount: Number(o.total_amount),
      itemCount: Number(o.item_count),
      confirmedCount,
      rejectedCount,
      status: o.status == null ? 0 : Number(o.status),
      remark: o.remark,
      createdAt: dt(o.created_at),
      confirmedAt: dt(o.confirmed_at),
      items: items.map(itemRow),
    };
  }));
}

router.get('/orders', (req, res) => listOrders(req, res, false));
router.get('/orders/today', (req, res) => listOrders(req, res, true));

/** 订单详情 */
router.get('/orders/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const o = await one('SELECT * FROM t_order WHERE id = ?', [id]);
    if (!o) throw httpError(404, '订单不存在');
    const items = await q('SELECT * FROM t_menu_item WHERE order_id = ? ORDER BY id ASC', [id]);
    let confirmedCount = 0;
    let rejectedCount = 0;
    for (const mi of items) {
      if (Number(mi.status) === 1) confirmedCount++;
      else if (Number(mi.status) === 2) rejectedCount++;
    }
    return ok(res, {
      orderId: Number(o.id),
      familyId: Number(o.family_id),
      userId: o.user_id,
      userNickname: o.user_nickname,
      totalAmount: Number(o.total_amount),
      itemCount: Number(o.item_count),
      confirmedCount,
      rejectedCount,
      status: o.status == null ? 0 : Number(o.status),
      remark: o.remark,
      createdAt: dt(o.created_at),
      confirmedAt: dt(o.confirmed_at),
      // 新后台没有权限门槛：能打开后台就能确认
      canConfirm: true,
      items: items.map(itemRow),
    });
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 检查订单内菜品状态并同步订单状态 */
async function syncOrderStatus(orderId, conn = null) {
  const run = conn
    ? async (sql, a = []) => (await conn.execute(sql, a))[0]
    : async (sql, a = []) => q(sql, a);
  const items = await run('SELECT status FROM t_menu_item WHERE order_id = ?', [orderId]);
  if (!items.length) return;
  const pending = items.filter((mi) => mi.status == null || Number(mi.status) === 0).length;
  const order = (await run('SELECT id, status, confirmed_at FROM t_order WHERE id = ?', [orderId]))[0];
  if (!order) return;
  if (order.status != null && Number(order.status) === -1) return; // 已撤销不动
  if (pending > 0) {
    if (Number(order.status) !== 0) {
      await run('UPDATE t_order SET status = 0, updated_at = NOW() WHERE id = ?', [orderId]);
    }
  } else if (Number(order.status) !== 1) {
    await run(
      'UPDATE t_order SET status = 1, updated_at = NOW(), confirmed_at = IFNULL(confirmed_at, NOW()) WHERE id = ?',
      [orderId]
    );
  }
}

/** 一键确认整单（跳过已撤销/已驳回的菜品） */
router.post('/orders/:id/confirm', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const o = await one('SELECT * FROM t_order WHERE id = ?', [id]);
    if (!o) throw httpError(404, '订单不存在');
    if (Number(o.status) === 1) throw httpError(400, '订单已确认');
    const operator = req.admin.username;
    await tx(async (conn) => {
      await conn.execute(
        `UPDATE t_menu_item
            SET status = 1, confirmed_by = ?, confirmed_at = NOW(), updated_at = NOW()
          WHERE order_id = ? AND status NOT IN (-1, 2)`,
        [operator, id]
      );
      await syncOrderStatus(id, conn);
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 单菜确认 */
router.post('/orders/items/:id/confirm', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const mi = await one('SELECT * FROM t_menu_item WHERE id = ?', [id]);
    if (!mi) throw httpError(404, '菜品不存在');
    if (Number(mi.status) === -1) throw httpError(400, '菜品已撤销，不能修改状态');
    await tx(async (conn) => {
      await conn.execute(
        'UPDATE t_menu_item SET status = 1, confirmed_by = ?, confirmed_at = NOW(), updated_at = NOW() WHERE id = ?',
        [req.admin.username, id]
      );
      if (mi.order_id != null) await syncOrderStatus(Number(mi.order_id), conn);
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 单菜驳回 */
router.post('/orders/items/:id/reject', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const mi = await one('SELECT * FROM t_menu_item WHERE id = ?', [id]);
    if (!mi) throw httpError(404, '菜品不存在');
    if (Number(mi.status) === -1) throw httpError(400, '菜品已撤销，不能修改状态');
    await tx(async (conn) => {
      await conn.execute(
        'UPDATE t_menu_item SET status = 2, confirmed_by = ?, confirmed_at = NOW(), updated_at = NOW() WHERE id = ?',
        [req.admin.username, id]
      );
      if (mi.order_id != null) await syncOrderStatus(Number(mi.order_id), conn);
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 修改订单（下单者 / 金额 / 备注） */
router.put('/orders/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const body = req.body || {};
    const o = await one('SELECT * FROM t_order WHERE id = ?', [id]);
    if (!o) throw httpError(404, '订单不存在');

    const sets = [];
    const args = [];
    let newUserId = null;

    if (body.userId != null && String(body.userId).trim() !== '') {
      newUserId = String(body.userId).trim();
      const u = await one('SELECT * FROM t_user WHERE open_id = ?', [newUserId]);
      if (!u) throw httpError(404, '用户不存在');
      const mem = await one(
        'SELECT nickname FROM t_family_member WHERE family_id = ? AND user_id = ?',
        [o.family_id, newUserId]
      );
      const nickname = mem && mem.nickname && String(mem.nickname).trim()
        ? mem.nickname
        : (u.nickname || null);
      sets.push('user_id = ?', 'user_nickname = ?');
      args.push(newUserId, nickname);
    }
    if (body.totalAmount != null && body.totalAmount !== '') {
      const amt = Number(body.totalAmount);
      if (!Number.isFinite(amt) || amt < 0) throw httpError(400, '金额不合法');
      sets.push('total_amount = ?');
      args.push(amt);
    }
    if (body.remark != null) {
      sets.push('remark = ?');
      args.push(String(body.remark).slice(0, 255));
    }
    if (!sets.length) throw httpError(400, '没有需要更新的字段');

    sets.push('updated_at = NOW()');
    args.push(id);

    await tx(async (conn) => {
      await conn.execute(`UPDATE t_order SET ${sets.join(', ')} WHERE id = ?`, args);
      // 下单者变了：订单内每条菜品的下单者快照一起同步（旧后端同样这么做）
      if (newUserId) {
        const [[ord]] = await conn.execute('SELECT user_nickname FROM t_order WHERE id = ?', [id]);
        await conn.execute(
          'UPDATE t_menu_item SET user_id = ?, user_nickname = ?, updated_at = NOW() WHERE order_id = ?',
          [newUserId, ord.user_nickname, id]
        );
      }
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 删除订单（连带菜品） */
router.delete('/orders/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const o = await one('SELECT id FROM t_order WHERE id = ?', [id]);
    if (!o) throw httpError(404, '订单不存在');
    await tx(async (conn) => {
      await conn.execute('DELETE FROM t_menu_item WHERE order_id = ?', [id]);
      await conn.execute('DELETE FROM t_order WHERE id = ?', [id]);
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

module.exports = router;
