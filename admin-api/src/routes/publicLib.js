'use strict';
/**
 * 公共菜品库 / 公共配菜库（平台级，家庭无关）：直连数据库，无任何权限限制。
 *
 * 对应表：t_public_dish / t_public_ingredient（category_id 指向 t_ingredient_category
 * 里 family_id=0 的那组"公共配菜分类"）。
 */
const express = require('express');
const { ok, fail, httpError, failFrom } = require('../auth');
const { q, one } = require('../db');

const router = express.Router();

/** 公共配菜分类：t_ingredient_category 中 family_id=0 的那组 */
router.get('/public/ingredients/categories', async (req, res) => {
  const rows = await q(
    'SELECT * FROM t_ingredient_category WHERE family_id = 0 ORDER BY sort ASC, id ASC'
  );
  return ok(res, rows.map((c) => ({
    id: Number(c.id),
    familyId: 0,
    name: c.name,
    emoji: c.emoji,
    sort: c.sort == null ? 0 : Number(c.sort),
    createdAt: c.created_at,
  })));
});

// ==================== 公共菜品 ====================

function mapPublicDish(d) {
  return {
    id: Number(d.id),
    name: d.name,
    categoryId: d.category_id == null ? null : Number(d.category_id),
    imageEmoji: d.image_emoji,
    description: d.description,
    spiceLevel: d.spice_level == null ? 0 : Number(d.spice_level),
    status: d.status == null ? 1 : Number(d.status),
    createdAt: d.created_at,
    updatedAt: d.updated_at,
  };
}

router.get('/public/dishes/admin/all', async (req, res) => {
  const rows = await q('SELECT * FROM t_public_dish ORDER BY category_id ASC, id ASC');
  return ok(res, rows.map(mapPublicDish));
});

/** 兼容小程序端读取公共菜品库（只读上架） */
router.get('/public/dishes', async (req, res) => {
  const rows = await q('SELECT * FROM t_public_dish WHERE status = 1 ORDER BY id ASC');
  return ok(res, rows.map(mapPublicDish));
});

router.post('/public/dishes', async (req, res) => {
  try {
    const b = req.body || {};
    const name = String(b.name == null ? '' : b.name).trim();
    if (!name) throw httpError(400, '请输入菜名');
    const r = await q(
      `INSERT INTO t_public_dish (name, category_id, image_emoji, description, spice_level, status, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())`,
      [
        name,
        b.categoryId == null || b.categoryId === '' ? null : Number(b.categoryId),
        b.imageEmoji == null ? null : String(b.imageEmoji),
        b.description == null ? null : String(b.description).slice(0, 255),
        b.spiceLevel == null ? 0 : Number(b.spiceLevel),
        b.status == null ? 1 : Number(b.status),
      ]
    );
    return ok(res, { id: r.insertId });
  } catch (e) {
    return failFrom(res, e);
  }
});

/**
 * 更新公共菜品。
 * 前端会把整行回传（含 id/createdAt 等只读字段），这里只取需要的字段，忽略其余。
 */
router.put('/public/dishes/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const b = req.body || {};
    const d = await one('SELECT * FROM t_public_dish WHERE id = ?', [id]);
    if (!d) throw httpError(404, '菜品不存在');
    const name = String(b.name == null ? d.name : b.name).trim();
    if (!name) throw httpError(400, '请输入菜名');
    await q(
      `UPDATE t_public_dish SET name = ?, category_id = ?, image_emoji = ?, description = ?, spice_level = ?, status = ?, updated_at = NOW()
        WHERE id = ?`,
      [
        name,
        b.categoryId === undefined ? d.category_id : (b.categoryId == null || b.categoryId === '' ? null : Number(b.categoryId)),
        b.imageEmoji === undefined ? d.image_emoji : (b.imageEmoji == null ? null : String(b.imageEmoji)),
        b.description === undefined ? d.description : (b.description == null ? null : String(b.description).slice(0, 255)),
        b.spiceLevel === undefined ? d.spice_level : Number(b.spiceLevel),
        b.status === undefined ? d.status : Number(b.status),
        id,
      ]
    );
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

router.delete('/public/dishes/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const d = await one('SELECT id FROM t_public_dish WHERE id = ?', [id]);
    if (!d) throw httpError(404, '菜品不存在');
    await q('DELETE FROM t_public_dish WHERE id = ?', [id]);
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

// ==================== 公共配菜 ====================

function mapPublicIngredient(i) {
  return {
    id: Number(i.id),
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

router.get('/public/ingredients/admin/all', async (req, res) => {
  const rows = await q('SELECT * FROM t_public_ingredient ORDER BY category_id ASC, id ASC');
  return ok(res, rows.map(mapPublicIngredient));
});

/** 兼容小程序端读取公共配菜库（只读上架） */
router.get('/public/ingredients', async (req, res) => {
  const rows = await q('SELECT * FROM t_public_ingredient WHERE status = 1 ORDER BY id ASC');
  return ok(res, rows.map(mapPublicIngredient));
});

router.post('/public/ingredients', async (req, res) => {
  try {
    const b = req.body || {};
    const r = await q(
      `INSERT INTO t_public_ingredient (name, category_id, unit, price, emoji, status, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())`,
      [
        String(b.name == null ? '' : b.name).trim(),
        b.categoryId == null || b.categoryId === '' ? 0 : Number(b.categoryId),
        b.unit == null ? '' : String(b.unit),
        b.price == null ? 0 : Number(b.price),
        b.emoji == null ? '' : String(b.emoji),
        b.status == null ? 1 : Number(b.status),
      ]
    );
    return ok(res, { id: r.insertId });
  } catch (e) {
    return failFrom(res, e);
  }
});

router.put('/public/ingredients/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const b = req.body || {};
    const i = await one('SELECT * FROM t_public_ingredient WHERE id = ?', [id]);
    if (!i) throw httpError(404, '配菜不存在');
    await q(
      `UPDATE t_public_ingredient SET name = ?, category_id = ?, unit = ?, price = ?, emoji = ?, status = ?, updated_at = NOW()
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

router.delete('/public/ingredients/:id', async (req, res) => {
  try {
    const id = Number(req.params.id);
    const i = await one('SELECT id FROM t_public_ingredient WHERE id = ?', [id]);
    if (!i) throw httpError(404, '配菜不存在');
    await q('DELETE FROM t_public_ingredient WHERE id = ?', [id]);
    return ok(res, null);
  } catch (e) {
    return failFrom(res, e);
  }
});

module.exports = router;
