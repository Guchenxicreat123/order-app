// 简单的购物车管理（替代 Pinia）
// 使用全局单例保证跨页面数据一致
const CART_KEY = 'cart_items'

let _cache = null

export function getCart() {
  if (_cache !== null) return _cache
  try {
    _cache = uni.getStorageSync(CART_KEY) || []
  } catch (e) {
    _cache = []
  }
  return _cache
}

function _save(items) {
  _cache = items
  try {
    uni.setStorageSync(CART_KEY, items)
  } catch (e) {
    console.error('cart save failed', e)
  }
}

export function addToCart(product) {
  const items = getCart()
  const exist = items.find(i => i.productId === product.id)
  if (exist) {
    exist.quantity += 1
  } else {
    items.push({
      productId: product.id,
      name: product.name,
      price: product.price,
      imageUrl: product.imageUrl || '',
      quantity: 1
    })
  }
  _save([...items]) // 触发更新
  return items
}

export function updateQuantity(productId, quantity) {
  const items = getCart()
  const it = items.find(i => i.productId === productId)
  if (it) {
    if (quantity <= 0) {
      it.quantity = 0
    } else {
      it.quantity = quantity
    }
  }
  _save(items.filter(i => i.quantity > 0))
  return getCart()
}

export function removeFromCart(productId) {
  _save(getCart().filter(i => i.productId !== productId))
  return getCart()
}

export function clearCart() {
  _cache = []
  uni.removeStorageSync(CART_KEY)
}

export function getCartTotal() {
  const items = getCart()
  return {
    count: items.reduce((s, i) => s + i.quantity, 0),
    amount: items.reduce((s, i) => s + i.price * i.quantity, 0).toFixed(2)
  }
}
