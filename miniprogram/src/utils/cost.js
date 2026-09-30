/**
 * 公共菜"参考成本"计算工具
 *
 * 数据来源：
 *   - 后端 PublicDishIngredientVO 数组（每项含 amount + unit + unitPrice）
 *
 * 重要：本工具**不做单位换算**，因为配菜库不知道"1 把韭菜是多少克"。
 *   - 克/g 单位：amount × price（精确）
 *   - 个/根/颗/勺 等单位：amount × price（**估算**）
 *   - 单位不一致或价格为 0 时：跳过该条 + 计入警告
 *
 * 返回：
 *   { cost, isEstimate, warning, display }
 *
 * 用途：
 *   - 公共菜详情页：底部"约 ¥X.XX"
 *   - 批量加入菜单预览：合计"约 ¥XX.XX"
 *   - 今日菜单页：参考成本（灰色小字）
 */

/** 单位换算提示表（不精确，标识"估算"用） */
const COUNT_LIKE_UNITS = ['个', '根', '颗', '只', '张', '片', '块', '盒', '把', '条', '杯', '勺']
const MASS_UNITS = ['克', 'g', 'G', 'kg', '千克', '斤']

/** 算单道菜的参考成本 */
export function calcDishCost(ingredients) {
  if (!Array.isArray(ingredients) || ingredients.length === 0) {
    return { cost: 0, isEstimate: true, warning: '无配方数据', display: '¥0.00', hasRecipe: false }
  }

  let total = 0
  let hasEstimate = false
  let hasPriceMissing = false

  for (const ing of ingredients) {
    const price = Number(ing.unitPrice) || 0
    const amount = Number(ing.amount) || 0
    const unit = (ing.unit || '').trim()

    if (price <= 0 || amount <= 0) {
      hasPriceMissing = true
      continue
    }
    if (MASS_UNITS.includes(unit)) {
      // 精确（克×元/克）
      total += amount * price
    } else if (COUNT_LIKE_UNITS.includes(unit)) {
      // 估算（个/根/颗/勺 × 元/单位）
      total += amount * price
      hasEstimate = true
    } else {
      // 未知单位，按"估算"算
      total += amount * price
      hasEstimate = true
    }
  }

  const cost = Math.round(total * 100) / 100
  let warning = ''
  if (hasPriceMissing) warning += '部分配菜缺单价；'
  if (hasEstimate) warning += '非克单位，估算值；'

  return {
    cost,
    isEstimate: hasEstimate || hasPriceMissing,
    warning: warning.trim(),
    hasRecipe: true,
    // 前端展示文案
    display: `约 ¥${cost.toFixed(2)}`,
    displayExact: `¥${cost.toFixed(2)}`,
  }
}

/** 算多道菜的合计 */
export function calcTotalCost(dishesWithIngredients) {
  if (!Array.isArray(dishesWithIngredients) || dishesWithIngredients.length === 0) {
    return { totalCost: 0, hasEstimate: true, display: '¥0.00', perDish: [] }
  }
  let totalCost = 0
  let anyEstimate = false
  const perDish = []
  for (const dish of dishesWithIngredients) {
    const r = calcDishCost(dish.ingredients || [])
    totalCost += r.cost
    if (r.isEstimate) anyEstimate = true
    perDish.push({ id: dish.id, name: dish.name, ...r })
  }
  return {
    totalCost: Math.round(totalCost * 100) / 100,
    hasEstimate: anyEstimate,
    display: `约 ¥${totalCost.toFixed(2)}`,
    perDish,
  }
}