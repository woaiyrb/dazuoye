const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const fullDateFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

const dayFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
})

const weekdayFormatter = new Intl.DateTimeFormat('zh-CN', { weekday: 'long' })

function toDate(input: string | Date | null | undefined): Date | null {
  if (!input) return null
  const date = input instanceof Date ? input : new Date(input)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(input: string | Date | null | undefined): string {
  const date = toDate(input)
  return date ? dateFormatter.format(date).replace(/\//g, '-') : '—'
}

export function formatFullDateTime(input: string | Date | null | undefined): string {
  const date = toDate(input)
  return date ? fullDateFormatter.format(date).replace(/\//g, '-') : '—'
}

export function formatDay(input: string | Date | null | undefined): string {
  const date = toDate(input)
  return date ? dayFormatter.format(date) : '—'
}

export function formatWeekday(input: string | Date | null | undefined): string {
  const date = toDate(input)
  return date ? weekdayFormatter.format(date) : '—'
}

export function formatRelative(input: string | Date | null | undefined): string {
  const date = toDate(input)
  if (!date) return '—'
  const diff = Date.now() - date.getTime()
  const min = Math.floor(diff / 60_000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day} 天前`
  return formatDateTime(date)
}

export function pct(value: number | null | undefined, digits = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return `${value.toFixed(digits)}%`
}

export function percentOf(part: number, total: number): number {
  if (!total) return 0
  return Math.round((part / total) * 10_000) / 100
}

export function toIsoDate(value: string): string | null {
  if (!value) return null
  const date = new Date(`${value}T18:00:00+08:00`)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

export function toDateInput(iso: string | null | undefined): string {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 10)
}

export function truncate(text: string, length = 60): string {
  return text.length > length ? `${text.slice(0, length)}…` : text
}

export function answerLabel(answer: string | null | undefined, type?: string): string {
  if (!answer) return '未作答'
  if (answer === 'TRUE' || answer === 'FALSE') {
    return answer === 'TRUE' ? '正确' : '错误'
  }
  return type === 'TRUE_FALSE' ? answer : `${answer}`
}
