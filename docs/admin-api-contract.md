# admin-web → Backend API Contract (reverse-engineered from `admin-web/src`)

Derived **only** by reading the current `admin-web` source. Every claim below is backed by a quoted
code fence with `file:line`. Nothing here is inferred from the old Spring Boot backend.

Scope: repo `/vol2/1000/File/aiwork/程序（工具）开发/点餐小程序`, app `admin-web` (Vue 3 SPA + Element Plus).

> **⚠️ Snapshot / moving target.** The migration to the new Node service (`admin-api/`) was **already
> half-applied while this document was being written**. Three frontend files were rewritten mid-read, and
> this document describes their **post-change** state:
>
> | File | mtime of the version documented here | Change during the read |
> |---|---|---|
> | `admin-web/src/api/index.js` | 2026-09-10 21:30 (161 lines) | `chefLogin` rewritten: `POST /chef/login {userId,pin}` → **`POST /admin/login {username,password}`**; `chefLogout` `/auth/logout` → **`/admin/logout`**; new export **`changeAdminPassword`** |
> | `admin-web/src/App.vue` | 2026-09-10 21:31 (671 lines) | added the "修改密码" dialog + `changeAdminPassword` import |
> | `admin-web/.env.development` / `.env.production` | 2026-09-10 21:30 | `VITE_API_URL` changed from an absolute `http://192.168.x.x:8006/api` to **`/api`** (same-origin) |
> | `admin-web/vite.config.js` | 2026-09-10 21:30 (43 lines) | dev proxy target `:8006` → **`process.env.ADMIN_API_URL \|\| 'http://localhost:8008'`** |
>
> **All `views/*.vue`, `components/*.vue`, `router.js`, `utils/secureStore.js` are untouched** (mtimes
> 2026-09-04…09-07) and are the authoritative source for §3's response/body field lists.
> If you re-read `api/index.js`/`App.vue` later, re-verify their line numbers.

> Stale-artifact warning: `admin-web/dist/`, `admin-web-serve/` and `admin-web/src/store/familyStore.js`
> are **old build/dead code** (the bundles still reference `/users`, `/page`, and lack `/orders/today`,
> `/admin/families/overview`). They are **not** authoritative. Only `admin-web/src/**` is.

---

## Table of contents

