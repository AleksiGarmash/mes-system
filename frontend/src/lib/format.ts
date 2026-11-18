const numberFormat = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 3 })
const dateFormat = new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' })
const dateTimeFormat = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

/** Число в русской записи (запятая, неразрывные пробелы между разрядами); null → тире. */
export function num(value: number | null | undefined, digits?: number): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  if (digits !== undefined) {
    return new Intl.NumberFormat('ru-RU', { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(value)
  }
  return numberFormat.format(value)
}

export function date(iso: string | null | undefined): string {
  if (!iso) return '—'
  return dateFormat.format(new Date(iso))
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return dateTimeFormat.format(new Date(iso))
}

/**
 * Разбор числа, введённого человеком: принимает запятую и точку, пробелы между разрядами.
 * Пустая строка или мусор → null.
 */
export function parseNumber(input: string): number | null {
  const cleaned = input.replace(/[\s ]/g, '').replace(',', '.')
  if (cleaned === '' || !/^-?\d*(\.\d+)?$/.test(cleaned) || cleaned === '-' || cleaned === '.') return null
  const value = Number(cleaned)
  return Number.isFinite(value) ? value : null
}

/** Диапазон допуска для подписи: «10 – 10,5», «≥ 9», «≤ 11». */
export function range(min: number | null, max: number | null, unit?: string | null): string {
  const u = unit ? ` ${unit}` : ''
  if (min !== null && max !== null) return `${num(min)} – ${num(max)}${u}`
  if (min !== null) return `≥ ${num(min)}${u}`
  if (max !== null) return `≤ ${num(max)}${u}`
  return '—'
}
