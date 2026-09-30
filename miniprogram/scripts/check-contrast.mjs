#!/usr/bin/env node
/**
 * 点9：亮色主题对比度巡检（CI 可挂）
 * ------------------------------------------------------------------
 * 背景：粉色渐变是「亮色主题」，最大的坑就是浅底上写白字 / 低透明文字。
 *      本项目实测过：纯白 on #FF7B94 只有 2.47:1，rgba(122,74,90,.48) 只有 2.22:1。
 *      靠人眼 review 一定会漏，所以固化成脚本。
 *
 * 用法（在 miniprogram/ 下）：
 *     npm run check:contrast
 *     退出码 0 = 通过；1 = 有违规（可挂 pre-commit / CI）
 *
 * 检查三件事（只在「已知浅色底」的规则上判定，避免误报）：
 *   1) .text-on-light / 浅色卡片语境里出现纯白文字        → 必错（白 on 浅 = 隐形）
 *   2) 用作 color 的 #rrggbb 与最常见的浅底对比度 < 3:1   → 报错
 *   3) 用作 color 的 rgba(122,74,90,α) 中 α < 0.62       → 告警（AA-large 边界）
 *
 * 白名单：命中即跳过（深底/语义底上的白字是合法的）。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const ROOT = `file://${new URL('..', import.meta.url).pathname}`.includes('%')
  // 路径含非 ASCII / 被 URL 编码：用 decodeURI 还原，规避 readsync 拿不到编码路径
  ? decodeURIComponent(new URL('..', import.meta.url).pathname)
  : new URL('..', import.meta.url).pathname
const SRC = join(ROOT, 'src')

/* ---------------- 颜色工具 ---------------- */
const lin = (c) => { c /= 255; return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4 }
const lum = ([r, g, b]) => 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((m, n) => n - m); return (x + 0.05) / (y + 0.05) }

const hex2rgb = (h) => {
  h = h.replace('#', '')
  if (h.length === 3) h = [...h].map((c) => c + c).join('')
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16))
}
const overOn = (fg, alpha, bg) => fg.map((c, i) => Math.round(alpha * c + (1 - alpha) * bg[i]))

/* 主题浅色底（来自 App.vue 的 token，改色请同步这里） */
const LIGHT_BASES = {
  '#FFFFFF': hex2rgb('#FFFFFF'),   // 实白卡
  '#FDF8F9': hex2rgb('#FDF8F9'),   // 页面兜底
  '#DAFFFB': hex2rgb('#DAFFFB'),   // 薄荷白
  '#EDFFFF': hex2rgb('#EDFFFF'),
  '#FFF0F3': hex2rgb('#FFF0F3'),   // 语义 info 底
  '#FFE7EC': hex2rgb('#FFE7EC'),
  '#E3F7EE': hex2rgb('#E3F7EE'),
}
/* 深色/实色底 —— 这些底上的白字合法，命中即跳过整条规则 */
const DARK_OK_SELECTORS = [
  /\.text-on-dark/, /\.btn-primary/, /\.btn-chef/, /\.btn-danger/,
  /\.st-1/, /\.st--1/, /\.gradient-pri/, /\.gradient-chef/,
  /cart-badge/, /menu-badge/, /fc-active/, /dish-status/,
  /entry-/,   // index 入口卡：底由 .entry-menu(实色珊瑚粉渐变) / .entry-custom(浅) 决定，见下方专项规则
  /\.pill-mirror/, /\.pill-solid/, /\.tag-chef/, /\.layer-3-bar/,
  /\.price--on-dark/, /.price--on-dark/,
]
const DARK_OK_BG = /(--g-primary|--g-chef|--g-warm|#FF7B94|#FF5D7E|#22B573|#E5484D|#B4637A|#F5A623|rgba\(255,123,148)/i
// 以下按钮是「实色语义底」上的白字，合法
const DARK_OK_BTN = new Set(["is-danger", "is-warn", "is-chef"])

/* ---------------- 收集文件 ---------------- */
function walk(dir, out = []) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e)
    if (statSync(p).isDirectory()) walk(p, out)
    else if (/\.(vue|scss|css)$/.test(e)) out.push(p)
  }
  return out
}
const files = walk(SRC)

/* ---------------- 逐规则检查 ---------------- */
const errors = []
const warns = []
let checkedRules = 0

