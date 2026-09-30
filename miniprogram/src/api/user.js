import { request } from '@/utils/request'

export function login(code) {
  return request({
    url: '/api/wx/login',
    method: 'POST',
    data: { code }
  }).then(res => {
    uni.setStorageSync('token', res.token)
    uni.setStorageSync('userInfo', {
      userId: res.userId,
      nickname: res.nickname,
      isChef: res.isChef,
      families: res.families || [],
      activeFamilyId: res.activeFamilyId || null
    })
    return res
  })
}