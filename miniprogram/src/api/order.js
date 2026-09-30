import { request } from '@/utils/request'

export const createOrder = (data) =>
  request({ url: '/api/orders', method: 'POST', data })

export const getMyOrders = () =>
  request({ url: '/api/orders' })

export const getOrderDetail = (id) =>
  request({ url: `/api/orders/${id}` })