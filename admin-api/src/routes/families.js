'use strict';
/**
 * 家庭管理：直连数据库，无任何权限/角色限制。
 *
 * 与旧后端保持同样的响应结构（admin-web 直接消费），但去掉了
 * "必须主厨 / APP_ADMIN_OPENID / 家庭作用域" 之类的限制。
 */
const express = require('express');
const { ok, fail, httpError, failFrom } = require('../auth');
const { q, one, scalar, tx } = require('../db');

const router = express.Router();

const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function genCode() {
  let s = '';
  for (let i = 0; i < 6; i++) s += CODE_CHARS[Math.floor(Math.random() * CODE_CHARS.length)];
  return s;
}
async function uniqueCode() {
  for (let i = 0; i < 20; i++) {
    const c = genCode();
    const n = await scalar('SELECT COUNT(*) FROM t_family WHERE code = ?', [c]);
    if (!Number(n)) return c;
  }
  throw httpError(500, '生成家庭码失败，请重试');
}

/** 用户在任一家庭是 CHEF ⇒ t_user.is_chef = 1（旧小程序仍读这个字段） */
async function syncGlobalChef(userId) {
  if (!userId) return;
  const n = await scalar(
    'SELECT COUNT(*) FROM t_family_member WHERE user_id = ? AND role = ?',
    [userId, 'CHEF']
  );
  await q('UPDATE t_user SET is_chef = ? WHERE open_id = ?', [Number(n) > 0 ? 1 : 0, userId]);
}

/** 重算用户的活跃家庭（取最早加入的剩余家庭；没有则 NULL） */
async function rebalanceActiveFamily(userId) {
  const rest = await one(
    'SELECT family_id FROM t_family_member WHERE user_id = ? ORDER BY joined_at ASC, id ASC LIMIT 1',
    [userId]
  );
  const next = rest ? rest.family_id : null;
  const u = await one('SELECT active_family_id FROM t_user WHERE open_id = ?', [userId]);
  if (!u) return;
  const changed = (u.active_family_id === null) !== (next === null) ||
    (u.active_family_id !== null && Number(u.active_family_id) !== Number(next));
  if (changed) {
    await q('UPDATE t_user SET active_family_id = ? WHERE open_id = ?', [next, userId]);
  }
  await syncGlobalChef(userId);
}

// ==================== 读取 ====================

/** 家庭列表（后台家庭切换器） */
router.get('/admin/families', async (req, res) => {
  const rows = await q(`
    SELECT f.id, f.name, f.code,
           (SELECT COUNT(*) FROM t_family_member m WHERE m.family_id = f.id) AS member_count
      FROM t_family f
     ORDER BY f.id ASC
  `);
  return ok(res, rows.map((r) => ({
    familyId: Number(r.id),
    name: r.name,
    code: r.code,
    memberCount: Number(r.member_count),
  })));
});

/** 家庭总览（含成员明细） */
router.get('/admin/families/overview', async (req, res) => {
  const fams = await q(`
    SELECT f.id, f.name, f.code, f.owner_user_id, f.created_at,
           (SELECT COUNT(*) FROM t_family_member m WHERE m.family_id = f.id) AS member_count
      FROM t_family f
     ORDER BY f.id ASC
  `);
  if (!fams.length) return ok(res, []);

  const ids = fams.map((f) => f.id);
  const placeholders = ids.map(() => '?').join(',');
  const members = await q(
    `SELECT family_id, user_id, nickname, role, joined_at
       FROM t_family_member
      WHERE family_id IN (${placeholders})
      ORDER BY joined_at ASC, id ASC`,
    ids
  );
  const users = await q('SELECT open_id, nickname FROM t_user');

  const nickOf = new Map(users.map((u) => [u.open_id, u.nickname]));
  const byFamily = new Map();
  for (const m of members) {
    const key = Number(m.family_id);
    if (!byFamily.has(key)) byFamily.set(key, []);
    byFamily.get(key).push({
      userId: m.user_id,
      nickname: m.nickname != null ? m.nickname : (nickOf.get(m.user_id) || null),
      role: m.role,
      joinedAt: m.joined_at,
    });
  }

  return ok(res, fams.map((f) => {
    const list = byFamily.get(Number(f.id)) || [];
    const chef = list.find((m) => m.role === 'CHEF');
    const ownerExists = nickOf.has(f.owner_user_id);
    return {
      familyId: Number(f.id),
      name: f.name,
      code: f.code,
      createdAt: f.created_at,
      ownerName: ownerExists ? (nickOf.get(f.owner_user_id) || null) : '管理员（后台创建）',
      chefName: chef ? chef.nickname : null,
      memberCount: Number(f.member_count),
      members: list,
    };
  }));
});

