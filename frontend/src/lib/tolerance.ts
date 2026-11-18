/**
 * Те же правила, что на сервере (DosageValidator, RangeValidator). На клиенте они нужны,
 * чтобы оператор сразу видел, попадает ли исправленное значение в допуск; окончательное
 * решение всё равно принимает сервер при подтверждении.
 */

/** Границы допуска навески: допуск задан в процентах от цели, отдельно вниз и вверх. */
export function dosageBounds(target: number, plusPct: number, minusPct: number): { min: number; max: number } {
  return {
    min: target - (target * minusPct) / 100,
    max: target + (target * plusPct) / 100,
  }
}

/** Навеска в допуске (границы включены). Сравнение с запасом на погрешность чисел с плавающей точкой. */
export function isDosageWithin(target: number, actual: number, plusPct: number, minusPct: number): boolean {
  const { min, max } = dosageBounds(target, plusPct, minusPct)
  const eps = 1e-9 * Math.max(1, Math.abs(target))
  return actual >= min - eps && actual <= max + eps
}

/** Значение в диапазоне; отсутствующая граница означает «без ограничения с этой стороны». */
export function isWithinRange(value: number, min: number | null, max: number | null): boolean {
  if (min !== null && value < min) return false
  if (max !== null && value > max) return false
  return true
}

/** Отклонение от цели в процентах (со знаком). */
export function deviationPct(target: number, actual: number): number {
  if (target === 0) return 0
  return ((actual - target) / target) * 100
}
