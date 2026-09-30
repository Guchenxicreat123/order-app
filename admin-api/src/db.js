'use strict';
/**
 * MySQL 连接池。
 * 本服务自己直连 order_app 库，不经过小程序后端（app 容器）。
 */
const mysql = require('mysql2/promise');

const pool = mysql.createPool({
  host: process.env.DB_HOST || 'mysql',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || 'changeme',
  database: process.env.DB_NAME || 'order_app',
  waitForConnections: true,
  connectionLimit: Number(process.env.DB_POOL || 10),
  charset: 'utf8mb4',
  // 让 DATETIME 按字符串返回，避免时区二次转换（前端只做展示）
  dateStrings: true,
  multipleStatements: false,
});

async function q(sql, params = []) {
  const [rows] = await pool.execute(sql, params);
  return rows;
}

/** 取一行（没有则 null） */
async function one(sql, params = []) {
  const rows = await q(sql, params);
  return rows.length ? rows[0] : null;
}

/** 取第一行的第一个字段值 */
async function scalar(sql, params = []) {
  const row = await one(sql, params);
  if (!row) return null;
  return row[Object.keys(row)[0]];
}

/** 事务包装 */
async function tx(fn) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const out = await fn(conn);
    await conn.commit();
    return out;
  } catch (e) {
    try { await conn.rollback(); } catch (_) { /* ignore */ }
    throw e;
  } finally {
    conn.release();
  }
}

module.exports = { pool, q, one, scalar, tx };
