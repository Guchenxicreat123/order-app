/**
 * 微信订阅消息授权工具
 *
 * 微信「一次性订阅」的规则（决定了本文件的写法）：
 *  1. 用户点一次「允许」＝ 服务端只能推 **一条**。所以要反复要授权，不能只问一次就完事。
 *  2. `requestSubscribeMessage` 必须由**用户点击**触发，不能放在 onLaunch/onShow 里自动调用。
 *  3. 它**不会抛异常**：用户拒绝时走 success 回调，每个 tmplId 的值是
 *     'accept'（允许）/ 'reject'（拒绝）/ 'ban'（被封禁）/ 'filter'（模板被过滤）。
 *  4. 面板上的标题由微信模板决定，我们无法自定义，所以**中间不要再插自定义弹窗**——
 *     多一次弹窗就多一次「用户手势丢失、微信静默拒绝」的机会。
 *
 * 对外函数：
 *  - {@link warmPushConfig}：页面 onShow 时后台预热配置（模板 ID），不进 await 链；
 *  - {@link ensureSubscribe}：点击回调里同步调用，直接拉微信面板、上报后端记账；
 *  - {@link ensureSubscribeBulk}：同样的机制，但连点多次把额度堆上去（主厨认领/确认订单时用）；
 *  - {@link CHEF_QUOTA_ON_CONFIRM}：主厨确认订单时补的额度，所有确认入口共用这一个数字。
 */

import { getPushConfig, reportSubscribeGrant } from './request.js'

/** 后端推送配置缓存（模板 ID / dry-run），预热后点击时可直接同步读取 */
let configCache = null
let configAt = 0
let warming = false
const CONFIG_TTL = 10 * 60 * 1000

/** 是否是微信小程序环境（H5 / App 上 requestSubscribeMessage 不存在） */
function isMp() {
  // #ifdef MP-WEIXIN
  return true
  // #endif
  // eslint-disable-next-line no-unreachable
  return false
}

/** 当前角色：主厨收「新订单」，普通成员收「订单状态变更」 */
export function currentRole() {
  try {
    const info = uni.getStorageSync('userInfo') || {}
    const fams = info.families || []
    const activeId = uni.getStorageSync('activeFamilyId')
    const fam = fams.find((f) => f.active) || fams.find((f) => f.familyId === activeId) || fams[0]
    return fam && fam.role === 'CHEF' ? 'CHEF' : 'MEMBER'
  } catch (e) {
    return 'MEMBER'
  }
}

/**
 * 预热推送配置。页面 onShow 里调用即可（不要 await，失败静默）。
 * 预热过之后 {@link ensureSubscribe} 才能在点击回调里同步拿到模板 ID。
 */
export async function warmPushConfig(role) {
  if (configCache && Date.now() - configAt < CONFIG_TTL) return configCache
  if (warming) return configCache
  warming = true
  try {
    const cfg = await getPushConfig(role || currentRole())
    if (cfg && cfg.configured) {
      configCache = cfg
      configAt = Date.now()
    }
    return cfg
  } catch (e) {
    return null
  } finally {
    warming = false
  }
}

/** 模板 ID 是否可用（微信模板 ID 为 43 位左右，这里只做粗校验） */
function validTemplateId(id) {
  return !!id && typeof id === 'string' && id.length >= 20
}

/**
 * 推送配置是否已经预热好（能同步拿到模板 ID）。
 *
 * 用途：页面「首次点击即要授权」的时机判断。如果 onShow 刚发出 warmPushConfig、
 * 还没回来、用户就立刻点了页面——这时调用 {@link ensureSubscribeBulk} 会因
 * `configCache` 为空直接返回 0，白费掉这次用户手势。所以调用方应当先问这里：
 * 没预热好就**不要**把「已经问过」的标记置上，等下一次点击还有机会。
 *
 * @param {'CHEF'|'MEMBER'} [role] 收件人角色
 */
export function isPushConfigReady(role) {
  return validTemplateId(pickTemplate(configCache, role))
}

/**
 * 角色 → 模板 ID。
 *
 * ⚠️ **调用方传的 role 优先，`cfg.role` 只在没传时兜底。**
 * cfg.role 是后端按 t_family_member.role 判出来的「你是谁」，而这里要的是
 * 「这次流程在替哪个身份要授权」——主厨本人也会下单，那时他需要的是
 * 「订单状态变更通知」。所以不能拿 cfg.role 覆盖调用方的意图。
 *
 * @param {object} cfg 后端返回的推送配置
 * @param {string} [role] 调用方声明的收件人角色
 */
function pickTemplate(cfg, role) {
  if (!cfg) return ''
  const effective = role || cfg.role || currentRole()
  return effective === 'CHEF' ? cfg.newOrderTemplate : cfg.statusChangedTemplate
}

/** 切换家庭 / 重新登录后调用，强制下次重新拉配置 */
export function resetPushConfigCache() {
  configCache = null
  configAt = 0
}