for (const file of files) {
  const text = readFileSync(file, 'utf8')
  // 去掉 <template> 部分，只查样式（模板里的 placeholder-style 单独查）
  const styleBlocks = [...text.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map((m) => m[1])
  const scss = styleBlocks.length ? styleBlocks.join('\n') : text

  const rules = [...scss.matchAll(/([^{}]+)\{([^{}]*)\}/g)]
  for (const m of rules) {
    const sel = m[1].replace(/\s+/g, ' ').trim().slice(-70)
    const body = m[2]
    checkedRules++

    // 实色语义底按钮（.is-danger/.is-warn 等）白字合法：其 background 在同一 {} 内声明
    const isSolidBtn = DARK_OK_BG.test(body) || /is-(danger|warn|chef)/.test(sel)
    const isDarkOk = DARK_OK_SELECTORS.some((r) => r.test(sel)) || isSolidBtn

    // ---- 1) color 的十六进制 / 白色 ----
    const colorDecl = body.match(/(?:^|[;{\s])color\s*:\s*([^;}!]+)/)
    if (colorDecl) {
      const val = colorDecl[1].trim()
      if (/^(#fff(f{0,3})?|white)$/i.test(val)) {
        if (!isDarkOk) {
          errors.push(`${relative(ROOT, file)}  «${sel}»  \`color:${val}\` 纯白文字但底不是深色（白 on 浅≈1.0:1，隐形）`)
        }
      } else if (/^#[0-9a-f]{3,8}$/i.test(val)) {
        const rgb = hex2rgb(val)
        for (const [name, bg] of Object.entries(LIGHT_BASES)) {
          const r = ratio(rgb, bg)
          if (r < 3) {
            warns.push(`${relative(ROOT, file)}  «${sel}»  \`color:${val}\` on ${name} 仅 ${r.toFixed(2)}:1 (<3:1)`)
            break
          }
        }
      }
      // ---- 3) rgba(122,74,90,α) 低透明度 ----
      const am = val.match(/rgba\(\s*122\s*,\s*74\s*,\s*90\s*,\s*(0?\.\d+|1)\s*\)/)
      if (am && parseFloat(am[1]) < 0.62) {
        warns.push(`${relative(ROOT, file)}  «${sel}»  \`color:${val}\` 透明度过低（<0.62，实测约 ${ratio(overOn(hex2rgb('#7A4A5A'), parseFloat(am[1]), [255, 255, 255]), [255, 255, 255]).toFixed(2)}:1）`)
      }
    }

    // ---- 4) 黑/灰投影禁令 ----
    if (/box-shadow[^;]*rgba\(\s*0\s*,\s*0\s*,\s*0/.test(body) && !/mask-image/.test(body)) {
      errors.push(`${relative(ROOT, file)}  «${sel}» 使用黑色投影（粉色主题规范：一律用粉色同色光晕）`)
    }
  }

  // 模板中的 placeholder-style 低对比度
  for (const m of text.matchAll(/placeholder-style="([^"]*)"/g)) {
    const am = m[1].match(/rgba\(\s*122\s*,\s*74\s*,\s*90\s*,\s*(0?\.\d+)\s*\)/)
    if (am && parseFloat(am[1]) < 0.55) {
      warns.push(`${relative(ROOT, file)}  placeholder-style 透明度 ${am[1]} 偏低（建议 ≥0.60）`)
    }
  }
}

/* ---------------- 报告 ---------------- */
const Y = (s) => `\x1b[33m${s}\x1b[0m`
const R = (s) => `\x1b[31m${s}\x1b[0m`
const G = (s) => `\x1b[32m${s}\x1b[0m`

console.log(`\n对比度巡检 · 扫描 ${files.length} 个样式文件 / ${checkedRules} 条规则\n`)
if (warns.length) {
  console.log(Y(`⚠ ${warns.length} 条告警（不阻塞构建，建议修）`))
  warns.forEach((w) => console.log('  ' + w))
  console.log()
}
if (errors.length) {
  console.log(R(`✗ ${errors.length} 条错误（阻塞）`))
  errors.forEach((e) => console.log('  ' + e))
  console.log()
  process.exit(1)
}
console.log(G('✓ 无对比度错误') + (warns.length ? Y('（仍有告警）') : '') + '\n')
