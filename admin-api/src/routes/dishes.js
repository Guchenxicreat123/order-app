'use strict';
/**
 * 菜品管理（家庭菜单）：直连数据库，无任何权限限制。
 */
const express = require('express');
const { ok, fail, httpError, failFrom } = require('../auth');
const { q, one, tx } = require('../db');

const router = express.Router();

/**
 * 解析当前查看/编辑的家庭。
 * 后台刷新时请求头 X-Family-Id 可能还没带上 → 服务端兜底（DEFAULT_FAMILY_ID 或第一个家庭）。
 * 不做任何权限校验。
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

/**
 * 菜价 = Σ(配料单价 × 用量)，四舍五入到分。
 * 与旧后端 DishService.computeDishPrice() 和后台前端 estimatedPrice 完全一致。
 */
async function computePrice(dishId, conn = null) {
  const run = conn
    ? async (sql, a = []) => (await conn.execute(sql, a))[0]
    : async (sql, a = []) => q(sql, a);
  const rows = await run(
    `SELECT i.price, di.amount
       FROM t_dish_ingredient di
       JOIN t_ingredient i ON i.id = di.ing_id
      WHERE di.dish_id = ?`,
    [dishId]
  );
  let sum = 0;
  for (const r of rows) {
    sum += (Number(r.price) || 0) * (Number(r.amount) || 0);
  }
  return Math.round((sum + Number.EPSILON) * 100) / 100;
}

/** 菜品列表（后台"菜单管理"页） */
router.get('/dishes/manage/all', async (req, res) => {
  const scope = await familyScope(req);
  const where = [];
  const args = [];
  if (scope != null) {
    where.push('family_id = ?');
    args.push(scope);
  }
  const rows = await q(
    `SELECT * FROM t_dish ${where.length ? 'WHERE ' + where.join(' AND ') : ''}
      ORDER BY category_id ASC, id ASC`,
    args
  );
  return ok(res, rows.map((d) => ({
    id: Number(d.id),
    familyId: Number(d.family_id),
    name: d.name,
    categoryId: d.category_id == null ? null : Number(d.category_id),
    imageEmoji: d.image_emoji,
    description: d.description,
    spiceLevel: d.spice_level == null ? 0 : Number(d.spice_level),
    price: Number(d.price),
    status: d.status == null ? 1 : Number(d.status),
    createdAt: d.created_at,
    updatedAt: d.updated_at,
  })));
});

/** 兼容旧前端的 GET /dishes（未被使用，保留以免 404） */
router.get('/dishes', async (req, res) => {
  const scope = await familyScope(req);
  const rows = await q(
    `SELECT * FROM t_dish ${scope != null ? 'WHERE family_id = ?' : ''} ORDER BY id ASC`,
    scope != null ? [scope] : []
  );
  return ok(res, rows.map((d) => ({
    id: Number(d.id),
    familyId: Number(d.family_id),
    name: d.name,
    categoryId: d.category_id == null ? null : Number(d.category_id),
    imageEmoji: d.image_emoji,
    description: d.description,
    spiceLevel: d.spice_level == null ? 0 : Number(d.spice_level),
    price: Number(d.price),
    status: d.status == null ? 1 : Number(d.status),
  })));
});