/**
 * 直接拉起微信订阅面板。**必须在用户点击的处理链里同步调用。**
 *
 * @param {string[]} tmplIds 模板 ID 数组
 * @returns {Promise<{accept:number, reject:number, ban:number, skipped?:boolean, error?:string}>}
 */
export function requestSubscribe(tmplIds) {
  return new Promise((resolve) => {
    if (!isMp() || !tmplIds || !tmplIds.length) {
      resolve({ accept: 0, reject: 0, ban: 0, skipped: true })
      return
    }
    try {
      uni.requestSubscribeMessage({
        tmplIds,
        success: (res) => {
          const stat = { accept: 0, reject: 0, ban: 0 }
          tmplIds.forEach((id) => {
            const v = res[id]
            if (v === 'accept') stat.accept++
            else if (v === 'reject') stat.reject++
            else if (v === 'ban') stat.ban++
          })
          resolve(stat)
        },
        fail: (err) => {
          // 常见 fail：10001 参数/环境错误、20001 无订阅权限、20004 用户拒绝
          console.warn('[subscribe] requestSubscribeMessage fail', err)
          resolve({ accept: 0, reject: 0, ban: 1, error: (err && (err.errMsg || err.errCode)) || 'fail' })
        }
      })
    } catch (e) {
      console.warn('[subscribe] requestSubscribeMessage throw', e)
      resolve({ accept: 0, reject: 0, ban: 0, error: String(e) })
    }
  })
}

/**
 * 读微信设置页里的订阅开关。
 * 用户勾了「总是保持以上选择，不再询问」时，微信会把该模板的订阅状态写进「小程序设置 → 订阅消息」，
 * `getSetting({withSubscriptions:true})` 能读到 `itemSettings[tmplId] === 'accept'`。
 *
 * ⚠️ `acceptSwitch` 语义是「总是保持」这个**开关本身**，不是「已授权」。
 *    用户点过「允许」但没勾选时，itemSettings['主开关'] === 'accept' 只能说明
 *    「订阅消息的入口是开着的」，不能说明他勾了「总是保持」。
 *    所以真正的判据只能是 itemSettings[tmplId]（模板级）。等真机验证后再决定是否放宽。
 */
function isAlwaysAccepted(tmplId) {
  return new Promise((resolve) => {
    if (!isMp() || typeof uni.getSetting !== 'function') {
      resolve(false)
      return
    }
    try {
      uni.getSetting({
        withSubscriptions: true,
        success: (res) => {
          const s = res && res.subscriptionsSetting
          resolve(!!(s && s.itemSettings && s.itemSettings[tmplId] === 'accept'))
        },
        fail: () => resolve(false)
      })
    } catch (e) {
      resolve(false)
    }
  })
}

/** 单次操作最多累加多少条额度（微信服务端也是按条扣，这里只防手滑） */
const MAX_BULK = 20

// ======================= 各业务的额度目标（改这里，别在页面里写魔法数字） =======================

/** 主厨认领时一次性要的额度：认领是低频高价值动作，值得多要点 */
export const CHEF_QUOTA_ON_CLAIM = 10

/**
 * 主厨确认/驳回订单时补的额度。
 *
 * 为什么是 10：确认订单是主厨**每单都要做**的高频动作，是最稳定的补额时机。
 * 用户勾了「总是保持以上选择」时会静默累加到 10 条；没勾就只有 1 条（冷却期内也不会重复弹面板）。
 */
export const CHEF_QUOTA_ON_CONFIRM = 10

/** 「通知设置」手动补额要的条数 */
export const NOTIFY_TARGET = 5

/**
 * 两次订阅面板之间的最小间隔（只在用户**没勾**「总是保持以上选择」时才生效）。
 *
 * 主厨在「确认订单」页会连着点好几个菜，每次点击都符合微信的手势要求。
 * 如果他**没勾**「总是保持」，我们每次调 requestSubscribeMessage 都会真的弹出面板 →
 * 连点 5 个菜就是连弹 5 次面板，用户大概率一烦就点「拒绝」。
 * 所以这种情况下限流：{@link SUB_COOLDOWN} 内只弹一次（拿到 1 条），其余的静默跳过。
 *
 * 如果他**勾了**「总是保持」，微信不会再弹面板、直接沿用上次选择，累加是完全静默的，
 * 这时候限流只会白白少拿额度 —— 而「确认订单时多攒几次」正是我们要的效果，所以不限流。
 *
 * 代价要说清楚：没勾的用户因此只拿到 1 条而不是 target 条。这是我们主动选择的取舍
 * （少拿额度 < 连弹面板把用户逼到点拒绝），要改就调 {@link SUB_COOLDOWN}。
 */
const SUB_COOLDOWN = 3000

/** 每个模板上次弹面板的时间戳。按模板存：主厨和成员的模板不同，别互相压制。 */
const subAtOf = new Map()
/** 已确认「总是保持以上选择」的模板 ID */
const alwaysOkSet = new Set()

