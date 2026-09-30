import { request } from '@/utils/request'

export const getCategories = () =>
  request({ url: '/api/categories' })

export const getProducts = (categoryId) =>
  request({ url: `/api/products${categoryId ? '?categoryId=' + categoryId : ''}` })

export const getProduct = (id) =>
  request({ url: `/api/products/${id}` })