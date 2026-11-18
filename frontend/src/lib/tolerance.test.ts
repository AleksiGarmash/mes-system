import { deviationPct, dosageBounds, isDosageWithin, isWithinRange } from './tolerance'

describe('допуск навески (как DosageValidator на сервере)', () => {
  it('считает границы от цели в процентах, отдельно вверх и вниз', () => {
    expect(dosageBounds(100, 3, 2)).toEqual({ min: 98, max: 103 })
  })
  it('включает границы', () => {
    expect(isDosageWithin(100, 103, 3, 2)).toBe(true)
    expect(isDosageWithin(100, 98, 3, 2)).toBe(true)
  })
  it('допуск только вверх: меньше цели — вне допуска', () => {
    expect(isDosageWithin(90, 89.9, 2, 0)).toBe(false)
    expect(isDosageWithin(90, 91.8, 2, 0)).toBe(true)
    expect(isDosageWithin(90, 91.9, 2, 0)).toBe(false)
  })
  it('не ломается на погрешности плавающей точки', () => {
    expect(isDosageWithin(0.3, 0.1 + 0.2, 0, 0)).toBe(true)
  })
})

describe('диапазон (как RangeValidator)', () => {
  it('обе границы', () => {
    expect(isWithinRange(4.2, 3.5, 4.5)).toBe(true)
    expect(isWithinRange(3.4, 3.5, 4.5)).toBe(false)
  })
  it('одна граница', () => {
    expect(isWithinRange(1000, 10, null)).toBe(true)
    expect(isWithinRange(-5, null, 10)).toBe(true)
  })
})

it('отклонение в процентах со знаком', () => {
  expect(deviationPct(200, 210)).toBeCloseTo(5)
  expect(deviationPct(0, 5)).toBe(0)
})
