'use strict';
/**
 * 用户管理：直连数据库，无任何权限/角色限制。
 */
const express = require('express');
const { ok, fail, httpError, failFrom } = require('../auth');
const { q, one, tx } = require('../db');

const router = express.Router();

/** 用户在任一家庭是 CHEF ⇒ t_user.is_chef = 1 */
async function syncGlobalChef(userId, conn = null) {
  const run = conn ? conn.execute.bind(conn) : null;
  let n;
  if (run) {
    const [rows] = await run('SELECT COUNT(*) AS c FROM t_family_member WHERE user_id = ? AND role = ?', [userId, 'CHEF']);
    n = rows[0].c;
  } else {
    const r = await one('SELECT COUNT(*) AS c FROM t_family_member WHERE user_id = ? AND role = ?', [userId, 'CHEF']);
    n = r.c;
  }
  const sql = 'UPDATE t_user SET is_chef = ? WHERE open_id = ?';
  const args = [Number(n) > 0 ? 1 : 0, userId];
  if (run) await run(sql, args); else await q(sql, args);
}

/** 重算活跃家庭 + 主厨镜像 */
async function rebalance(userId) {
  const rest = await one(
    'SELECT family_id FROM t_family_member WHERE user_id = ? ORDER BY joined_at ASC, id ASC LIMIT 1',
    [userId]
  );
  const next = rest ? rest.family_id : null;
  await q('UPDATE t_user SET active_family_id = ? WHERE open_id = ?', [next, userId]);
  await syncGlobalChef(userId);
}

/** 把该家庭里除 keepUserId 之外的主厨降为成员 */
async function demoteOtherChef(familyId, keepUserId, conn = null) {
  const run = conn ? conn.execute.bind(conn) : q;
  const chefs = conn
    ? (await run('SELECT user_id FROM t_family_member WHERE family_id = ? AND role = ? AND user_id <> ?', [familyId, 'CHEF', keepUserId]))[0]
    : await run('SELECT user_id FROM t_family_member WHERE family_id = ? AND role = ? AND user_id <> ?', [familyId, 'CHEF', keepUserId]);
  for (const c of chefs) {
    await run('UPDATE t_family_member SET role = ? WHERE family_id = ? AND user_id = ?', ['MEMBER', familyId, c.user_id]);
    await syncGlobalChef(c.user_id, conn);
  }
}

function normalizeRole(role) {
  if (role == null || String(role).trim() === '') return 'MEMBER';
  const r = String(role).trim().toUpperCase();
  return r === 'MEMBER' || r === 'CHEF' ? r : null;
}

// ==================== 读取 ====================

/** 用户列表（含所属家庭） */
router.get('/admin/users', async (req, res) => {
  const users = await q('SELECT * FROM t_user ORDER BY created_at ASC, open_id ASC');
  const members = await q(`
    SELECT m.user_id, m.family_id, m.role, m.joined_at, f.name AS family_name, f.code, f.owner_user_id
      FROM t_family_member m
      JOIN t_family f ON f.id = m.family_id
     ORDER BY m.joined_at ASC, m.id ASC
  `);
  const byUser = new Map();
  for (const m of members) {
    if (!byUser.has(m.user_id)) byUser.set(m.user_id, []);
    byUser.get(m.user_id).push({
      familyId: Number(m.family_id),
      familyName: m.family_name,
      code: m.code,
      role: m.role,
      isOwner: m.owner_user_id === m.user_id,
      active: Number(m.family_id) === Number(users.find((u) => u.open_id === m.user_id)?.active_family_id),
    });
  }
  return ok(res, users.map((u) => {
    const fams = byUser.get(u.open_id) || [];
    const active = fams.find((f) => f.active) || fams[0] || null;
    return {
      userId: u.open_id,
      nickname: u.nickname,
      avatarUrl: u.avatar_url,
      phone: u.phone,
      isChef: u.is_chef === 1,
      allowPush: u.allow_push === 1,
      activeFamilyId: u.active_family_id == null ? null : Number(u.active_family_id),
      createdAt: u.created_at,
      families: fams,
      primaryFamilyName: active ? active.familyName : null,
    };
  }));
});

