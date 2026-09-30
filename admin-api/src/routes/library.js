'use strict';
/**
 * 菜品分类 / 配菜分类 / 配菜：直连数据库，无任何权限限制。
 *
 * 家庭范围：请求头 X-Family-Id（后台家庭切换器）；缺省则不过滤。
 * 注意：后台"配菜管理"页把 /ingredients/public 当作本家庭的完整配菜列表用
 *（含已下架），所以这里返回该范围下的全部配菜，不做 status 过滤。
 */
const express = require('express');
const { ok, fail, httpError, failFrom } = require('../auth');
const { q, one, tx } = require('../db');

const router = express.Router();

/**
 * 解析当前家庭：请求头 X-Family-Id → DEFAULT_FAMILY_ID → 第一个家庭。
 * 后台刷新时请求头可能还没带上，所以必须有服务端兜底。不做任何权限校验。
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

async function resolveFamilyId(req) {
  const scope = await familyScope(req);
  if (scope != null) return scope;
  throw httpError(400, '还没有任何家庭，请先创建家庭');
}

// ==================== 菜品分类（平台级，家庭无关） ====================

router.get('/categories', async (req, res) => {
  const rows = await q('SELECT * FROM t_category ORDER BY sort ASC, id ASC');
  return ok(res, rows.map((c) => ({
    id: Number(c.id),
    name: c.name,
    sort: c.sort == null ? 0 : Number(c.sort),
    status: c.status == null ? 1 : Number(c.status),
  })));
});

// ==================== 配菜分类（家庭级 t_ingredient_category） ====================

router.get('/ingredient-categories', async (req, res) => {
  const scope = await familyScope(req);
  const rows = await q(
    `SELECT * FROM t_ingredient_category ${scope != null ? 'WHERE family_id = ?' : ''}
      ORDER BY sort ASC, id ASC`,
    scope != null ? [scope] : []
  );
  return ok(res, rows.map((c) => ({
    id: Number(c.id),
    familyId: Number(c.family_id),
    name: c.name,
    emoji: c.emoji,
    sort: c.sort == null ? 0 : Number(c.sort),
    createdAt: c.created_at,
  })));
});

router.post('/ingredient-categories', async (req, res) => {
  try {
    const b = req.body || {};
    const familyId = await resolveFamilyId(req);
    const r = await q(
      'INSERT INTO t_ingredient_category (name, emoji, sort, family_id, created_at) VALUES (?, ?, ?, ?, NOW())',
      [
        String(b.name == null ? '' : b.name).trim(),
        b.emoji == null ? '' : String(b.emoji),
        b.sort == null ? 0 : Number(b.sort),
        familyId,
      ]
    );
    return ok(res, { id: r.insertId });
  } catch (e) {
    return failFrom(res, e);
  }
});

router.put('/ingredient-categories/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const b = req.body || {};
    const c = await one('SELECT id FROM t_ingredient_category WHERE id = ?', [id]);
    if (!c) throw httpError(404, '分类不存在');
    await q('UPDATE t_ingredient_category SET name = ?, emoji = ?, sort = ? WHERE id = ?', [
      String(b.name == null ? '' : b.name).trim(),
      b.emoji == null ? '' : String(b.emoji),
      b.sort == null ? 0 : Number(b.sort),
      id,
    ]);
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 删除分类：该分类下的配菜保留，只是失去分类（category_id 置 0） */
router.delete('/ingredient-categories/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const c = await one('SELECT id FROM t_ingredient_category WHERE id = ?', [id]);
    if (!c) throw httpError(404, '分类不存在');
    await tx(async (conn) => {
      await conn.execute('UPDATE t_ingredient SET category_id = 0 WHERE category_id = ?', [id]);
      await conn.execute('DELETE FROM t_ingredient_category WHERE id = ?', [id]);
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

// ==================== 配菜（家庭级 t_ingredient） ====================

/** 后台当作"本家庭配菜库"，返回全部（含已下架） */
router.get('/ingredients/public', async (req, res) => {
  const scope = await familyScope(req);
  const rows = await q(
    `SELECT * FROM t_ingredient ${scope != null ? 'WHERE family_id = ?' : ''}
      ORDER BY category_id ASC, id ASC`,
    scope != null ? [scope] : []
  );
  return ok(res, rows.map(mapIngredient));
});

/** 兼容：GET /ingredients（未被后台使用） */
router.get('/ingredients', async (req, res) => {
  const scope = await familyScope(req);
  const rows = await q(
    `SELECT * FROM t_ingredient ${scope != null ? 'WHERE family_id = ?' : ''} ORDER BY id ASC`,
    scope != null ? [scope] : []
  );
  return ok(res, rows.map(mapIngredient));
});

function mapIngredient(i) {
  return {
    id: Number(i.id),
    familyId: Number(i.family_id),
    name: i.name,
    categoryId: i.category_id == null ? 0 : Number(i.category_id),
    unit: i.unit,
    price: Number(i.price),
    emoji: i.emoji,
    status: i.status == null ? 1 : Number(i.status),
    createdAt: i.created_at,
    updatedAt: i.updated_at,
  };
}

router.get('/ingredients/:id(\\d+)', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const i = await one('SELECT * FROM t_ingredient WHERE id = ?', [id]);
    if (!i) throw httpError(404, '配菜不存在');
    return ok(res, mapIngredient(i));
  } catch (e) {
    return failFrom(res, e);
  }
});

router.post('/ingredients', async (req, res) => {
  try {
    const b = req.body || {};
    const familyId = await resolveFamilyId(req);
    const r = await q(
      `INSERT INTO t_ingredient (name, category_id, unit, price, emoji, status, family_id, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())`,
      [
        String(b.name == null ? '' : b.name).trim(),
        b.categoryId == null || b.categoryId === '' ? 0 : Number(b.categoryId),
        b.unit == null ? '' : String(b.unit),
        b.price == null ? 0 : Number(b.price),
        b.emoji == null ? '' : String(b.emoji),
        b.status == null ? 1 : Number(b.status),
        familyId,
      ]
    );
    return ok(res, { id: r.insertId });
  } catch (e) {
    return failFrom(res, e);
  }
});

router.put('/ingredients/:id(\\d+)', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const b = req.body || {};
    const i = await one('SELECT * FROM t_ingredient WHERE id = ?', [id]);
    if (!i) throw httpError(404, '配菜不存在');
    await q(
      `UPDATE t_ingredient SET name = ?, category_id = ?, unit = ?, price = ?, emoji = ?, status = ?, updated_at = NOW()
        WHERE id = ?`,
      [
        b.name == null ? i.name : String(b.name).trim(),
        b.categoryId == null || b.categoryId === '' ? i.category_id : Number(b.categoryId),
        b.unit == null ? i.unit : String(b.unit),
        b.price == null ? i.price : Number(b.price),
        b.emoji == null ? i.emoji : String(b.emoji),
        b.status == null ? i.status : Number(b.status),
        id,
      ]
    );
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 启用/停用切换 */
router.put('/ingredients/:id(\\d+)/toggle', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const i = await one('SELECT id, status FROM t_ingredient WHERE id = ?', [id]);
    if (!i) throw httpError(404, '配菜不存在');
    const next = Number(i.status) === 1 ? 0 : 1;
    await q('UPDATE t_ingredient SET status = ?, updated_at = NOW() WHERE id = ?', [next, id]);
    return ok(res, { status: next });
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 删除配菜（连带清掉菜品配方里的引用，并重算受影响菜品的价格） */
router.delete('/ingredients/:id(\\d+)', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const i = await one('SELECT id FROM t_ingredient WHERE id = ?', [id]);
    if (!i) throw httpError(404, '配菜不存在');
    await tx(async (conn) => {
      const [affected] = await conn.execute(
        'SELECT DISTINCT dish_id FROM t_dish_ingredient WHERE ing_id = ?',
        [id]
      );
      await conn.execute('DELETE FROM t_dish_ingredient WHERE ing_id = ?', [id]);
      await conn.execute('DELETE FROM t_ingredient WHERE id = ?', [id]);
      // 配方变了，重算这些菜品的价格
      for (const row of affected) {
        const [ings] = await conn.execute(
          `SELECT i.price, di.amount FROM t_dish_ingredient di
             JOIN t_ingredient i ON i.id = di.ing_id WHERE di.dish_id = ?`,
          [row.dish_id]
        );
        let sum = 0;
        for (const r of ings) sum += (Number(r.price) || 0) * (Number(r.amount) || 0);
        await conn.execute('UPDATE t_dish SET price = ?, updated_at = NOW() WHERE id = ?', [
          Math.round((sum + Number.EPSILON) * 100) / 100,
          row.dish_id,
        ]);
      }
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

module.exports = router;
