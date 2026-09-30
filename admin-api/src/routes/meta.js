'use strict';
/**
 * 兼容性接口：旧后台前端会调 /me/chef-status 之类的小程序侧接口。
 * 新后台不区分主厨，直接回一个"永远有权限"的结果，避免前端因为拿不到主厨状态而禁用按钮。
 */
const express = require('express');
const { ok, failFrom } = require('../auth');
const { one, q } = require('../db');

const router = express.Router();

router.get('/me/chef-status', async (req, res) => {
  // 取任意一个用户作为展示用的"主厨"，纯粹为了兼容旧字段
  const u = await one('SELECT open_id, nickname FROM t_user ORDER BY created_at LIMIT 1');
  return ok(res, {
    hasChef: true,
    chefId: u ? u.open_id : null,
    chefNickname: req.admin.displayName || req.admin.username,
    isMe: true,
  });
});

/** 数据总览：给后台首页/统计用的轻量计数 */
router.get('/admin/stats/overview', async (req, res) => {
  const [users, families, dishes, orders, ingredients] = await Promise.all([
    q('SELECT COUNT(*) AS c FROM t_user'),
    q('SELECT COUNT(*) AS c FROM t_family'),
    q('SELECT COUNT(*) AS c FROM t_dish'),
    q('SELECT COUNT(*) AS c FROM t_order'),
    q('SELECT COUNT(*) AS c FROM t_ingredient'),
  ]);
  return ok(res, {
    userCount: Number(users[0].c),
    familyCount: Number(families[0].c),
    dishCount: Number(dishes[0].c),
    orderCount: Number(orders[0].c),
    ingredientCount: Number(ingredients[0].c),
  });
});

module.exports = router;