/** 创建家庭 */
router.post('/admin/families', async (req, res) => {
  try {
    const body = req.body || {};
    const name = String(body.name == null ? '' : body.name).trim();
    if (!name) throw httpError(400, '家庭名称不能为空');
    if (name.length > 32) throw httpError(400, '名称不能超过 32 字符');

    // 未指定创建者时，用一个标记值占位（后台管理员不是 t_user 里的用户）
    const ownerId = body.ownerUserId ? String(body.ownerUserId) : 'admin';
    const owner = ownerId === 'admin' ? null : await one('SELECT * FROM t_user WHERE open_id = ?', [ownerId]);
    if (ownerId !== 'admin' && !owner) throw httpError(404, '创建者用户不存在');

    const out = await tx(async (conn) => {
      const code = await uniqueCode();
      const [r] = await conn.execute(
        'INSERT INTO t_family (name, code, owner_user_id, created_at) VALUES (?, ?, ?, NOW())',
        [name, code, ownerId]
      );
      const familyId = r.insertId;
      if (owner) {
        await conn.execute(
          'INSERT INTO t_family_member (family_id, user_id, role, nickname, joined_at) VALUES (?, ?, ?, ?, NOW())',
          [familyId, ownerId, 'OWNER', owner.nickname]
        );
        if (owner.active_family_id == null) {
          await conn.execute('UPDATE t_user SET active_family_id = ? WHERE open_id = ?', [familyId, ownerId]);
        }
      }
      return { familyId, name, code };
    });
    return ok(res, out);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 更新家庭名称 / 加入码（加入码最多 8 位，字母数字，全局唯一，前端展示时转大写） */
router.put('/admin/families/:familyId', async (req, res) => {
  try {
    const id = Number(req.params.familyId);
    const body = req.body || {};
    const name = String(body.name == null ? '' : body.name).trim();
    if (!name) throw httpError(400, '家庭名称不能为空');
    if (name.length > 32) throw httpError(400, '名称不能超过 32 字符');
    const fam = await one('SELECT * FROM t_family WHERE id = ?', [id]);
    if (!fam) throw httpError(404, '家庭不存在');

    // —— 加入码（可编辑，可保留原码）——
    let code = body.code == null ? null : String(body.code).trim();
    if (code != null) {
      code = code.toUpperCase().replace(/[^A-Z0-9]/g, '');
      if (!code) throw httpError(400, '加入码不能为空');
      if (code.length < 4 || code.length > 8) throw httpError(400, '加入码需为 4~8 位字母或数字');
      // 全局唯一（排除自己）
      const dup = await one('SELECT id FROM t_family WHERE code = ? AND id <> ?', [code, id]);
      if (dup) throw httpError(400, `加入码 ${code} 已被其他家庭使用`);
    }

    if (code == null) {
      await q('UPDATE t_family SET name = ? WHERE id = ?', [name, id]);
    } else {
      await q('UPDATE t_family SET name = ?, code = ? WHERE id = ?', [name, code, id]);
    }
    return ok(res, { familyId: id, code: code || fam.code });
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 删除家庭（级联清理该家庭下的全部数据） */
router.delete('/admin/families/:familyId', async (req, res) => {
  try {
    const id = Number(req.params.familyId);
    if (!id) throw httpError(400, 'familyId 不能为空');
    const fam = await one('SELECT id FROM t_family WHERE id = ?', [id]);
    if (!fam) throw httpError(404, '家庭不存在');

    const memberIds = (await q('SELECT user_id FROM t_family_member WHERE family_id = ?', [id]))
      .map((m) => m.user_id);

    await tx(async (conn) => {
      const [dishes] = await conn.execute('SELECT id FROM t_dish WHERE family_id = ?', [id]);
      const dishIds = dishes.map((d) => d.id);
      if (dishIds.length) {
        const ph = dishIds.map(() => '?').join(',');
        await conn.execute(`DELETE FROM t_dish_ingredient WHERE dish_id IN (${ph})`, dishIds);
        await conn.execute(`DELETE FROM t_dish_blacklist WHERE dish_id IN (${ph})`, dishIds);
      }
      await conn.execute('DELETE FROM t_dish_ingredient WHERE family_id = ?', [id]);
      await conn.execute('DELETE FROM t_dish WHERE family_id = ?', [id]);
      await conn.execute('DELETE FROM t_menu_item WHERE family_id = ?', [id]);
      await conn.execute('DELETE FROM t_cart WHERE family_id = ?', [id]);
      await conn.execute('DELETE FROM t_ingredient WHERE family_id = ?', [id]);
      await conn.execute('DELETE FROM t_ingredient_category WHERE family_id = ?', [id]);
      await conn.execute('DELETE FROM t_family_member WHERE family_id = ?', [id]);
      await conn.execute('DELETE FROM t_family WHERE id = ?', [id]);
    });

    // 被解散家庭的原成员：重算活跃家庭与主厨镜像
    for (const uid of memberIds) {
      await rebalanceActiveFamily(uid);
    }
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

module.exports = router;