/** 该模板最近是否刚弹过面板（已确认「总是保持」的模板永远返回 false） */
function isCoolingDown(tmplId) {
  if (alwaysOkSet.has(tmplId)) return false // 静默累加，不限流
  const at = subAtOf.get(tmplId) || 0
  return at > 0 && Date.now() - at < SUB_COOLDOWN
}

/** 记录刚弹过面板 */
function markSubscribed(tmplId) {
  subAtOf.set(tmplId, Date.now())
}

/**
 * 累加订阅额度：把「一次授权只能发一条」这个限制，用**连续多次授权**补回来。
 *
 * ⚠️ 调用要求：必须在用户点击的处理链里**同步**调用（不要先 await 任何网络请求），
 *    否则第一次 requestSubscribeMessage 会丢掉用户手势、面板静默不弹。
 *    注意是「同步调用」，不是「同步等待」——异步 continue 不会重新弹面板，
 *    微信只认这个 Promise 首次被调用时所在的那次点击。
 *
 * 行为（四条规则，缺一不可）：
 *  1. 冷却期内（{@link SUB_COOLDOWN} 内刚弹过）→ 直接返回 0，不弹面板、不打扰；
 *  2. 第 1 次拉起面板，用户点「允许」→ +1；
 *  3. 若用户勾了「总是保持以上选择」→ 后续调用**不再弹面板**、直接返回 accept，可继续静默累加；
 *     没勾 → 只算这 1 次就收手，绝不连着弹第二次面板骚扰用户；
 *  4. 用户一旦拒绝/被封禁 → 立即停止，一次都不多问。
 *
 * @param {'CHEF'|'MEMBER'} role 收件人角色
 * @param {number} target 期望累加到的条数（用上面的 CHEF_QUOTA_* / NOTIFY_TARGET 常量，别写魔法数字）
 * @returns {Promise<number>} 实际拿到的条数（0 = 冷却中 / 配置没预热 / 用户拒绝，都不影响业务）
 */
export async function ensureSubscribeBulk(role, target = 1) {
  const cfg = configCache
  if (!cfg) {
    warmPushConfig(role || currentRole())
    return 0
  }
  const tmplId = pickTemplate(cfg, role)
  if (!validTemplateId(tmplId)) return 0
  if (isCoolingDown(tmplId)) return 0

  // 立刻占坑：主厨连点多个菜时，后面的调用必须看到这个时间戳并跳过
  markSubscribed(tmplId)

  const want = Math.max(1, Math.min(Math.floor(target) || 1, MAX_BULK))
  let accept = 0
  for (let i = 0; i < want; i++) {
    // 第 1 次由用户点击触发 → 弹面板；
    // 第 2 次起，只有确认用户开了「总是保持以上选择」才继续：微信的规则是勾了之后
    // 不再弹窗、直接沿用上次选择，所以这些调用是静默的；没勾的话再调就是**又弹一次面板**，
    // 属于骚扰用户。因此每轮都重新查一次设置，一旦不是 accept 立刻收手。
    if (i > 0) {
      const always = await isAlwaysAccepted(tmplId)
      if (!always) break
      alwaysOkSet.add(tmplId) // 记下这次结果，后续调用可跳过冷却、直接静默累加
    }
    const stat = await requestSubscribe([tmplId])
    if (stat.accept <= 0) break // 拒绝 / 封禁 → 收手
    accept += stat.accept
  }

  if (accept > 0) {
    reportSubscribeGrant(tmplId, accept, role || cfg.role || currentRole()).then((r) => {
      if (r && typeof r.remain === 'number') cfg.remaining = r.remain
    })
  }
  return accept
}

/**
 * 关键操作前调用：顺带拿一次订阅授权。
 *
 * @param {'CHEF'|'MEMBER'} role 收件人角色，决定用哪个模板
 * @returns {boolean} 是否发起了授权（配置未预热时为 false，静默跳过，不影响业务）
 *
 * 用法（不要 await：授权是「顺带」做的，业务照常执行）：
 * ```js
 * const confirmCheckout = async () => {
 *   ensureSubscribe('MEMBER')
 *   await checkoutOrders(ids)
 * }
 * ```
 */
export function ensureSubscribe(role) {
  const cfg = configCache
  if (!cfg) {
    // 没预热成功（H5 / 未登录 / 后端未配模板）→ 安静跳过，绝不打扰用户
    warmPushConfig(role || currentRole())
    return false
  }
  const tmplId = pickTemplate(cfg, role)
  if (!validTemplateId(tmplId)) return false

  requestSubscribe([tmplId]).then((stat) => {
    if (stat.accept > 0) {
      // 微信一次性订阅：允许一次 = 服务端可发一条，如实上报
      reportSubscribeGrant(tmplId, stat.accept, role || cfg.role || currentRole()).then((r) => {
        if (r && typeof r.remain === 'number') cfg.remaining = r.remain
      })
    }
  })
  return true
}