/** 菜品详情（含配方） */
router.get('/dishes/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const d = await one('SELECT * FROM t_dish WHERE id = ?', [id]);
    if (!d) throw httpError(404, '菜品不存在');
    const ings = await q(
      `SELECT di.ing_id, di.amount, di.unit,
              i.name, i.emoji, i.unit AS ing_unit, i.price
         FROM t_dish_ingredient di
         LEFT JOIN t_ingredient i ON i.id = di.ing_id
        WHERE di.dish_id = ?
        ORDER BY di.id ASC`,
      [id]
    );
    return ok(res, {
      id: Number(d.id),
      familyId: Number(d.family_id),
      name: d.name,
      categoryId: d.category_id == null ? null : Number(d.category_id),
      imageEmoji: d.image_emoji,
      description: d.description,
      spiceLevel: d.spice_level == null ? 0 : Number(d.spice_level),
      price: Number(d.price),
      status: d.status == null ? 1 : Number(d.status),
      ingredients: ings.map((i) => ({
        ingId: Number(i.ing_id),
        name: i.name,
        emoji: i.emoji,
        unit: i.unit || i.ing_unit,
        price: Number(i.price),
        amount: Number(i.amount),
      })),
    });
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 保存配方（全量替换） */
async function replaceIngredients(conn, dishId, familyId, ingredients) {
  await conn.execute('DELETE FROM t_dish_ingredient WHERE dish_id = ?', [dishId]);
  if (!Array.isArray(ingredients)) return;
  const seen = new Set();
  for (const ing of ingredients) {
    const ingId = Number(ing && ing.ingId);
    if (!ingId) continue;
    // t_dish_ingredient 有 uk_dish_ing 唯一键：同一配料只保留第一次出现的
    if (seen.has(ingId)) continue;
    seen.add(ingId);
    const amount = Number(ing.amount);
    // 配料可能已被删除：跳过不存在的 ingId，避免脏数据
    const [[exists]] = await conn.execute('SELECT id, unit FROM t_ingredient WHERE id = ?', [ingId]);
    if (!exists) continue;
    await conn.execute(
      'INSERT INTO t_dish_ingredient (dish_id, ing_id, amount, unit, family_id) VALUES (?, ?, ?, ?, ?)',
      [dishId, ingId, Number.isFinite(amount) ? amount : 100, ing.unit || exists.unit || '', familyId]
    );
  }
}

async function resolveFamilyId(req) {
  const scope = await familyScope(req);
  if (scope != null) return scope;
  throw httpError(400, '还没有任何家庭，请先创建家庭');
}

/** 新建菜品 */
router.post('/dishes', async (req, res) => {
  try {
    const b = req.body || {};
    const name = String(b.name == null ? '' : b.name).trim();
    if (!name) throw httpError(400, '请输入菜名');
    if (name.length > 64) throw httpError(400, '菜名不能超过 64 字符');
    const familyId = await resolveFamilyId(req);
    const categoryId = b.categoryId == null || b.categoryId === '' ? 0 : Number(b.categoryId);

    const dishId = await tx(async (conn) => {
      const [r] = await conn.execute(
        `INSERT INTO t_dish (name, category_id, image_emoji, description, spice_level, price, status, family_id, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, 0, 1, ?, NOW(), NOW())`,
        [
          name,
          categoryId,
          b.imageEmoji == null ? '' : String(b.imageEmoji),
          b.description == null ? null : String(b.description),
          b.spiceLevel == null ? 0 : Number(b.spiceLevel),
          familyId,
        ]
      );
      const id = r.insertId;
      await replaceIngredients(conn, id, familyId, b.ingredients);
      const price = await computePrice(id, conn);
      await conn.execute('UPDATE t_dish SET price = ? WHERE id = ?', [price, id]);
      return id;
    });
    return ok(res, { id: dishId });
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 更新菜品 */
router.put('/dishes/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const b = req.body || {};
    const d = await one('SELECT * FROM t_dish WHERE id = ?', [id]);
    if (!d) throw httpError(404, '菜品不存在');
    const name = String(b.name == null ? d.name : b.name).trim();
    if (!name) throw httpError(400, '请输入菜名');
    const familyId = Number(d.family_id);

    await tx(async (conn) => {
      await conn.execute(
        `UPDATE t_dish SET name = ?, category_id = ?, image_emoji = ?, description = ?, spice_level = ?, updated_at = NOW()
          WHERE id = ?`,
        [
          name,
          b.categoryId == null || b.categoryId === '' ? d.category_id : Number(b.categoryId),
          b.imageEmoji == null ? d.image_emoji : String(b.imageEmoji),
          b.description === undefined ? d.description : (b.description == null ? null : String(b.description)),
          b.spiceLevel == null ? d.spice_level : Number(b.spiceLevel),
          id,
        ]
      );
      if (b.ingredients !== undefined) {
        await replaceIngredients(conn, id, familyId, b.ingredients);
      }
      const price = await computePrice(id, conn);
      await conn.execute('UPDATE t_dish SET price = ? WHERE id = ?', [price, id]);
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 上下架切换（无 body：后端自己翻转） */
router.put('/dishes/:id/status', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const d = await one('SELECT id, status FROM t_dish WHERE id = ?', [id]);
    if (!d) throw httpError(404, '菜品不存在');
    const next = Number(d.status) === 1 ? 0 : 1;
    await q('UPDATE t_dish SET status = ?, updated_at = NOW() WHERE id = ?', [next, id]);
    return ok(res, { status: next });
  } catch (e) {
    return failFrom(res, e);
  }
});

/** 删除菜品（连带配方） */
router.delete('/dishes/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const d = await one('SELECT id FROM t_dish WHERE id = ?', [id]);
    if (!d) throw httpError(404, '菜品不存在');
    await tx(async (conn) => {
      await conn.execute('DELETE FROM t_dish_ingredient WHERE dish_id = ?', [id]);
      await conn.execute('DELETE FROM t_dish_blacklist WHERE dish_id = ?', [id]);
      await conn.execute('DELETE FROM t_dish WHERE id = ?', [id]);
    });
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

module.exports = router;