1. [Transport layer contract (axios instance, interceptors, envelope)](#1-transport-layer-contract)
2. [Auth / storage keys](#2-auth--storage-keys)
3. [API functions — USED by components](#3-api-functions--used-by-components)
   - 3.1 [chefLogin](#31-cheflogin)
   - 3.2 [chefLogout](#32-cheflogout)
   - 3.3 [getAdminFamilies](#33-getadminfamilies)
   - 3.4 [getAdminFamiliesOverview](#34-getadminfamiliesoverview)
   - 3.5 [getAdminUsers](#35-getadminusers)
   - 3.6 [createFamily](#36-createfamily)
   - 3.7 [updateFamily](#37-updatefamily)
   - 3.8 [deleteFamily](#38-deletefamily)
   - 3.9 [adminAddFamily](#39-adminaddfamily)
   - 3.10 [adminRemoveFamily](#310-adminremovefamily)
   - 3.11 [adminSetRole](#311-adminsetrole)
   - 3.12 [adminDeleteUser](#312-admindeleteuser)
   - 3.13 [adminRevokeUser](#313-adminrevokeuser)
   - 3.14 [getTodayOrders](#314-gettodayorders)
   - 3.15 [getOrders](#315-getorders)
   - 3.16 [getOrderDetail](#316-getorderdetail)
   - 3.17 [confirmOrder](#317-confirmorder)
   - 3.18 [confirmOrderItem](#318-confirmorderitem)
   - 3.19 [rejectOrderItem](#319-rejectorderitem)
   - 3.20 [updateOrder](#320-updateorder)
   - 3.21 [deleteOrder](#321-deleteorder)
   - 3.22 [getCategories](#322-getcategories)
   - 3.23 [getDishesManageAll](#323-getdishesmanageall)
   - 3.24 [getDishDetail](#324-getdishdetail)
   - 3.25 [createDish](#325-createdish)
   - 3.26 [updateDish](#326-updatedish)
   - 3.27 [toggleDishStatus](#327-toggledishstatus)
   - 3.28 [getIngredientCategories](#328-getingredientcategories)
   - 3.29 [createIngredientCategory](#329-createingredientcategory)
   - 3.30 [updateIngredientCategory](#330-updateingredientcategory)
   - 3.31 [deleteIngredientCategory](#331-deleteingredientcategory)
   - 3.32 [getIngredientsPublic](#332-getingredientspublic)
   - 3.33 [createIngredient](#333-createingredient)
   - 3.34 [updateIngredient](#334-updateingredient)
   - 3.35 [deleteIngredient](#335-deleteingredient-api-indexjs-export-name)
   - 3.36 [getPublicDishes](#336-getpublicdishes)
   - 3.37 [createPublicDish](#337-createpublicdish)
   - 3.38 [updatePublicDish](#338-updatepublicdish)
   - 3.39 [deletePublicDish](#339-deletepublicdish)
   - 3.40 [getPublicIngredients](#340-getpublicingredients)
   - 3.41 [getPublicIngredientCategories](#341-getpublicingredientcategories)
   - 3.42 [createPublicIngredient](#342-createpublicingredient)
   - 3.43 [updatePublicIngredient](#343-updatepublicingredient)
   - 3.44 [deletePublicIngredient](#344-deletepublicingredient)
   - 3.45 [changeAdminPassword](#345-changeadminpassword) *(auth; grouped here to avoid renumbering)*
4. [Exported-but-UNUSED functions (still must exist if you keep the module)](#4-exported-but-unused-functions)
5. [Login flow end-to-end (Login.vue)](#5-login-flow-end-to-end-loginvue)
6. [USED / UNUSED audit (whole `admin-web/src`)](#6-used--unused-audit)
7. [Router routes → view mapping; FamilyUsers.vue role](#7-router-routes--view-mapping)
8. [`X-Family-Id` header and `admin_family_id`](#8-x-family-id-header-and-admin_family_id)
9. [Dependencies on the OLD backend's permission behaviour](#9-dependencies-on-old-backend-permission-behaviour)
10. [Cross-cutting gotchas for the new Node service](#10-cross-cutting-gotchas-for-the-new-node-service)

---

## 1. Transport layer contract

Whole file: `admin-web/src/api/index.js`.

### 1.1 Instance config

```js
// admin-web/src/api/index.js:10-16
const API_BASE = import.meta.env.VITE_API_URL || 'http://192.168.x.x:8006/api'

const service = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
  withCredentials: false  // 当前端到端使用 Bearer token 鉴权，不走 cookie
})
```

- `VITE_API_URL` is now **`/api` (same-origin relative)** in both env files:
  ```ini
  # admin-web/.env.development:1-3
  # ============ 开发环境 ============
  # 后端 API 地址（同源，由 vite dev server 代理到独立管理服务）
  VITE_API_URL=/api
  ```
  ```ini
  # admin-web/.env.production:1-5
  # ============ 生产环境 ============
  # 后端 API 地址：改为同源相对路径。
  # 由 admin-web 容器内的 server.js 把 /api 反代到独立管理服务 order-admin-api:8008。
  # 好处：不写死内网 IP、不受网段影响、没有跨域问题、https 下也正常。
  VITE_API_URL=/api
  ```
  → the `|| 'http://192.168.x.x:8006/api'` fallback in `api/index.js:10` is now **dead** unless the env
  var is missing. Every live request goes to **same-origin `/api/...`**.
- Dev: Vite proxies `/api` to the new service; prod: the `admin-web` container's `server.js` reverse-proxies:
  ```js
  // admin-web/vite.config.js:31-37
  proxy: {
    // 开发时把 /api 打到独立的后台管理服务（admin-api），与小程序后端无关
    '/api': {
      target: process.env.ADMIN_API_URL || 'http://localhost:8008',
      changeOrigin: true
    }
  }
  ```
  ```js
  // admin-web/server.js:61-68  (production proxy — zero-dependency, node:http)
  function proxy(req, res) {
    const options = {
      host: API_TARGET_HOST,          // ADMIN_API_HOST || 'admin-api'
      port: API_TARGET_PORT,          // ADMIN_API_PORT || 8008
      method: req.method,
      path: req.url,                  // ← full path INCLUDING /api, unchanged
      headers: { ...req.headers, host: `${API_TARGET_HOST}:${API_TARGET_PORT}` },
    };
  ```
  ```js
  // admin-web/server.js:123-124
  const pathname = (req.url || '/').split('?')[0];
  if (pathname === '/api' || pathname.startsWith('/api/')) return proxy(req, res);
  ```
  ⚠️ **Neither proxy strips the `/api` prefix** — `path: req.url` forwards `/api/orders` verbatim to
  `admin-api:8008`. So the Node service **must mount every route under `/api`** (`/api/orders`,
  `/api/admin/login`, …), not at the root. `admin-api` does exactly this
  (`admin-api/src/server.js:47-71`: `app.post('/api/admin/login', …)`, `app.use('/api', require('./routes/orders'))`).
  Requests that are not `/api/*` are served from `dist/` with an SPA fallback (`server.js:125-129`), and
  `/health` is answered by the web container itself (`server.js:125-128`).
- `timeout: 10000` → **any endpoint taking >10 s rejects** with an axios timeout error (no `err.response`), which the error interceptor turns into `ElMessage.error('网络异常')`.
- `withCredentials: false` → **no cookies**; all auth is the `Authorization` header.

### 1.2 Request interceptor (headers)

```js
// admin-web/src/api/index.js:19-31
primeTokenCache('chef_token')

service.interceptors.request.use(config => {
  const token = cachedToken('chef_token')
  if (token) config.headers.Authorization = `Bearer ${token}`

  const famId = cachedToken('admin_family_id')
  if (famId) config.headers['X-Family-Id'] = famId

  return config
})
```

- `Authorization: Bearer <chef_token>` — omitted entirely when the cache is empty.
- `X-Family-Id: <admin_family_id>` — omitted entirely when the cache is empty (see §8; **this happens more often than you'd expect**).
- No `Content-Type` override → axios default `application/json` for bodies.

### 1.3 Response envelope (**hard requirement**)

```js
// admin-web/src/api/index.js:33-46
service.interceptors.response.use(
  res => res.data?.code === 0 ? res.data.data : Promise.reject(res.data),
  err => {
    if (err.response?.status === 401 || err.response?.data?.code === 401) {
      removeSecureItem('chef_token')
      clearTokenCache('chef_token')
      if (location.pathname !== '/login') {
        location.href = '/login'
      }
    }
    ElMessage.error(err.response?.data?.message || '网络异常')
    return Promise.reject(err)
  }
)
```

Consequences for the new service:

1. **Every 2xx response body must be `{ "code": 0, "data": <payload> }`.** Anything else (raw payload, `code` as a string, `data` missing) causes `res.data?.code === 0` to be false → the promise **rejects with the raw body**, and no component expects that.
2. **Success unwrapping is total**: callers receive `res.data.data` directly, never the envelope. So `getAdminFamilies()` must resolve to a **bare JSON array** (see §3.3).
3. **HTTP status drives UX**: the `err` branch runs only for transport/HTTP failures, and it always shows `ElMessage.error(err.response?.data?.message || '网络异常')`. So business failures the user must *see* should use a non-2xx status (401/403/400/500) with `{ "message": "..." }`.
4. **A rejection thrown by the success handler skips the error handler** (axios interceptor semantics): a `200 + code!=0` response is **silent** — no toast, no redirect. Most components swallow it (`catch (e) {}`).
5. **401 handling**: clears `chef_token` + cache and does `location.href = '/login'` (hard reload, full page). Triggered by HTTP 401 or by `body.code === 401` on a non-2xx response.
6. `location.href = '/login'` assumes the SPA is served at origin root (`base: '/'`, `admin-web/vite.config.js:13`).

---

## 2. Auth / storage keys

`admin-web/src/utils/secureStore.js` AES-GCM-encrypts values before `localStorage.setItem`
(`secureStore.js:116-119`), keyed by `origin + VITE_STORAGE_SALT` (`secureStore.js:16-58`). Values are
stored as `v1:<base64(iv||ciphertext)>`. Reading is async (`getSecureItem`) plus a sync in-memory cache
(`cachedToken`) used by the axios interceptor (`secureStore.js:154-167`).

Keys actually used anywhere in `admin-web/src`:

| Key | Written by | Read by | Meaning |
|---|---|---|---|
| `chef_token` | `chefLogin` (`api/index.js:55`), removed by `chefLogout` (`:78`) and the 401 handler (`:37`) | request interceptor (`api/index.js:23`), `router.js:23-25`, `App.vue:343` | Bearer JWT. **Must be a 3-part JWT with a numeric `exp`** — `secureStore.js:99-113` parses `parts[1]` as base64url JSON and, if `exp` is present and in the past, **deletes the token** and treats the user as logged out. A non-JWT string is tolerated (`jwtExp` returns `null` → not expired). |
| `chef_nickname` | `chefLogin` (`api/index.js:58`, from `res.displayName \|\| res.username`) | `App.vue:344-346` → sidebar name | Plain string. |
| `admin_family_id` | `App.vue:286`, `App.vue:300`, `FamilySelect.vue:59` | `api/index.js:27` (→ `X-Family-Id`), `App.vue:345`, `Orders.vue:244`, `FamilySelect.vue:47`, `store/familyStore.js:21` | **Stored as `String(familyId)`**, then `Number()`-ed back on read. |

Nothing else is persisted. `window.__adminFamilies` is an in-memory-only handoff (`App.vue:281`) that
**no component reads** (grep: single occurrence).

---

## 3. API functions — USED by components

Legend: **UI source** = which control feeds the value. All bodies are JSON objects unless stated.

### 3.1 `chefLogin`

- **Definition**: `api/index.js:48-65` (rewritten for the new service)
  ```js
  // admin-web/src/api/index.js:48-65
  // ============ 登录 ============
  // 只校验管理员账号密码（后端 t_admin_user 表），不再有主厨/角色/家庭等限制。
  // 登录成功后自动写入加密存储 + 内存缓存
  export const chefLogin = async (username, password) => {
    const res = await service.post('/admin/login', { username, password })
    // res 已是 data 部分：{ token, username, displayName }
    if (res && res.token) {
      await setSecureItem('chef_token', res.token)
      primeTokenCache('chef_token')
      const shown = res.displayName || res.username
      if (shown) await setSecureItem('chef_nickname', shown)
      // 通知 App.vue：登录已完成，重新加载家庭列表（onMounted 在登录前已跑过且无 token）
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('families-changed'))
      }
    }
    return res
  }
  ```
- **Method + path**: `POST /admin/login` (effective URL `/api/admin/login`).
  The new `admin-api` service also accepts the legacy `POST /api/chef/login` for old bundles.
- **Body**: `{ username: <string>, password: <string> }`
  - `username` ← `form.username` (`Login.vue:18`, passed as 1st arg `Login.vue:70`) — UI label "管理员账号"
  - `password` ← `form.password` (`Login.vue:27`, passed as 2nd arg `Login.vue:70`) — UI label "管理员密码"
  - **Historical note**: the pre-migration version sent `{ userId, pin }` to `POST /chef/login`; if you
    still have old built bundles in the wild, the backend must keep tolerating that shape too.
- **Query params**: none.
- **Response consumption**:
  - `res.token` — required, else the token is never stored (login appears to succeed but every later request is unauthenticated). Must be a JWT with `exp` (see §2 `chef_token`).
  - `res.displayName` — optional; preferred for the sidebar name.
  - `res.username` — fallback for the sidebar name (`res.displayName || res.username`).
  - Type: `token` **string** (JWT), `displayName`/`username` **string**.
- **Error handling**: `Login.vue:72-74` swallows the rejection (`catch (e) { /* 错误已由 axios 拦截器 ElMessage.error 提示 */ }`). So the **only** way a user sees "wrong password" is the interceptor → the backend must answer a failed login with a **non-2xx** status and `{"message": "..."}`. A `200`+`{"code":1001}` produces a silently dead button.
  (`admin-api` does this correctly: `fail(res, e.status || 500, …)` at `admin-api/src/server.js:44`.)

### 3.2 `chefLogout`

- **Definition**: `api/index.js:72-82`
  ```js
  // admin-web/src/api/index.js:72-82
  /**
   * 退出登录：清掉本地加密存储与内存缓存。
   * （新管理服务用无状态 JWT，服务端没有 token_version 需要递增）
   */
  export const chefLogout = async () => {
    try { await service.post('/admin/logout') } catch (e) { /* 即使失败也继续清前端 */ }
    removeSecureItem('chef_token')
    removeSecureItem('chef_nickname')
    removeSecureItem('admin_family_id')
    clearTokenCache()
  }
  ```
- **Method + path**: `POST /admin/logout` (effective `/api/admin/logout`). Was `POST /auth/logout` before the migration.
- **Response**: not read at all — any 2xx body (still must be `{code:0,...}` or it will reject into the swallowed catch) is fine.
- **Error handling**: fully swallowed; local state is cleared regardless.
- **Caller**: `App.vue:306-309` then `router.push('/login')`.

### 3.3 `getAdminFamilies`

- **Definition**: `api/index.js:151` → `GET /admin/families`
- **Body/params**: none.
- **Callers**:
  - `App.vue:279` `const list = await getAdminFamilies() || []` → sidebar family switcher.
  - `components/FamilySelect.vue:41` → **component itself is dead code** (see §6 note).
- **Response consumption** — must be a **bare array**, each item an object:

  | Field | Read at | Type / use |
  |---|---|---|
  | `familyId` | `App.vue:61,92,98,99,234,239,262,268,261`; `FamilySelect.vue:13,15,50,51` | id; compared via `String(f.familyId) === String(currentFamily)` and via `Number()` equality (`FamilySelect.vue:50`). Number or numeric string both survive. |
  | `name` | `App.vue:63,96,235`; `FamilySelect.vue:14` | string, rendered raw. |
  | `code` | `App.vue:97` | string, rendered in `<code>` in the picker. **Optional** (picker only). |
  | `memberCount` | `App.vue:63,98,240`; `FamilySelect.vue:14` | number; `f.memberCount || 0` → falsy-safe. |

- **Non-null requirements**: `App.vue:279-290` does `list.length > 0` and `list.some(...)` → **`list` must be an Array** (not `{items:[...]}`). `App.vue:233-241` `families.value.find(...)` on a non-array throws.
- **Error handling**: `App.vue:292-294` `catch (e) { families.value = [] }` + interceptor toast. On failure the whole family switcher silently disappears from the sidebar/mobile header.

### 3.4 `getAdminFamiliesOverview`

- **Definition**: `api/index.js:152` → `GET /admin/families/overview`
- **Body/params**: none.
- **Callers**: `Families.vue:250`; `Orders.vue:245`.
- **Response consumption** — bare array:

  | Field | Read at | Type / use |
  |---|---|---|
  | `familyId` | `Families.vue:29,79,132,192,366,388,392,411,414`; `FamilyUsers.vue:97,214,259` (via prop) | id; `row-key`, `===` compare `Families.vue:414`, `String()` compare `Orders.vue:246`. |
  | `name` | `Families.vue:36,84,129,171,232,356`; `FamilyUsers.vue:6,85,123,144,287` | string. |
  | `code` | `Families.vue:41,85,233`; `FamilyUsers.vue:8,85` | string, rendered raw. |
  | `ownerName` | `Families.vue:46,89`; `FamilyUsers.vue:10` | string **or null/undefined** (`v-if` + `|| '-'`). |
  | `chefName` | `Families.vue:52,91`; `FamilyUsers.vue:11` | string or null (`v-if` + `|| '-'`) — no chef ⇒ `null`. |
  | `memberCount` | `Families.vue:56,95,192`; `FamilyUsers.vue:9` | number. Rendered raw at `Families.vue:95` (`{{ row.memberCount }} 人`) → **must not be null** there (no `|| 0`), unlike `App.vue:98`. |
  | `createdAt` | `Families.vue:59` → `fmtDate()` | must be parseable by `new Date(s)`; `Families.vue:239-244` renders `-` for falsy/`NaN`. ISO-8601 or `YYYY-MM-DD HH:mm:ss` expected. |
  | `members` | `Families.vue:235` `(f.members \|\| [])`, `:268`; `FamilyUsers.vue:227` `props.family.members \|\| []` | **array of member objects** — see below. |

- **Nested `members[]`** (consumed by `FamilyUsers.vue` and `Families.vue` orphan detection):

  | Field | Read at | Type / use |
  |---|---|---|
  | `userId` | `Families.vue:269`; `FamilyUsers.vue:19,120,144,155,229,274,291,299,305` | **user identifier — quoted as an openid-like long string** (`FamilyUsers.vue:349-350` comment: "openid 是长串字符"). See the type caveat in §10. |
  | `nickname` | `Families.vue:235`; `FamilyUsers.vue:26,27,64,120,144,155,204` | string; `row.nickname[0]` is indexed (`FamilyUsers.vue:26`) → must be a string or null (null is guarded in template, but `filteredMembers` uses `(m.nickname \|\| '')`). |
  | `role` | `FamilyUsers.vue:32,33,43,47,66,67,73,74,211,227-229` | **uppercase enum string**: `'OWNER'` \| `'CHEF'` \| `'MEMBER'`. |
  | `joinedAt` | `FamilyUsers.vue:38,70` → `fmtDate()` | date-parseable string; falsy ⇒ `-`. |

- **Non-null requirements**: `Families.vue:249-254` runs both calls in `Promise.all`; a **bare array** is required (`fams || []`, then `families.value.find`/`filter`). `FamilyUsers.vue:227` spreads `props.family.members` → must be an array (guarded with `|| []`).
- **Error handling**: `Families.vue:255-257` sets both lists to `[]`; the interceptor toasts.
- **`Orders.vue` extra usage** (`loadFamilyMembers`, `Orders.vue:241-255`):
  ```js
  const currentFamilyId = await getSecureItem('admin_family_id')
  const fams = await getAdminFamiliesOverview()
  const fam = fams.find(f => String(f.familyId) === String(currentFamilyId))
  if (fam && fam.members) {
    familyMembers.value = fam.members.map(m => ({
      userId: m.userId,
      nickname: m.nickname || `用户#${m.userId}`,
      role: m.role
    }))
  }
  ```
  → `fams` must be an array (`find`), `admin_family_id` must match a `familyId` by string comparison, and the "下单者" dropdown (`Orders.vue:69-81`) is populated **only** from `members[]`. Empty result = empty picker = order edit impossible.
  `role` is mapped but never rendered in `Orders.vue`. Errors are swallowed (`Orders.vue:254`).

### 3.5 `getAdminUsers`

- **Definition**: `api/index.js:150` → `GET /admin/users`
- **Body/params**: none.
- **Callers**: `Families.vue:251` (`getAdminUsers().catch(() => [])`), `Families.vue:278`; `FamilyUsers.vue:243`.
- **Response consumption** — bare array:

  | Field | Read at | Type / use |
  |---|---|---|
  | `userId` | `Families.vue:113,116,273,296,309,316,318,328,335`; `FamilyUsers.vue:95,98,99` | id; `<el-option :value="u.userId">` then posted back verbatim as the path param of `adminAddFamily`. |
  | `nickname` | `Families.vue:114,154,186,309,328`; `FamilyUsers.vue:98,120,155` | string or null (`\|\| '(未命名)'`). |
  | `families` | `FamilyUsers.vue:97,214` | array of `{ familyId, ... }`; used as `u.families.some(f => f.familyId === family.familyId)` → **strict `===` on `familyId`**, so `familyId` must have the **same JS type** in `/admin/users[].families[].familyId` and in `/admin/families/overview[].familyId`. Optional (`u.families &&` guard) but needed for disabling already-member options. |

- **Cross-endpoint type invariant**: `Families.vue:264-274` builds `inFamily = Set(m.userId)` from `families[].members[].userId` and filters `allUsers` with `!inFamily.has(u.userId)`. **`Set.has` is strict**, so `members[].userId` and `users[].userId` must be the *same JS type and value format* or every user shows up as an "orphan" (`Families.vue:106-121`).
- **Error handling**: `Families.vue:251` per-call `.catch(() => [])`; `FamilyUsers.vue:244-246` try/catch → `[]`.

### 3.6 `createFamily`

- **Definition**: `api/index.js:153` → `POST /admin/families` (body passthrough)
- **Body** — built at `Families.vue:369-371`:
  ```js
  const payload = { name: formName.value.trim() }
  if (formOwnerId.value) payload.ownerUserId = formOwnerId.value
  await createFamily(payload)
  ```
  - `name`: **string**, trimmed, from the "家庭名称" input (`Families.vue:142`, validated non-empty at `:362`).
  - `ownerUserId`: id of the "创建者" select (`Families.vue:145-158`), **omitted entirely when the select is cleared** (placeholder says "留空则默认使用您（admin）"). Type = whatever `<el-option :value="u.userId">` holds (id).
- **Response**: **not read**. Only `ElMessage.success('家庭已创建')` + `loadData()` + `window.dispatchEvent(new CustomEvent('families-changed'))` (`Families.vue:372-376`) — the event makes `App.vue:351` reload the family list, so the created family must appear in `GET /admin/families` immediately.
- **Error handling**: `catch (e) {}` (`Families.vue:377`) — interceptor toast only.

### 3.7 `updateFamily`

- **Definition**: `api/index.js:154` → `PUT /admin/families/{familyId}`
- **Path param**: `familyId` ← `editingFamily.value.familyId` (`Families.vue:366`).
- **Body**: `{ name: <string> }` — `Families.vue:366` `{ name: formName.value.trim() }`. **Nothing else is sent** (no owner, no code).
- **Response**: not read. Then `loadData()` + `families-changed` (`Families.vue:374-376`).
- **Error handling**: `catch (e) {}`.

### 3.8 `deleteFamily`

- **Definition**: `api/index.js:155` → `DELETE /admin/families/{familyId}`
- **Path param**: `deletingFamily.value.familyId` (`Families.vue:388`).
- **Body**: none.
- **Response**: not read.
- **Confirmation copy defines expected semantics** (`Families.vue:173`):
  > ⚠️ 此操作不可恢复，将同时删除该家庭的所有成员关系、菜品、菜单、购物车、配料等全部数据。
  → a cascade delete is expected.
- **Post-conditions the UI assumes**: `loadData()` + `families-changed`, and if the deleted family was open, `currentFamily` is reset (`Families.vue:392-394`).
- **Error handling**: `catch (e) {}`.

### 3.9 `adminAddFamily`

- **Definition**: `api/index.js:156` → `POST /admin/users/{userId}/add-family`
- **Path param**: `userId`.
- **Body** — two distinct call sites with **different shapes**:

  ```js
  // Families.vue:296  (assign an orphan user)
  await adminAddFamily(assigningUser.value.userId, { familyId: assignFamilyId.value })
  ```
  ```js
  // FamilyUsers.vue:259  (add a user into the family currently open)
  await adminAddFamily(addUserId.value, { familyId: props.family.familyId, role: addRole.value })
  ```
  - `familyId`: from `<el-option :value="f.familyId">` (`Families.vue:188-195`) or `props.family.familyId` (`FamilyUsers.vue:259`).
  - `role`: **`'MEMBER'` | `'CHEF'`, uppercase string** — `addRole` radio group defaults to `'MEMBER'` (`FamilyUsers.vue:105-107,192`). **Omitted** in the `Families.vue` call → backend must default it (presumably `MEMBER`).
- **Response**: not read; `ElMessage.success` + `loadData()`/`reload()` (`Families.vue:297-299`, `FamilyUsers.vue:260-262`).
- **Error handling**: `catch (e) {}` both sites.

### 3.10 `adminRemoveFamily`

- **Definition**: `api/index.js:157` → `POST /admin/users/{userId}/remove-family`
- **Caller**: `FamilyUsers.vue:291` — `await adminRemoveFamily(current.value.userId, { familyId: props.family.familyId })`
- **Body**: `{ familyId }` only.
- **Response**: not read; `reload()` → `emit('changed')` → `Families.vue:410-417 reloadCurrent()` re-fetches the overview.
- **Error handling**: `catch (e) {}` (`FamilyUsers.vue:295`).

### 3.11 `adminSetRole`

- **Definition**: `api/index.js:158` → `POST /admin/users/{userId}/set-role`
- **Caller**: `FamilyUsers.vue:274` — `await adminSetRole(current.value.userId, { familyId: props.family.familyId, role: roleTarget.value })`
- **Body**: `{ familyId, role }`, `role ∈ {'MEMBER','CHEF'}` (`FamilyUsers.vue:126-129`, `roleTarget` default `'MEMBER'`).
- **Expected backend behaviour** (stated in UI copy, `FamilyUsers.vue:131-133`):
  > 提示：设为主厨会把该家庭原主厨降为成员（每家庭仅一位主厨）。
  → promoting to `CHEF` must demote the previous `CHEF` to `MEMBER`.
- **Response**: not read; `reload()`.
- **Error handling**: `catch (e) {}` (`FamilyUsers.vue:278`).

### 3.12 `adminDeleteUser`

- **Definition**: `api/index.js:159` → `DELETE /admin/users/{userId}`
- **Callers**: `Families.vue:316`; `FamilyUsers.vue:305` (`current.value.userId`).
- **Body**: none.
- **Expected semantics** (`FamilyUsers.vue:157`): "将删除该用户在所有家庭的成员关系、购物车、菜单记录" — global deletion + cascade.
- **Response**: not read; `loadData()` / `reload()`.
- **Error handling**: `catch (e) {}` (`Families.vue:319`, `FamilyUsers.vue:309`).

### 3.13 `adminRevokeUser`

- **Definition**: `api/index.js:161` — comment: `/** 强制下线：撤销指定用户全部已签发 token */` → `POST /admin/users/{userId}/revoke`
- **Caller**: `Families.vue:335` (button "踢下线", only rendered on orphan users, `Families.vue:117`).
- **Body**: none.
- **Response**: not read (`ElMessage.success('已踢下线，该用户需重新登录')`, no reload).
- **Error handling**: `catch (e) {}` (`Families.vue:337`).
- **Note**: this revokes **mini-program user** tokens, not admin-web sessions; `chefLogout` (the admin
  session) now hits `/admin/logout` and is purely client-side (§3.2, §9.6).

### 3.14 `getTodayOrders`

- **Definition**: `api/index.js:128` → `GET /orders/today`
- **Body/params**: none.
- **Caller**: `Orders.vue:153`.
- **Response consumption** — bare array of order objects (shared shape, see §3.15 table).

### 3.15 `getOrders`

- **Definition**: `api/index.js:127` → `GET /orders`
- **Body/params**: none.
- **Caller**: `Orders.vue:153`:
  ```js
  const list = mode.value === 'today' ? await getTodayOrders() : await getOrders()
  orders.value = list || []
  ```
  Both endpoints **must return the identical item shape**; the same card/stat components render either.
- **Response item fields** (aggregated from `OrderCard.vue` + `OrderStats.vue`):

  | Field | Read at | Type / use |
  |---|---|---|
  | `orderId` | `Orders.vue:31,33,232,266`; `OrderCard.vue:5,8` | id; `:key`, `openOrder(orderId)`, `updateOrder(id,…)`, `deleteOrder` |
  | `status` | `OrderCard.vue:4,9,57-60,78-80` | **number**: `1`=已确认, `2`=已驳回, `-1`=已撤销, anything else (0)=待确认. Used in CSS class `'status-' + order.status` → must be a scalar number/string, not null. |
  | `userId` | `Orders.vue:231` | fed into the edit dialog's 下单者 select |
  | `userNickname` | `OrderCard.vue:12`; `Orders.vue:65` | string, rendered raw |
  | `createdAt` | `OrderCard.vue:14` → `formatTime(order.createdAt)` | **string** — `OrderCard.vue:63-66` does `ts.length >= 16 ? ts.substring(5, 16) : ts`; a number yields `undefined >= 16`→false and renders the raw number. Expected `"YYYY-MM-DD HH:mm:ss"` (displays `MM-DD HH:mm`). |
  | `itemCount` | `OrderCard.vue:17,20`; `OrderStats.vue:50` | **number** (`order.itemCount` rendered raw at `:17` and summed) |
  | `confirmedCount` | `OrderCard.vue:20`; `OrderStats.vue:51` | number |
  | `rejectedCount` | `OrderCard.vue:23-24`; `OrderStats.vue:52` | number; falsy ⇒ the "已驳回" chip is hidden (`v-if`), so `0`/`null`/missing all acceptable |
  | `totalAmount` | `OrderCard.vue:28`; `OrderStats.vue:54`; `Orders.vue:65,282` | **number or numeric string** — `parseFloat(n \|\| 0).toFixed(2)`; also summed across orders (`OrderStats.vue:53-55`). Decimal (元). |
  | `remark` | `Orders.vue:234` | string or null (`o.remark \|\| ''`) |

- **`OrderStats.vue` arithmetic** (`:50-55`) runs over **every** element: `reduce((s,o) => s + (o.itemCount || 0), 0)` → non-numeric `itemCount` poisons the total (string concat / NaN). All five stat cards (`订单数/总菜数/已确认/已驳回/合计`) derive from these.
- **Non-null requirement**: `orders.value = list || []` then `v-for` + `orders.length` → **bare array required** (`{items:[…]}` breaks the grid silently and prints `undefined`).
- **Error handling**: `catch (e) { orders.value = [] }` (`Orders.vue:155-156`) → empty state "还没有任何订单".

### 3.16 `getOrderDetail`

- **Definition**: `api/index.js:129` → `GET /orders/{id}`
- **Path param**: `orderId` from `OrderCard`'s `@open` (`Orders.vue:34` → `openOrder(orderId)` `:162`).
- **Response consumption** — **single object** (never an array):

  | Field | Read at | Type / use |
  |---|---|---|
  | `orderId` | `Orders.vue:178,270,291`; `OrderDialog.vue:14` | id |
  | `status` | `OrderDialog.vue:15,58` | number (same enum as the list) |
  | `canConfirm` | `Orders.vue:140` | **strict boolean** — `const canOperate = computed(() => detail.value && detail.value.canConfirm === true)`. Must be literal `true` (not `1`, not `"true"`). |
  | `itemCount` | `OrderDialog.vue:25` | number, rendered raw |
  | `totalAmount` | `OrderDialog.vue:30`; `Orders.vue` edit path | number/numeric string |
  | `userNickname` | `OrderDialog.vue:48` | string |
  | `createdAt` | `OrderDialog.vue:49` | **full string rendered raw** (no substring here) → `"YYYY-MM-DD HH:mm:ss"` |
  | `remark` | `OrderDialog.vue:53` | string or null (guarded by `v-if`) |
  | `rejectedCount` | `OrderDialog.vue:147` (`props.detail.rejectedCount ?? …`) | number; **optional** — falls back to counting `items[].status === 2` |
  | `items` | `OrderDialog.vue:35,76,78,144,147` | **array, must be non-null** — `detail.items.length` is called unguarded (`:35`, `:76`) and `.filter` at `:144`. An absent `items` throws inside a computed → render error. |

  **`items[]` fields**:

  | Field | Read at | Type / use |
  |---|---|---|
  | `itemId` | `Orders.vue:187,190,198,202,204`; `OrderDialog.vue:79,105,113` | id; passed to `confirmOrderItem`/`rejectOrderItem` |
  | `dishId` | `OrderDialog.vue:87,91` | **`null`/`0` ⇒ rendered as "自定义"** (`it.dishId ? '已有菜单' : '自定义'` and emoji fallback `it.dishEmoji \|\| (it.dishId ? '🍱' : '🍳')`). Custom dishes must therefore send `dishId: null`. |
  | `dishName` | `OrderDialog.vue:89` | string, required; rendered raw |
  | `dishEmoji` | `OrderDialog.vue:87` | string or null |
  | `spiceLevel` | `OrderDialog.vue:92-93` | number `0/1/2` |
  | `status` | `OrderDialog.vue:82-84,94,144,147` | number, same enum (`1`/`2`/`-1`/`0`); drives CSS classes |
  | `price` | `OrderDialog.vue:99` | number/numeric string → `formatMoney` |
  | `remark` | `OrderDialog.vue:96` | string or null (`v-if`) |

- **Error handling**: `catch (e) { detail.value = null }` (`Orders.vue:168-169`) → dialog opens with `loading=false` and `detail=null`, i.e. **completely blank** (no error panel). `refreshDetail` (`:175-180`) swallows silently, leaving stale data on screen.
- **Post-close**: `onDetailClose` → `loadOrders()` (`Orders.vue:183-185`).

### 3.17 `confirmOrder`

- **Definition**: `api/index.js:130` → `POST /orders/{id}/confirm`
- **Path param**: `detail.value.orderId` (`Orders.vue:218`).
- **Body**: none (no `data` passed to `service.post`).
- **Response**: not read; on resolve → `ElMessage.success('已一键确认')`, `refreshDetail()`, `loadOrders()` (`Orders.vue:219-221`).
- **Gating**: the button only exists when `canOperate` **and** `detail.status === 0` (`OrderDialog.vue:56-63`); otherwise a static `✓ 已全部确认` tag.
- **Error handling**: `catch (e) {}` (`Orders.vue:222`).

### 3.18 `confirmOrderItem`

- **Definition**: `api/index.js:131` → `POST /orders/items/{id}/confirm`
- **Path param**: `itemId` from `OrderDialog`'s `@confirm-item` (`OrderDialog.vue:106` → `onConfirmItem(itemId)` `Orders.vue:187`).
- **Body**: none.
- **Response**: not read; `ElMessage.success('已确认')` + `refreshDetail()` (`Orders.vue:191-192`). The dialog's per-item badge/`confirmedOfDetail` counter (`OrderDialog.vue:143-145`) updates **only** because the detail is refetched → the backend must reflect the new `items[].status` immediately on `GET /orders/{id}`.
- **Gating**: rendered only when `canOperate && it.status !== -1` and `it.status !== 1` (`OrderDialog.vue:100-107`).
- **Error handling**: `catch (e) {}` (`Orders.vue:193`).

### 3.19 `rejectOrderItem`

- **Definition**: `api/index.js:132` → `POST /orders/items/{id}/reject`
- **Path param**: `itemId` (`Orders.vue:204`).
- **Body**: none.
- **Response**: not read; `ElMessage.success('已驳回')` + `refreshDetail()`.
- **Gating**: only when `canOperate && it.status !== -1` and `it.status !== 2` (`OrderDialog.vue:108-115`).
- **Error handling**: `catch (e) {}` after a `ElMessageBox.confirm` whose cancellation is also swallowed (`Orders.vue:199-201,207`).

### 3.20 `updateOrder`

- **Definition**: `api/index.js:133` → `PUT /orders/{id}` (body passthrough)
- **Path param**: `f.orderId` from `editForm` (`Orders.vue:266`), itself seeded from the card's `orderId` (`Orders.vue:230`).
- **Body** — exact literal at `Orders.vue:266`:
  ```js
  await updateOrder(f.orderId, { userId: f.userId, totalAmount: f.totalAmount, remark: f.remark })
  ```
  - `userId`: `editForm.userId`, sourced from `<el-select v-model="editForm.userId">` (`Orders.vue:70`) whose options are `familyMembers` (`Orders.vue:75-80`) → **only members of the currently selected family are allowed**, per UI copy at `:82` ("只能选择当前家庭内的成员"). Initially seeded from `o.userId` (`Orders.vue:231`). Client-side validation only: `if (!f.userId) ElMessage.warning('请选择下单者')` (`Orders.vue:260-263`).
  - `totalAmount`: **number** — `<el-input-number :min="0" :precision="2" :step="1">` (`Orders.vue:85-91`), seeded via `parseFloat(o.totalAmount) || 0` (`:233`). Unit 元.
  - `remark`: string, `''` when empty — `<el-input type="textarea" maxlength="255">` (`Orders.vue:95`), seeded `o.remark || ''` (`:234`). Note the max length 255 is client-side.
  - `orderId` / `userNickname` are carried in `editForm` **only for display** (`Orders.vue:65`) and are **not** sent.
- **Response**: not read; `ElMessage.success('订单已更新')`, `editVisible=false`, `loadOrders()`, and `refreshDetail()` if the same order's dialog is open (`Orders.vue:267-272`).
- **Error handling**: `catch (e) {}` (`Orders.vue:273`).

### 3.21 `deleteOrder`

- **Definition**: `api/index.js:134` → `DELETE /orders/{id}`
- **Path param**: `o.orderId` (`Orders.vue:289`).
- **Body**: none.
- **Expected semantics** (`Orders.vue:282-283` dialog copy): "订单及其所有菜品将一并删除，不可恢复" → cascade over order items.
- **Response**: not read; `ElMessage.success('订单已删除')`, closes the detail dialog if it was showing that order, `loadOrders()` (`Orders.vue:290-294`).
- **Error handling**: `catch (e) {}` (`Orders.vue:295`); the confirm-box cancel is returned early (`:286`).

### 3.22 `getCategories`

- **Definition**: `api/index.js:108` → `GET /categories`
- **Body/params**: none.
- **Callers**: `Dishes.vue:205` (dish categories), `PublicMenu.vue:129`.
- **Response consumption** — bare array of `{ id, name }`:
  - `Dishes.vue:50` `<el-option :key="c.id" :label="c.name" :value="c.id">`; `Dishes.vue:213-215` `dishCats[0].id` seeds `dishForm.categoryId`.
  - `PublicMenu.vue:74` same; `PublicMenu.vue:140-143` `categories.find(x => x.id === id)` → **strict `===`** between `categories[].id` and `dish.categoryId` returned by `/public/dishes/admin/all` (same type required), fallback label `'未分类'`.
- **Error handling**: `Dishes.vue:200-218` has `try/finally` **without `catch`** → a rejection propagates out of `loadAll()`; on mount (`:316`) it becomes an unhandled rejection (interceptor already toasted). `PublicMenu.vue:133-137` explicitly catches and comments "error handled by api interceptor".

### 3.23 `getDishesManageAll`

- **Definition**: `api/index.js:112` → `GET /dishes/manage/all`
- **Body/params**: none. (Distinct from the unused `getDishes` → `GET /dishes`.)
- **Caller**: `Dishes.vue:204`.
- **Response consumption** — bare array, each item:

  | Field | Read at | Type / use |
  |---|---|---|
  | `id` | `Dishes.vue:10,28,238,298,311` | id; `:key`, `getDishDetail(id)`, `updateDish(id,…)`, `toggleDishStatus(id)` |
  | `name` | `Dishes.vue:13,231` | string |
  | `imageEmoji` | `Dishes.vue:11,232` | string or null (`dish.imageEmoji \|\| '🍽️'`) |
  | `status` | `Dishes.vue:15,16,28,29` | **number `1`=上架 / `0`=下架**; strict `=== 1` checks for the tag colour and the toggle button label |
  | `price` | `Dishes.vue:18` | number/numeric string, rendered after `¥` (no formatting) |
  | `spiceLevel` | `Dishes.vue:21-23,234` | number `0/1/2` |
  | `categoryId` | `Dishes.vue:233` (edit seeding) | must match `categories[].id` |

- **Error handling**: no `catch` (see §3.22).

### 3.24 `getDishDetail`

- **Definition**: `api/index.js:113` → `GET /dishes/{id}`
- **Path param**: `dish.id` (`Dishes.vue:238`).
- **Response consumption** — single object; only `ingredients` is read, at `Dishes.vue:239-246`:
  ```js
  const detail = await getDishDetail(dish.id)
  selectedIngredients.value = (detail.ingredients || []).map(i => ({
    id: i.ingId,
    name: i.name,
    emoji: i.emoji || '',
    unit: i.unit,
    price: parseFloat(i.price),
    amount: parseFloat(i.amount),
  }))
  ```
  | Field | Type / use |
  |---|---|
  | `ingredients` | array (may be absent/null → `\|\| []`) |
  | `ingredients[].ingId` | id; **renamed to `id`** client-side and posted back as `ingId` on save |
  | `ingredients[].name` | string |
  | `ingredients[].emoji` | string or null |
  | `ingredients[].unit` | string |
  | `ingredients[].price` | numeric string/number → `parseFloat` |
  | `ingredients[].amount` | numeric string/number → `parseFloat`; feeds `el-input-number v-model="sel.amount"` (`Dishes.vue:128-136`) and the estimated-price computation (`Dishes.vue:193-198`) |

- **Error handling**: `catch (e) { selectedIngredients.value = [] }` (`Dishes.vue:247-249`) — the dialog still opens, just with no ingredients selected.

### 3.25 `createDish`

- **Definition**: `api/index.js:116` → `POST /dishes` (body passthrough)
- **Body** — exact literal at `Dishes.vue:289-296`:
  ```js
  const payload = {
    ...dishForm.value,
    ingredients: selectedIngredients.value.map(s => ({
      ingId: s.id,
      amount: parseFloat(s.amount) || 100,
      unit: s.unit,
    })),
  }
  await createDish(payload)
  ```
  with `dishForm` initialized/updated at `Dishes.vue:180,223,230-235` as:
  ```js
  { name: '', imageEmoji: '', categoryId: null, spiceLevel: 0 }
  ```
  → flattened top-level body: `{ name, imageEmoji, categoryId, spiceLevel, ingredients: [{ ingId, amount, unit }] }`

  | Field | UI source | Type |
  |---|---|---|
  | `name` | `el-input v-model="dishForm.name"` (`Dishes.vue:43`) | string; validated non-empty at `:283-286` |
  | `imageEmoji` | `Dishes.vue:46` (maxlength 8) | string (`''` when blank) |
  | `categoryId` | `el-select` (`Dishes.vue:49-51`) | id matching `categories[].id`; `null` when there are no categories |
  | `spiceLevel` | radio `0/1/2` (`Dishes.vue:54-58`) | number |
  | `ingredients[].ingId` | left-column ingredient (`Dishes.vue:84-93`, or drag payload `:275`) | id matching `/ingredients/public[].id` |
  | `ingredients[].amount` | `el-input-number v-model="sel.amount"` (`Dishes.vue:128-135`); defaults: `100` on add (`:258`), `100` on drop (`:278`), `100` fallback on save (`:293`) | **number** |
  | `ingredients[].unit` | copied from the ingredient | string — **note it is a copy of the ingredient's unit, not user-edited**, so a stale client value can be sent |

  **Not sent**: `price` (server recomputes; the UI only shows an estimate, `Dishes.vue:146,193-198`), `status` (the create dialog has no status control for dishes).
- **Response**: not read; `ElMessage.success('保存成功')`, close, `loadAll()` (`Dishes.vue:302-304`).
- **Error handling**: `try/finally` with **no `catch`** (`Dishes.vue:288-307`) → rejection propagates (interceptor toasts), dialog stays open because the close is after the await.

### 3.26 `updateDish`

- **Definition**: `api/index.js:117` → `PUT /dishes/{id}`
- **Path param**: `editingDish.value.id` (`Dishes.vue:298`).
- **Body**: **identical shape to `createDish`** (§3.25) — same `payload` object, `Dishes.vue:289-298`. `ingredients` is the **full replacement list** (client sends the whole selection each time).
- **Response**: not read; same success path.
- **Error handling**: same as §3.25 (no catch).

### 3.27 `toggleDishStatus`

- **Definition**: `api/index.js:118` → `PUT /dishes/{id}/status`
- **Path param**: `dish.id` (`Dishes.vue:311`).
- **Body**: **none** (`service.put(url)` with no data) → the backend flips the value itself.
- **Response**: not read; `ElMessage.success('已切换状态')` + `loadAll()` (`Dishes.vue:312-313`).
- **Error handling**: `toggleDish` (`Dishes.vue:310-314`) has **no try/catch** → rejection is unhandled (interceptor toasts).
- **Callers of the toggle button**: `Dishes.vue:28-30`, label depends on `dish.status === 1`.

### 3.28 `getIngredientCategories`

- **Definition**: `api/index.js:93` → `GET /ingredient-categories`
- **Callers**: `Dishes.vue:206` / `Ingredients.vue:204`.
- **Response consumption** — bare array:

  | Field | Read at | Type |
  |---|---|---|
  | `id` | `Dishes.vue:74,76,78`; `Ingredients.vue:21,23,100,105,241` | id; compared with `ingredient.categoryId` by `===` |
  | `name` | `Dishes.vue:80`; `Ingredients.vue:26,102,216,233` | string |
  | `emoji` | `Dishes.vue:80` (`cat.emoji \|\| ''`); `Ingredients.vue:26,101,122,216,233` | string or null |
  | `sort` | `Ingredients.vue:103,233` (`cat.sort \|\| 0`) | number |

- **Error handling**: `Dishes.vue` no catch; `Ingredients.vue:199-211` also `try/finally` **without catch**.

### 3.29 `createIngredientCategory`

- **Definition**: `api/index.js:94` → `POST /ingredient-categories`
- **Body** — `catForm.value`, defined at `Ingredients.vue:196` / reset at `:227`:
  ```js
  const catForm = ref({ name: '', emoji: '', sort: 0 })
  await createIngredientCategory(catForm.value)   // Ingredients.vue:244
  ```
  | Field | UI source | Type |
  |---|---|---|
  | `name` | `Ingredients.vue:119` | string (no required validation — an empty name is submitted if the user clicks 保存) |
  | `emoji` | `Ingredients.vue:122` (maxlength 8) | string |
  | `sort` | `el-input-number :min="0"` (`Ingredients.vue:125`) | number |
- **Response**: not read; `ElMessage.success('创建成功')`, `loadAll()` (`Ingredients.vue:245-249`).
- **Error handling**: `try/finally`, no catch (`Ingredients.vue:238-252`).

### 3.30 `updateIngredientCategory`

- **Definition**: `api/index.js:95` → `PUT /ingredient-categories/{id}`
- **Path param**: `editingCat.value.id` (`Ingredients.vue:241`).
- **Body**: same `{ name, emoji, sort }`, seeded from the row at `Ingredients.vue:233` (`emoji: cat.emoji || ''`, `sort: cat.sort || 0`).
- **Response**: not read; success + `loadAll()`.
- **Error handling**: same as §3.29.

### 3.31 `deleteIngredientCategory`

- **Definition**: `api/index.js:96` → `DELETE /ingredient-categories/{id}`
- **Body**: none. **Path param**: `cat.id` (`Ingredients.vue:257`).
- **Expected semantics** (`Ingredients.vue:256` copy): "删除分类后，该分类下的配菜将失去分类" → ingredients keep existing with a null/dangling `categoryId` (the UI tolerates it: `getCategoryName` renders `'-'`, `Ingredients.vue:214-217`).
- **Response**: not read; `ElMessage.success('已删除')`, reset filter, `loadAll()`.
- **Error handling**: **none** — `deleteCategory` (`Ingredients.vue:255-262`) has no try/catch at all, so both an API failure *and* a user pressing Cancel on the confirm box produce unhandled rejections (the API-failure toast still fires from the interceptor).

### 3.32 `getIngredientsPublic`

- **Definition**: `api/index.js:101` → `GET /ingredients/public`
- **Callers**: `Dishes.vue:207` (left-hand ingredient library), `Ingredients.vue:205` (the 配菜管理 list itself).
- **Response consumption** — bare array:

  | Field | Read at | Type / use |
  |---|---|---|
  | `id` | `Dishes.vue:85,119,254,263,277`; `Ingredients.vue:74,193` | id; used as `:key`, matched against `selectedIngredients[].id`, and converted to `ingId` in dish payloads |
  | `name` | `Dishes.vue:91,189`; `Ingredients.vue:41,79,273,296` | string; `Dishes.vue:189` `i.name.includes(ingSearch.value)` (search box `Dishes.vue:69`) |
  | `emoji` | `Dishes.vue:90,124`; `Ingredients.vue:41,79,273` | string or null |
  | `unit` | `Dishes.vue:92,136,242`; `Ingredients.vue:49,52,86,273` | string, rendered raw |
  | `price` | `Dishes.vue:92,195` (`parseFloat(ing.price)`); `Ingredients.vue:52,86,273` | number/numeric string |
  | `categoryId` | `Dishes.vue:188` (`i.categoryId === activeCategory.value`, strict); `Ingredients.vue:193` (strict) | must be the same type as `ingredient-categories[].id` |
  | `status` | `Ingredients.vue:57,58,76,81` | number `1`=启用/在用, `0`=停售/下架 |

- **Important**: `/ingredients/public` is used as the **admin list** in `Ingredients.vue` — it must return *all* ingredients for the family, not only enabled ones (the page renders and filters on `status`).
- **Error handling**: `Dishes.vue` no catch; `Ingredients.vue:199-211` no catch.

### 3.33 `createIngredient`

- **Definition**: `api/index.js:102` → `POST /ingredients`
- **Body** — `Ingredients.vue:280`:
  ```js
  const payload = { ...ingForm.value, status: 1 }
  await createIngredient(payload)
  ```
  with `ingForm` reset at `Ingredients.vue:267`:
  ```js
  ingForm.value = { name: '', categoryId: categories.value[0]?.id || null, unit: '克', price: 0, emoji: '' }
  ```
  → flattened body: `{ name, categoryId, unit, price, emoji, status }`

  | Field | UI source | Type |
  |---|---|---|
  | `name` | `Ingredients.vue:143` | string (no required validation) |
  | `categoryId` | `Ingredients.vue:146-148`; defaults to the **first category id**, `null` if none | id |
  | `unit` | `Ingredients.vue:151`; default `'克'` | string |
  | `price` | `el-input-number :min="0" :precision="2"` (`Ingredients.vue:154`) | number |
  | `emoji` | `Ingredients.vue:157` (maxlength 8) | string |
  | `status` | **hard-coded `1`** on create (`Ingredients.vue:280`) — there is no status control on this page | number |

- **Response**: not read; `ElMessage.success('保存成功')`, close, `loadAll()` (`Ingredients.vue:286-288`).
- **Error handling**: `try/finally`, no catch (`Ingredients.vue:278-291`).

### 3.34 `updateIngredient`

- **Definition**: `api/index.js:103` → `PUT /ingredients/{id}`
- **Path param**: `editingIng.value.id` (`Ingredients.vue:282`).
- **Body**: same flattened `{ name, categoryId, unit, price, emoji, status }`; `status` is **forced to `1`** even when editing an ingredient whose `status` was `0` (`Ingredients.vue:280`, seeded at `:273` without `status`). ⚠️ Editing a disabled ingredient silently re-enables it. The new backend receives `status: 1` in that case.
- **Response**: not read; success + `loadAll()`.
- **Error handling**: same as §3.33.

### 3.35 `deleteIngredient` (api/index.js export name)

- **Definition**: `api/index.js:105` → `DELETE /ingredients/{id}`
- **Caller**: `Ingredients.vue:174` imports it **aliased**: `deleteIngredient as apiDeleteIngredient`, called at `Ingredients.vue:298`.
- **Body**: none. **Path param**: `row.id`.
- **Response**: not read; `ElMessage.success('已删除')`, `loadAll()`.
- **Error handling**: **none** around the API call — `Ingredients.vue:294-301` only wraps the `ElMessageBox.confirm` (early `return` on cancel); `await apiDeleteIngredient(row.id)` is bare.

### 3.36 `getPublicDishes`

- **Definition**: `api/index.js:137` → `GET /public/dishes/admin/all`
- **Caller**: `PublicMenu.vue:128`.
- **Response consumption** — bare array:

  | Field | Read at | Type / use |
  |---|---|---|
  | `id` | `PublicMenu.vue:37,44,59,150,164,182` | id; `showDialog('edit', row)` stores it as `editingId` |
  | `name` | `PublicMenu.vue:13,48,67,148` | string; validated with `form.value.name?.trim()` (`:157`) |
  | `imageEmoji` | `PublicMenu.vue:13,46` | string or null (`\|\| '🍱'` in the card view; the table cell renders it raw) |
  | `categoryId` | `PublicMenu.vue:17` | matched with `categories[].id` via `find(x => x.id === id)` → **strict type equality** |
  | `spiceLevel` | `PublicMenu.vue:21-22,49` | number `0/1/2` |
  | `description` | `PublicMenu.vue:26,55` | string or null (`v-if`) |
  | `status` | `PublicMenu.vue:29,30,51,52` | number `1`=上架 / `0`=下架 (strict `=== 1`) |

- **⚠️ Round-trip trap**: `showDialog('edit', row)` does `form.value = { ...row }` (`PublicMenu.vue:151`) and then `updatePublicDish(editingId, form.value)` — the **entire row object is echoed back**, so the backend must tolerate unknown extra fields and the row must not carry read-only fields that would break an update. Anything the backend adds to this payload will be sent back verbatim.
- **Error handling**: `PublicMenu.vue:133-137` catches and ignores (interceptor toasts).

### 3.37 `createPublicDish`

- **Definition**: `api/index.js:138` → `POST /public/dishes`
- **Body** — `form.value` (`PublicMenu.vue:116`, reset at `:148`):
  ```js
  { name: '', imageEmoji: '', categoryId: null, spiceLevel: 0, description: '', status: 1 }
  ```
  | Field | UI source | Type |
  |---|---|---|
  | `name` | `PublicMenu.vue:67` (maxlength 64) | string; `ElMessage.warning('请输入菜名')` if blank (`:157-160`) |
  | `imageEmoji` | `PublicMenu.vue:70` (maxlength 8) | string |
  | `categoryId` | `PublicMenu.vue:73-75` | id or `null` |
  | `spiceLevel` | `PublicMenu.vue:78-82` | number `0/1/2` |
  | `description` | `PublicMenu.vue:85` (textarea, maxlength 255) | string |
  | `status` | `PublicMenu.vue:88-91` | number `1`/`0`, default `1` |
- **Response**: not read; `ElMessage.success('创建成功')`, close, `loadData()` (`PublicMenu.vue:167-171`).
- **Error handling**: `catch (e) {}` (`PublicMenu.vue:172-174`) — interceptor toast only.

### 3.38 `updatePublicDish`

- **Definition**: `api/index.js:139` → `PUT /public/dishes/{id}`
- **Path param**: `editingId` (set from `row.id` at `PublicMenu.vue:150`).
- **Body**: `form.value` — the **spread of the original row plus any edits** (see the round-trip trap in §3.36); on a pure edit with no changes the backend receives exactly what `GET /public/dishes/admin/all` returned for that row.
- **Response**: not read; `ElMessage.success('更新成功')`, `loadData()`.
- **Error handling**: `catch (e) {}`.

### 3.39 `deletePublicDish`

- **Definition**: `api/index.js:140` → `DELETE /public/dishes/{id}`
- **Body**: none. **Path param**: `row.id` (`PublicMenu.vue:182`).
- **Response**: not read; `ElMessage.success('删除成功')` + `loadData()`.
- **Error handling** — the **only** component with explicit API error UX:
  ```js
  // PublicMenu.vue:180-187
  try {
    await ElMessageBox.confirm('删除后用户将无法再加入此菜品，是否确认删除？', '确认删除', { type: 'warning' })
    await deletePublicDish(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
  ```
  → a **double toast** on API failure (interceptor's `message` + this `删除失败`). Cancel is detected by comparing to the string `'cancel'`.

### 3.40 `getPublicIngredients`

- **Definition**: `api/index.js:143` → `GET /public/ingredients/admin/all`
- **Caller**: `PublicIngredients.vue:162`.
- **Response consumption** — bare array:

  | Field | Read at | Type / use |
  |---|---|---|
  | `id` | `PublicIngredients.vue:72,199,215` | id |
  | `name` | `PublicIngredients.vue:40,78,99,184,213` | string |
  | `categoryId` | `PublicIngredients.vue:45,84,151,185` (strict `===` against `categories[].id`) | id |
  | `unit` | `PublicIngredients.vue:48,51,85,107,187` | string |
  | `price` | `PublicIngredients.vue:51,85,110,188` | number/numeric string (`parseFloat` on edit) |
  | `emoji` | `PublicIngredients.vue:40,78,113,189` | string or null |
  | `status` | `PublicIngredients.vue:56,58,75,79,81,190` | number `1`=上架 / `0`=下架 |
  | *(implicit)* `sort`, `description` | not read | ignored |

- **Error handling**: `PublicIngredients.vue:156-168` `try/finally`, **no catch**.

### 3.41 `getPublicIngredientCategories`

- **Definition**: `api/index.js:144` → `GET /public/ingredients/categories`
- **Caller**: `PublicIngredients.vue:161`.
- **Response consumption**: bare array of `{ id, name, emoji }` — `id`/`name`/`emoji` read at `PublicIngredients.vue:20-27,73,103,147,172-173`.
- **Note**: this is a **separate** category list from `/ingredient-categories` (§3.28). `PublicIngredients.vue` never calls the private one.
- **Error handling**: same as §3.40.

### 3.42 `createPublicIngredient`

- **Definition**: `api/index.js:145` → `POST /public/ingredients`
- **Body** — `ingForm.value` (`PublicIngredients.vue:154,178`):
  ```js
  { name: '', categoryId: null, unit: '克', price: 0, emoji: '', status: 1 }
  ```
  | Field | UI source | Type |
  |---|---|---|
  | `name` | `PublicIngredients.vue:99` | string (no required validation) |
  | `categoryId` | `PublicIngredients.vue:102-104`; defaults to `categories[0]?.id \|\| null` | id |
  | `unit` | `PublicIngredients.vue:107`; default `'克'` | string |
  | `price` | `el-input-number :min="0" :precision="2"` (`:110`) | number |
  | `emoji` | `PublicIngredients.vue:113` (maxlength 8) | string |
  | `status` | radio `1/0` (`:116-119`), default `1` | number |
- **Response**: not read; `ElMessage.success('保存成功')`, close, `loadAll()` (`PublicIngredients.vue:203-205`).
- **Error handling**: `try/finally`, no catch (`:195-208`).

### 3.43 `updatePublicIngredient`

- **Definition**: `api/index.js:146` → `PUT /public/ingredients/{id}`
- **Path param**: `editingIng.value.id` (`PublicIngredients.vue:199`).
- **Body**: `ingForm.value` — seeded at `PublicIngredients.vue:184-191`:
  ```js
  { name, categoryId, unit, price: parseFloat(ing.price), emoji: ing.emoji || '', status: ing.status != null ? ing.status : 1 }
  ```
  → same six fields; unlike §3.34, `status` **is** preserved here.
- **Response**: not read; success + `loadAll()`.
- **Error handling**: same as §3.42.

### 3.44 `deletePublicIngredient`

- **Definition**: `api/index.js:147` → `DELETE /public/ingredients/{id}`
- **Caller**: aliased as `apiDeleteIngredient` (`PublicIngredients.vue:136`, called `:215`).
- **Body**: none. **Path param**: `row.id`.
- **Response**: not read; `ElMessage.success('已删除')`, `loadAll()`.
- **Error handling**: confirm-box cancellation returns early (`PublicIngredients.vue:212-214`); **no catch around the API call** (`:215`).

### 3.45 `changeAdminPassword`

*(Auth endpoint, added by the migration; numbered last only to avoid renumbering §3.1–§3.44.)*

- **Definition**: `api/index.js:67-70`
  ```js
  // admin-web/src/api/index.js:67-70
  /** 修改当前管理员的登录密码（后台自助改密） */
  export const changeAdminPassword = async (oldPassword, newPassword) => {
    return service.post('/admin/change-password', { oldPassword, newPassword })
  }
  ```
- **Method + path**: `POST /admin/change-password` (effective `/api/admin/change-password`).
- **Body**: `{ oldPassword: <string>, newPassword: <string> }`
  - `oldPassword` ← `pwdForm.oldPassword` (`App.vue:159`)
  - `newPassword` ← `pwdForm.newPassword` (`App.vue:162`)
  - ⚠️ **`pwdForm.confirmPassword` (`App.vue:165`) is NOT sent** — the match check is client-side only.
  - Validation before the call (`App.vue:322-326`), all early-returning with `ElMessage.warning`:
    ```js
    if (!oldPassword) return ElMessage.warning('请输入原密码')
    if (!newPassword || newPassword.length < 4) return ElMessage.warning('新密码至少 4 位')
    if (newPassword !== confirmPassword) return ElMessage.warning('两次输入的新密码不一致')
    ```
    → the only length rule is **`newPassword.length >= 4`**; the backend should enforce its own minimum.
- **Query params**: none.
- **Response**: **not read** (`return service.post(...)` returns `res.data.data` to the caller, which ignores it).
- **Success flow** (`App.vue:329-338`):
  ```js
  await changeAdminPassword(oldPassword, newPassword)
  pwdVisible.value = false
  ElMessage.success('密码已修改，请用新密码重新登录')
  await chefLogout()
  router.push('/login')
  ```
  → **the backend must invalidate the current token as part of this call** (or at least the client assumes
  it is gone). The frontend forces a re-login. `admin-api` still returns `ok(res, true)` from
  `/api/admin/change-password` (`admin-api/src/server.js:53-61`) and does **not** revoke — since tokens are
  stateless JWTs, an old token stays valid until `exp` (12 h default). If that matters, add `token_version`
  or shorten TTL.
- **Trigger UI**: the "修改密码" button in the desktop sidebar footer (`App.vue:41`) and in the mobile
  drawer footer (`App.vue:144`), both calling `openPwdDialog()`.
- **Error handling**: `catch (e) { /* 拦截器已弹错误提示 */ }` (`App.vue:334-336`) — interceptor toast only;
  the dialog stays open so the user can retry.

---

## 4. Exported-but-UNUSED functions

These are exported from `api/index.js` but **never imported anywhere in `admin-web/src`**. They are listed
with their contract as declared, in case the new service wants parity (the mini-program is the likely
consumer of the same routes).

| Export | Method + path | Params / body | Declared at |
|---|---|---|---|
| `getChefStatus` | `GET /me/chef-status` | — | `api/index.js:85` |
| `getTodayMenu` | `GET /menu/today` | — | `api/index.js:86` |
| `getTodayMenuByUser` | `GET /menu/today/by-user` | — | `api/index.js:87` |
| `getTodayTotal` | `GET /menu/today/total` | — | `api/index.js:88` |
| `confirmMenuItem` | `PUT /menu/items/{id}/confirm` | no body | `api/index.js:89` |
| `cancelMenuItem` | `DELETE /menu/items/{id}` | no body | `api/index.js:90` |
| `getIngredients` | `GET /ingredients` | `params` passthrough (caller-supplied) | `api/index.js:99` |
| `getIngredient` | `GET /ingredients/{id}` | — | `api/index.js:100` |
| `toggleIngredient` | `PUT /ingredients/{id}/toggle` | no body | `api/index.js:104` |
| `getDishes` | `GET /dishes` | — | `api/index.js:111` |
| `getDishRecommendation` | `GET /dishes/recommend` | `params: { ingIds, threshold }` | `api/index.js:114-115` |
| `getShoppingList` | `GET /menu/shopping-list` | `params: { date }` | `api/index.js:121` |
| `getMenuHistory` | `GET /menu/today/by-user` | `params: { date }` | `api/index.js:124` |

`getDishRecommendation` is the only caller-shaped query contract, verbatim:
```js
// admin-web/src/api/index.js:114-115
export const getDishRecommendation = (ingIds, threshold) =>
  service.get('/dishes/recommend', { params: { ingIds, threshold } })
```
`ingIds` is presumably an array (axios serializes arrays as repeated keys by default: `ingIds=1&ingIds=2`), `threshold` a number.

**Consequence: no endpoint that admin-web actually calls sends a query string at all.** If the new
service keys list endpoints off query params (e.g. `?familyId=`), admin-web will never supply them —
the family scope must come from `X-Family-Id` / the token (§8).

---

## 5. Login flow end-to-end (Login.vue)

1. **Defaults pre-filled** (`Login.vue:55`):
   ```js
   const form = ref({ username: 'admin', password: '123456' })
   ```
2. **Client-side validation only** — non-empty checks with `ElMessage.warning` and early return (`Login.vue:59-66`). No length/format rules.
3. **Submit** (`Login.vue:58-77`, bound to both `@submit.prevent` and `@click` on the button):
   ```js
   loading.value = true
   try {
     // chefLogin 内部会写加密 token + nickname，无需前端再 setItem
     await chefLogin(form.value.username, form.value.password)
     router.push('/orders')
   } catch (e) {
     // 错误已由 axios 拦截器 ElMessage.error 提示
   } finally {
     loading.value = false
   }
   ```
4. **What `chefLogin` does** (`api/index.js:48-65`): `POST /admin/login {username, password}`; on `res.token` → `setSecureItem('chef_token', res.token)` (AES-GCM → `localStorage`), `primeTokenCache('chef_token')`, and `const shown = res.displayName || res.username; if (shown) setSecureItem('chef_nickname', shown)`. Then `window.dispatchEvent(new CustomEvent('families-changed'))` so `App.vue:351` re-runs `loadFamilies()` (which had already run without a token).
5. **Navigation**: `router.push('/orders')` — a **client-side** push, no full reload. Because `'/orders'` has `meta.requiresAuth` (`router.js:7`), the guard (`router.js:21-32`) re-checks `cachedToken('chef_token')`, which is populated synchronously by step 4.
6. **Storage written**: `chef_token` (encrypted), `chef_nickname` (encrypted, from `displayName || username`, only if non-empty). `admin_family_id` is **not** written by login — it is written later by `App.vue:286`/`:300` when families load.
7. **Failure behaviour**: silent except for the interceptor toast; the button just stops loading and stays on `/login`. **The backend must fail login with a non-2xx status** for the user to see anything (§3.1).
8. **No logout call from Login.vue**; `App.vue:306-309` does `await chefLogout(); router.push('/login')`.
9. **Password change** is not part of the login page: it lives in `App.vue`'s sidebar/drawer dialog and calls `changeAdminPassword` (§3.45), which then force-logs-out back to `/login`.

---

## 6. USED / UNUSED audit

Requested list, verified by grepping all of `admin-web/src` (`grep -rn "\b<fn>\b"`):

| Function | Verdict |
|---|---|
| `getChefStatus` | **UNUSED** (only its own definition, `api/index.js:85`) |
| `getTodayMenu` | **UNUSED** (`api/index.js:86`) |
| `getTodayMenuByUser` | **UNUSED** (`api/index.js:87`) |
| `getTodayTotal` | **UNUSED** (`api/index.js:88`) |
| `confirmMenuItem` | **UNUSED** (`api/index.js:89`) |
| `cancelMenuItem` | **UNUSED** (`api/index.js:90`) |
| `getShoppingList` | **UNUSED** (`api/index.js:121`) |
| `getMenuHistory` | **UNUSED** (`api/index.js:124`) |
| `getDishRecommendation` | **UNUSED** (`api/index.js:114`) |
| `getIngredients` (non-public) | **UNUSED** (`api/index.js:99`) |
| `getIngredient` | **UNUSED** (`api/index.js:100`) |
| `toggleIngredient` | **UNUSED** (`api/index.js:104`) |
| `getOrders` | **USED in `views/Orders.vue:111`** (import) and **`views/Orders.vue:153`** (call) |
| `getTodayOrders` | **USED in `views/Orders.vue:111`** (import) and **`views/Orders.vue:153`** (call) |

Additional unused-but-exported: `getDishes` (`api/index.js:111`).

Additional notes on the audit:

- `components/FamilySelect.vue` **is not rendered anywhere.** Its only mention outside itself is a comment:
  ```js
  // admin-web/src/api/index.js:26
  // 家庭 ID：FamilySelect.vue / App.vue 在 setItem 时同步 prime
  ```
  So `getAdminFamilies` has exactly **one live caller**: `App.vue:279`.
- `store/familyStore.js` (`useFamilyStore`) is likewise imported by nothing; it still calls `getSecureItem('admin_family_id')` at module load (`familyStore.js:21`) — dead code that never runs.
- `window.__adminFamilies` is written at `App.vue:281` and read nowhere.

---

## 7. Router routes → view mapping

Verbatim from `admin-web/src/router.js:4-14`:

```js
const routes = [
  { path: '/login', name: 'Login', component: () => import('./views/Login.vue') },
  { path: '/', redirect: '/orders' },
  { path: '/orders', name: 'Orders', component: () => import('./views/Orders.vue'), meta: { requiresAuth: true } },
  { path: '/dishes', name: 'Dishes', component: () => import('./views/Dishes.vue'), meta: { requiresAuth: true } },
  { path: '/public-menu', name: 'PublicMenu', component: () => import('./views/PublicMenu.vue'), meta: { requiresAuth: true } },
  { path: '/ingredients', name: 'Ingredients', component: () => import('./views/Ingredients.vue'), meta: { requiresAuth: true } },
  { path: '/public-ingredients', name: 'PublicIngredients', component: () => import('./views/PublicIngredients.vue'), meta: { requiresAuth: true } },
  { path: '/families', name: 'Families', component: () => import('./views/Families.vue'), meta: { requiresAuth: true } },
  { path: '/reload', name: 'Reload', component: { render: () => null }, meta: { requiresAuth: true } },
]
```

| Path | View | Page title (`App.vue:221-231`) | Auth |
|---|---|---|---|
| `/login` | `views/Login.vue` | — (no sidebar) | public |
| `/` | → redirect `/orders` | — | — |
| `/orders` | `views/Orders.vue` | 订单管理 | token |
| `/dishes` | `views/Dishes.vue` | 菜单管理 | token |
| `/public-menu` | `views/PublicMenu.vue` | 公共菜单 | token |
| `/ingredients` | `views/Ingredients.vue` | 配菜管理 | token |
| `/public-ingredients` | `views/PublicIngredients.vue` | 公共配菜 | token |
| `/families` | `views/Families.vue` | 家庭管理 | token |
| `/reload` | inline `{ render: () => null }` | 主厨后台 (fallback) | token |

- History mode: `createWebHistory(import.meta.env.BASE_URL)` with `base: '/'` (`router.js:17`, `vite.config.js:14-16`) → server must serve `index.html` for unknown paths (nginx already does `try_files ... /index.html`, `admin-web/nginx/default.conf:10-12`).
- **No catch-all / 404 route** — an unknown path renders an empty `<router-view />` (only the chrome remains).
- **Guard is token-presence only** (`router.js:21-32`), no role/permission check:
  ```js
  let token = cachedToken('chef_token')
  if (!token) token = await primeTokenCache('chef_token')
  if (to.meta.requiresAuth && !token) next('/login')
  else next()
  ```
- **`FamilyUsers.vue` is NOT a route.** It is imported and embedded inside `Families.vue`:
  ```js
  // admin-web/src/views/Families.vue:209
  import FamilyUsers from './FamilyUsers.vue'
  ```
  ```html
  <!-- admin-web/src/views/Families.vue:131-135 -->
  <FamilyUsers
    :key="currentFamily.familyId"
    :family="currentFamily"
    @changed="reloadCurrent"
  />
  ```
  It is rendered only when a family row was clicked (`Families.vue:4` `<template v-if="!currentFamily">` … `<template v-else>` at `:125`). Its required prop is a **single element of `GET /admin/families/overview`** (`FamilyUsers.vue:176`: `family: { type: Object, required: true }`), and it reads `family.name`, `family.code`, `family.memberCount`, `family.ownerName`, `family.chefName`, `family.familyId`, `family.members` — it **never issues a request to fetch members itself**; members come from the overview payload already in `Families.vue`, sorted client-side (`FamilyUsers.vue:227-230`). Its only own requests are `getAdminUsers` plus the mutations.
- `@changed` → `Families.vue:410-417 reloadCurrent()` → `loadData()` (both endpoints) → `families-changed` event → `App.vue` reloads `/admin/families`.

---

## 8. `X-Family-Id` header and `admin_family_id`

**Where it is sent** — exactly one place, the axios request interceptor:
```js
// admin-web/src/api/index.js:27-28
const famId = cachedToken('admin_family_id')
if (famId) config.headers['X-Family-Id'] = famId
```
So `X-Family-Id` rides on **every** request (GET, POST, PUT, DELETE) **whenever the in-memory cache holds a
value**, and the value is the `String(familyId)` that was last persisted.

**Where the value is produced / persisted:**

| Site | Code | Notes |
|---|---|---|
| `App.vue:283-287` | `currentFamily.value = def.familyId; await setSecureItem('admin_family_id', String(def.familyId)); primeTokenCache('admin_family_id')` | runs **only** when the stored family is absent/not in the freshly-fetched list (i.e. first ever login, or the stored family was deleted) |
| `App.vue:299-303` | `await setSecureItem('admin_family_id', String(val)); primeTokenCache('admin_family_id'); currentFamily.value = Number(val); window.dispatchEvent(new CustomEvent('admin-family-changed', { detail: val }))` | user picked a family in the sidebar picker / mobile dropdown |
| `FamilySelect.vue:57-64` | same write + `emit('family-changed', val)` | **dead code** (component unused) |
| `store/familyStore.js:21-29` | reads only | dead code |
|  `api/index.js:80` | `removeSecureItem('admin_family_id')` in `chefLogout` | cleared on logout |

**Where it is read:**
- `api/index.js:27` (header) — from the sync cache.
- `App.vue:345-347`: `const fam = await getSecureItem('admin_family_id'); if (fam) currentFamily.value = Number(fam) || null` — UI selection restore, **does not prime the axios cache**.
- `FamilySelect.vue:47` (dead).
- `Orders.vue:244`: `const currentFamilyId = await getSecureItem('admin_family_id')` → matched against `getAdminFamiliesOverview()` to pick the member list for the order-edit dropdown.

**What it means for request scope.** `X-Family-Id` is the only per-request family selector the frontend
has; there is no `?familyId=` on any live call. It scopes every family-owned resource: `/dishes*`,
`/ingredients*`, `/ingredient-categories`, `/categories`(presumably), `/orders*`, and it is presumably
also the authorization input for "may this chef confirm this order" (`canConfirm`).

**⚠️ The header is NOT reliably present — this is a real defect in the current client.**

The in-memory cache (`_tokenCache`) and `localStorage` are primed at different times:

- On a **full page load**, `App.vue:341-353 onMounted` primes `chef_token` (`:291`) but only *reads*
  `admin_family_id` (`:293`) — it never calls `primeTokenCache('admin_family_id')`.
- `loadFamilies()` (`App.vue:276-297`) primes `admin_family_id` **only inside the "stored family missing
  from the list" branch** (`:264-265`). If the saved id *is* found in the list, the code takes the `else`
  branch (`:267`) and the axios cache stays **empty**.
- `router.js:21-32` primes only `chef_token`.

Net effect: after a refresh, with a previously selected family that still exists, the UI shows a current
family, `admin_family_id` is in `localStorage`, but `cachedToken('admin_family_id')` is `''` → **the
`X-Family-Id` header is omitted on every request until the user explicitly switches family.** (Also,
`chefLogout` clears the whole cache, and a re-login does not re-prime it: login only primes `chef_token`.)

**Implication for the new Node service:** it must not treat a missing `X-Family-Id` as an error. It must
fall back to a server-side default scope (e.g. the family where the authenticated user is OWNER/CHEF, or
their primary family), and it must behave sanely when the header disagrees with the user's memberships
(return 403 rather than silently acting on another family).

---

## 9. Dependencies on OLD backend permission behaviour

1. **`canConfirm` is a backend-supplied boolean that gates all order mutations.**
   ```js
   // admin-web/src/views/Orders.vue:140
   const canOperate = computed(() => detail.value && detail.value.canConfirm === true)
   ```
   `Orders.vue:45` passes it to `OrderDialog`, which uses it to render *either* the action bar *or* an
   informational alert:
   ```html
   <!-- admin-web/src/components/OrderDialog.vue:56-73 -->
   <div class="dh-actions-bar" v-if="canOperate"> … 一键确认全部 … </div>
   <el-alert v-else title="您不是本家庭的主厨或创建者，无法确认/驳回菜品" type="info" … />
   ```
   and per item:
   ```html
   <!-- admin-web/src/components/OrderDialog.vue:100 -->
   <div class="dhi-actions" v-if="canOperate && it.status !== -1">
   ```
   → the new backend must return `canConfirm: true` **for the OWNER/CHEF of that order's family** and
   `false` otherwise; the string is *not* a role name and must be a real boolean.
   **The backend must still enforce it** — the client hides buttons but never re-checks before PUT/POST.

2. **Family-member role checks are client-side only.** `FamilyUsers.vue` hides 调角色/移出 for OWNER rows
   (`:42-49`, `:73-74`) and relies on the backend to reject demoting/removing an OWNER. No 403-specific
   handling exists — a 403 just becomes a generic `ElMessage.error(err.response.data.message)`.

3. **"One chef per family" is a backend rule advertised in the UI** (`FamilyUsers.vue:131-133`). The client
   does no local conflict resolution: after `adminSetRole`, it relies on a full refetch (`reload()` →
   `Families.vue reloadCurrent()`) to show the demotion. The old premaster must therefore mutate both rows
   atomically.

4. **Deletion cascades are assumed** (and described in confirmation copy): deleting a family
   (`Families.vue:173`), deleting a user (`FamilyUsers.vue:157`), deleting an order (`Orders.vue:282-283`),
   deleting an ingredient category (`Ingredients.vue:256`). The frontend never cleans up related entities.

5. **`adminRevokeUser` semantics** (`api/index.js:160`): "撤销指定用户全部已签发 token" — token
   revocation is expected to be effective immediately (the UI says the user "需重新登录"), i.e. the new
   service needs server-side token invalidation, not just a client-side clear.

6. **`/admin/logout` no longer needs server-side token invalidation** (post-migration contract):
   ```js
   // admin-web/src/api/index.js:72-75
   /**
    * 退出登录：清掉本地加密存储与内存缓存。
    * （新管理服务用无状态 JWT，服务端没有 token_version 需要递增）
    */
   ```
   → **The OLD backend used a per-user `token_version` counter** embedded in issued JWTs, incrementing it
   on logout so old tokens died immediately. The migration explicitly **dropped** that requirement for
   `/admin/logout`: stateless JWT, client-side clear only. Consequence: a token captured before logout
   remains valid until `exp`. `admin-api` answers `app.post('/api/admin/logout', (req, res) => ok(res, true))`
   (`admin-api/src/server.js:49`).
   **Counterpoint**: `adminRevokeUser` (§3.13) still advertises forced logout of *小程序* users, whose tokens
   are issued by the mini-program backend — that is a different token domain and still needs `token_version`.

7. **403/401 swallowing.** There is **no** `if (status === 403)` branch anywhere. Every mutation handler
   is `catch (e) {}` (e.g. `Families.vue:300,319,337,377,397`; `FamilyUsers.vue:263,278,295,309`;
   `Orders.vue:193,207,222,273,295`; `Dishes.vue` none at all), so a permission failure is invisible except
   for the interceptor's toast. A few pages don't even catch (`Ingredients.vue:255-262,294-301`,
   `Dishes.vue:282-314`) → unhandled promise rejections in the console.
   Only two pages show anything beyond the interceptor toast: `PublicMenu.vue:186`
   (`ElMessage.error('删除失败')`, causing a **double toast**) and `Login.vue` (nothing).

8. **No user/role is ever fetched by admin-web.** `getChefStatus` (`/me/chef-status`) is unused, so the SPA
   has no client-side notion of the logged-in user's global role — it derives everything from
   `canConfirm` and from the member `role` strings inside the family overview. Any permission decision the
   new service wants the UI to reflect must be encoded in those payloads.

9. **Token lifetime is client-enforced too**: `secureStore.js:109-113` treats a JWT with `exp` in the past
   as logged-out and **deletes** it, without calling the backend. So `chef_token` must be a JWT with an
   `exp` claim (or omit `exp` entirely to disable the check — not recommended).

---

## 10. Cross-cutting gotchas for the new Node service

1. **Envelope or bust.** `{code:0, data:…}` on every 2xx (§1.3). Errors: non-2xx + `{message: "…"}`.
2. **Lists must be bare arrays**, never `{items:[…]}` or `{list:[…]}`: `getAdminFamilies`,
   `getAdminFamiliesOverview`, `getAdminUsers`, `getOrders`, `getTodayOrders`, `getCategories`,
   `getDishesManageAll`, `getIngredientCategories`, `getIngredientsPublic`, `getPublicDishes`,
   `getPublicIngredients`, `getPublicIngredientCategories`.
   Single objects: `getOrderDetail`, `getDishDetail`, `chefLogin`.
3. **`getOrderDetail().items` must always be an array** — `detail.items.length` is unguarded
   (`OrderDialog.vue:35,76`). Same for `detail.canConfirm === true`.
4. **`userId` type must be consistent across endpoints.** `Families.vue:264-274` uses `Set.has(u.userId)`
   (strict) and `FamilyUsers.vue:97` uses `f.familyId === family.familyId` (strict);
   `Families.vue:414` uses `f.familyId === fid` (strict, but both sides come from the same payload).
   `FamilyUsers.vue:229` even does arithmetic on it: `(a.userId - b.userId)` — with long openid-like
   strings that is `NaN` and the comparator degrades to insertion order. `userId` is documented as an
   openid-like string (`FamilyUsers.vue:349-350`), so **do not return it as a number** unless it truly is.
5. **`familyId` must be the same type in `/admin/families[].familyId`, `/admin/families/overview[].familyId`
   and `/admin/users[].families[].familyId`** — several strict `===` comparisons and a `Number()` round-trip
   through `admin_family_id` (`App.vue:286,302`; `Orders.vue:246`; `FamilySelect.vue:50`).
6. **Numeric fields must be JSON numbers (or clean numeric strings).** `totalAmount`, `itemCount`,
   `confirmedCount`, `rejectedCount`, `price`, `amount`, `memberCount`, `sort` all flow into
   `parseFloat`, `+`, `reduce`, or raw rendering. `memberCount` is rendered **without** a fallback at
   `Families.vue:95` and `FamilyUsers.vue:9`.
7. **Enum spellings are exact:** status numbers `1`=confirmed/on-shelf/enabled, `0`=pending/off-shelf,
   `2`=rejected, `-1`=cancelled; roles are uppercase strings `'OWNER' | 'CHEF' | 'MEMBER'`;
   spice levels `0 | 1 | 2`.
8. **Date fields:** `createdAt` (orders) must be a string ≥16 chars for `OrderCard.formatTime` and gets
   substringed to `MM-DD HH:mm`; `createdAt` (families) and `joinedAt` go through `new Date(s)`.
9. **`PUT` endpoints with no body** (`confirmOrder`, `confirmOrderItem`, `rejectOrderItem`,
   `toggleDishStatus`) expect the server to derive the new state — the client sends no `Content-Length`
   payload at all. Don't require a body.
10. **`toggleDishStatus` / `toggleIngredient` are described as toggles but the client never sends the
    target state** — the server must flip `status`, and `Dishes.vue:313` refetches to see the result.
11. **Raw echo on `PublicMenu` edit** (§3.36): `updatePublicDish` may receive back literally every field of
    the list row. Keep read-only/computed fields out of that payload or make `PUT` tolerant of them.
12. **`updateIngredient` forces `status: 1`** (`Ingredients.vue:280`) — the private-API edit path cannot
    keep an ingredient disabled. Contract-wise this is a client bug the backend must simply accept.
13. **`X-Family-Id` may be absent** (§8) — always design a fallback.
14. **10 s timeout** on every request; long-running endpoints must stay under it.
15. **Login failure must be a non-2xx HTTP status**, otherwise the user gets no feedback at all (§3.1).
16. **Mount everything under `/api`.** `VITE_API_URL=/api` + a prefix-preserving reverse proxy on both the
    dev server and the production `server.js` mean the service must answer `/api/<path>` — see §1.1.
    It must **not** be reachable only at the root path.
17. **CORS is no longer needed in the happy path** (same-origin), but `admin-api` still opens it for
    LAN/domain access (`admin-api/src/server.js:19-26`) — it whitelists
    `Content-Type, Authorization, X-Family-Id`.
18. **The family switcher is event-driven, not request-driven.** `App.vue:299-303 onSwitchFamily`
    persists the id and dispatches `admin-family-changed`; only these components listen and refetch:
    `Dishes.vue:318`, `Ingredients.vue:305`, `Families.vue:421`, plus `FamilySelect.vue:81` (dead code).
    **`Orders.vue`, `PublicMenu.vue` and `PublicIngredients.vue` do NOT listen** — they keep whatever they
    loaded until the user hits 刷新 or navigates. Whichever way the new service scopes data (the
    `X-Family-Id` header, or a server-side default), the client will not re-request those three pages on a
    family switch.
19. **Migration status at the moment of writing** (do not treat as spec): `admin-api/` already contains
    `src/server.js`, `src/auth.js`, `src/db.js`, `src/routes/{meta}.js` and `sql/001_t_admin_user.sql`;
    `meta.js` even stubs `GET /me/chef-status` for compatibility (`admin-api/src/routes/meta.js:12-19`)
    although admin-web never calls it (§6). `src/routes/{orders,dishes,library,publicLib,families,users}.js`
    are referenced by `server.js` but were not present when this document was written — those are the
    endpoints §3.3–§3.44 specify.
