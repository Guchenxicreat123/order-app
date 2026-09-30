/**
 * secureStore.js — 加密敏感数据 + JWT 过期校验
 *
 * 用 Web Crypto AES-GCM 加密后再写入 localStorage。
 * 即使 XSS 拿到 localStorage 原文，没有动态生成的密钥也无法解密。
 *
 * 密钥派生：
 *   PBKDF2(SHA-256, salt=origin+envSecret, iter=100000) -> 256-bit AES key
 * salt 嵌入到代码中（增加静态提取难度），但仍可被逆向。
 * 真正的防御：后端尽量改 httpOnly cookie；前端 AES 只是为「非键盘记录 XSS」拉高门槛。
 *
 * 存值结构：base64( 12字节IV || ciphertext )
 * 过期策略：JWT 类型条目会先解析 payload.exp，过期则视作无效并清理。
 */

const ENV_SECRET = (import.meta.env.VITE_STORAGE_SALT || 'order-admin-default-salt-change-me').toString()
const APP_ORIGIN = (typeof window !== 'undefined' && window.location?.origin) || 'unknown'

// ---------------- 工具函数 ----------------
function b64encode(bytes) {
  let s = ''
  for (const b of bytes) s += String.fromCharCode(b)
  return btoa(s)
}

function b64decode(s) {
  const bin = atob(s)
  const out = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
  return out
}

function getSubtle() {
  return (typeof crypto !== 'undefined' && crypto.subtle) ? crypto.subtle : null
}

function deriveKey(salt) {
  const subtle = getSubtle()
  if (!subtle) return null
  const enc = new TextEncoder()
  // 16字节 salt：origin 字节 + env 字节 + 0 填充
  const saltBytes = new Uint8Array(16)
  const ob = enc.encode(APP_ORIGIN)
  const eb = enc.encode(ENV_SECRET)
  for (let i = 0; i < Math.min(ob.length, 16); i++) saltBytes[i] = ob[i]
  for (let i = 0; i < Math.min(eb.length, 16 - 8); i++) saltBytes[8 + i] = eb[i]
  return subtle.importKey(
    'raw', enc.encode(ENV_SECRET + ':' + APP_ORIGIN),
    { name: 'PBKDF2' }, false, ['deriveKey']
  ).then(baseKey =>
    subtle.deriveKey(
      { name: 'PBKDF2', salt: saltBytes, iterations: 100000, hash: 'SHA-256' },
      baseKey,
      { name: 'AES-GCM', length: 256 },
      false, ['encrypt', 'decrypt']
    )
  )
}

// 单例 key 缓存
let _keyPromise = null
function getKey() {
  if (!_keyPromise) _keyPromise = deriveKey()
  return _keyPromise
}

// ---------------- 加密 / 解密 ----------------
async function encrypt(plain) {
  const subtle = getSubtle()
  if (!subtle) return plain  // 降级：原样返回（极端环境）
  const key = await getKey()
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const ctBuf = await subtle.encrypt(
    { name: 'AES-GCM', iv },
    key, new TextEncoder().encode(plain)
  )
  const ct = new Uint8Array(ctBuf)
  const out = new Uint8Array(iv.length + ct.length)
  out.set(iv, 0); out.set(ct, iv.length)
  return 'v1:' + b64encode(out)
}

async function decrypt(payload) {
  const subtle = getSubtle()
  if (!subtle || !payload || !payload.startsWith('v1:')) return null
  const raw = b64decode(payload.slice(3))
  const iv = raw.slice(0, 12)
  const ct = raw.slice(12)
  try {
    const key = await getKey()
    const ptBuf = await subtle.decrypt({ name: 'AES-GCM', iv }, key, ct)
    return new TextDecoder().decode(ptBuf)
  } catch (e) {
    return null  // 密钥变了 / 数据被改过
  }
}

// ---------------- JWT 过期判断 ----------------
function jwtExp(token) {
  if (!token || typeof token !== 'string') return null
  const parts = token.split('.')
  if (parts.length !== 3) return null
  try {
    const json = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof json.exp === 'number' ? json.exp * 1000 : null
  } catch (e) { return null }
}

function isExpired(token) {
  const exp = jwtExp(token)
  if (exp == null) return false  // 无 exp 字段：放行
  return Date.now() >= exp
}

// ---------------- 主 API ----------------
export async function setSecureItem(key, value) {
  const enc = await encrypt(String(value))
  try { localStorage.setItem(key, enc) } catch (e) {}
}

export async function getSecureItem(key) {
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    const dec = await decrypt(raw)
    if (dec == null) {
      // 旧的明文数据？试读一次（兼容迁移）
      if (!raw.startsWith('v1:')) return raw
      localStorage.removeItem(key)
      return null
    }
    return dec
  } catch (e) {
    return null
  }
}

export function removeSecureItem(key) {
  try { localStorage.removeItem(key) } catch (e) {}
}

/** 取一个可能过期的 JWT，过期则自动清并返回 null */
export async function getSecureToken(key) {
  const tok = await getSecureItem(key)
  if (!tok) return null
  if (isExpired(tok)) {
    removeSecureItem(key)
    return null
  }
  return tok
}

/** 同步版本（用于拦截器热路径）：JWT 解析不需异步，但加密存储需要解 */
let _tokenCache = new Map()
export async function primeTokenCache(key) {
  const tok = await getSecureToken(key)
  _tokenCache.set(key, tok || '')
  return tok  // 返回实际 token：调用方可用它判断登录态（如刷新后加载家庭列表）
}

export function cachedToken(key) {
  return _tokenCache.get(key) || ''
}

export function clearTokenCache(key) {
  if (key) _tokenCache.delete(key)
  else _tokenCache.clear()
}