/** 把用户加入家庭（已是成员则改角色） */
router.post('/admin/users/:userId/add-family', async (req, res) => {
  try {
    const userId = String(req.params.userId);
    const body = req.body || {};
    const familyId = body.familyId == null ? null : Number(body.familyId);
    if (!familyId) throw httpError(400, '参数不完整');
    const u = await one('SELECT * FROM t_user WHERE open_id = ?', [userId]);
    if (!u) throw httpError(404, '用户不存在');
    const f = await one('SELECT id FROM t_family WHERE id = ?', [familyId]);
    if (!f) throw httpError(404, '家庭不存在');
    const role = normalizeRole(body.role);
    if (!role) throw httpError(400, '角色只能是 MEMBER 或 CHEF');

    const existing = await one(
      'SELECT * FROM t_family_member WHERE family_id = ? AND user_id = ?',
      [familyId, userId]
    );

    await tx(async (conn) => {
      if (existing) {
        if (existing.role === 'OWNER') throw httpError(400, '创建者角色不可修改');
        await conn.execute('UPDATE t_family_member SET role = ? WHERE id = ?', [role, existing.id]);
      } else {
        await conn.execute(
          'INSERT INTO t_family_member (family_id, user_id, role, nickname, joined_at) VALUES (?, ?, ?, ?, NOW())',
          [familyId, userId, role, u.nickname]
        );
      }
      if (role === 'CHEF') await demoteOtherChef(familyId, userId, conn);
      if (u.active_family_id == null) {
        await conn.execute('UPDATE t_user SET active_family_id = ? WHERE open_id = ?', [familyId, userId]);
      }
    });
    await syncGlobalChef(userId);
    return ok(res, { familyId, role });
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 把用户移出家庭（创建者不可移出） */
router.post('/admin/users/:userId/remove-family', async (req, res) => {
  try {
    const userId = String(req.params.userId);
    const familyId = Number((req.body || {}).familyId);
    if (!familyId) throw httpError(400, '参数不完整');
    const m = await one('SELECT * FROM t_family_member WHERE family_id = ? AND user_id = ?', [familyId, userId]);
    if (!m) throw httpError(404, '该用户不在该家庭');
    if (m.role === 'OWNER') throw httpError(400, '创建者不能移出，请先转移所有权');
    await q('DELETE FROM t_family_member WHERE id = ?', [m.id]);
    await rebalance(userId);
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 调整角色（每家庭只保留一位主厨） */
router.post('/admin/users/:userId/set-role', async (req, res) => {
  try {
    const userId = String(req.params.userId);
    const body = req.body || {};
    const familyId = Number(body.familyId);
    const role = normalizeRole(body.role);
    if (!familyId) throw httpError(400, '参数不完整');
    if (!role) throw httpError(400, '角色只能是 MEMBER 或 CHEF');
    const m = await one('SELECT * FROM t_family_member WHERE family_id = ? AND user_id = ?', [familyId, userId]);
    if (!m) throw httpError(404, '该用户不在该家庭');
    if (m.role === 'OWNER') throw httpError(400, '创建者角色不可修改');
    await q('UPDATE t_family_member SET role = ? WHERE id = ?', [role, m.id]);
    if (role === 'CHEF') await demoteOtherChef(familyId, userId);
    await syncGlobalChef(userId);
    return ok(res, { familyId, role });
  } catch (e) {
    return failFrom(res, e);
  }
});

/**
 * 删除用户。
 *
 * 兼容"幽灵成员"：有些家庭成员的 user_id 在 t_user 里已经没有对应账号了
 * （历史数据，或有人直接改库删过账号）。这类用户在家庭成员列表里能正常显示
 * ——列表读的是 t_family_member 的昵称快照 —— 但如果只按 t_user 判断存在性，
 * 点删除就会报"用户不存在"。
 * 所以这里只要求"这个 id 在库里确实还有数据"，然后把它的痕迹全部清掉。
 */
/**
 * 删除用户（硬删除，级联清理全部关联数据）。
 *
 * 顺序严格按外键依赖从子到父：
 *   t_push_pending → t_push_log → t_menu_item_rating → t_menu_item
 *   → t_order → t_dish_blacklist → t_cart / t_family_member → t_user
 *
 * ⚠️ 不可逆：会一并删掉该用户的订单与推送历史。
 * 若要保留订单历史，需改为软删除（见 README 的「删除用户」说明）。
 *
 * 拒绝删除家庭创建者（t_family.owner_user_id）：删他等于毁掉整个家庭。
 */
router.delete('/admin/users/:userId', async (req, res) => {
  try {
    const userId = String(req.params.userId);
    const u = await one('SELECT open_id FROM t_user WHERE open_id = ?', [userId]);

    // 该用户创建的家庭 → 拒绝（会牵连其他成员的订单/数据）
    const owned = await q('SELECT id, name FROM t_family WHERE owner_user_id = ?', [userId]);
    if (owned.length) {
      throw httpError(400,
        `该用户是家庭「${owned.map(f => f.name).join('、')}」的创建者，请先在家庭管理中删除或转移该家庭`);
    }

    const members = await q('SELECT id FROM t_family_member WHERE user_id = ?', [userId]);
    const carts = await q('SELECT id FROM t_cart WHERE user_id = ?', [userId]);
    // 该用户的全部菜单项（含已进入订单的），及其评分
    const menus = await q('SELECT id FROM t_menu_item WHERE user_id = ?', [userId]);
    const menuIds = menus.map(m => m.id);
    const orders = await q('SELECT id FROM t_order WHERE user_id = ?', [userId]);
    const pushLogs = await q('SELECT id FROM t_push_log WHERE user_id = ?', [userId]);
    const pushPending = await q('SELECT id FROM t_push_pending WHERE user_id = ?', [userId]);
    const ratings = await q('SELECT id FROM t_menu_item_rating WHERE user_id = ?', [userId]);
    const blacklist = await q('SELECT id FROM t_dish_blacklist WHERE user_id = ?', [userId]);

    if (!u && !members.length && !carts.length && !menus.length && !orders.length) {
      throw httpError(404, '用户不存在');
    }

    await tx(async (conn) => {
      // 1) 推送（无子表，先删）
      await conn.execute('DELETE FROM t_push_pending WHERE user_id = ?', [userId]);
      await conn.execute('DELETE FROM t_push_log WHERE user_id = ?', [userId]);

      // 2) 菜单项评分：先删「评分人是该用户」的，再删「评的是该用户菜单项」的
      await conn.execute('DELETE FROM t_menu_item_rating WHERE user_id = ?', [userId]);
      if (menuIds.length) {
        const ph = menuIds.map(() => '?').join(',');
        await conn.execute(`DELETE FROM t_menu_item_rating WHERE menu_item_id IN (${ph})`, menuIds);
      }

      // 3) 菜单项（含已进入订单的：订单本体马上也要删，不能留孤儿行）
      await conn.execute('DELETE FROM t_menu_item WHERE user_id = ?', [userId]);

      // 4) 订单本体
      await conn.execute('DELETE FROM t_order WHERE user_id = ?', [userId]);

      // 5) 菜品黑名单
      await conn.execute('DELETE FROM t_dish_blacklist WHERE user_id = ?', [userId]);

      // 6) 家庭关系 / 购物车
      await conn.execute('DELETE FROM t_family_member WHERE user_id = ?', [userId]);
      await conn.execute('DELETE FROM t_cart WHERE user_id = ?', [userId]);

      // 7) 用户本体（幽灵成员没有这行）
      if (u) await conn.execute('DELETE FROM t_user WHERE open_id = ?', [userId]);
    });

    return ok(res, {
      hadAccount: !!u,
      removedMemberRows: members.length,
      removedCartRows: carts.length,
      removedMenuItemRows: menus.length,
      removedOrderRows: orders.length,
      removedPushLogRows: pushLogs.length,
      removedPushPendingRows: pushPending.length,
      removedRatingRows: ratings.length,
      removedBlacklistRows: blacklist.length,
    });
  } catch (e) {
    return failFrom(res, e, '删除失败');
  }
});

/** 强制下线：递增 token_version，旧 token 立刻失效 */
router.post('/admin/users/:userId/revoke', async (req, res) => {
  try {
    const userId = String(req.params.userId);
    const u = await one('SELECT token_version FROM t_user WHERE open_id = ?', [userId]);
    if (!u) {
      const ghost = await one('SELECT id FROM t_family_member WHERE user_id = ? LIMIT 1', [userId]);
      throw httpError(404, ghost ? '该用户没有账号记录，无需踢下线' : '用户不存在');
    }
    const cur = u.token_version == null ? 0 : Number(u.token_version);
    await q('UPDATE t_user SET token_version = ? WHERE open_id = ?', [cur + 1, userId]);
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

module.exports = router;
