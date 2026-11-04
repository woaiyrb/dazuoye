import type { PageResult } from '@/types/models'

export interface PageLoader<T> {
  (page: number, pageSize: number): Promise<PageResult<T>>
}

export interface AllPagesResult<T> {
  items: T[]
  total: number
  truncated: boolean
}

/**
 * 一次性拉取全部页，供仍依赖完整列表做本地筛选/计数的页面使用。
 * 设置上限是为了避免异常数据造成无限翻页；超出时由页面展示截断提示。
 */
export async function fetchAllPages<T>(
  loader: PageLoader<T>,
  pageSize = 100,
  maxPages = 20,
): Promise<AllPagesResult<T>> {
  const first = await loader(1, pageSize)
  const size = Math.max(1, first.pageSize)
  const totalPages = Math.max(1, Math.ceil(first.total / size))
  const cappedPages = Math.min(totalPages, maxPages)
  const items = [...first.items]

  for (let page = 2; page <= cappedPages; page += 1) {
    const next = await loader(page, size)
    if (!next.items.length) break
    items.push(...next.items)
  }

  return {
    items,
    total: first.total,
    truncated: items.length < first.total,
  }
}
