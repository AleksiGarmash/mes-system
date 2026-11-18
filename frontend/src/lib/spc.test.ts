import { analyze } from './spc'

// Те же примеры, что в серверном SpcCalculatorTest: клиент и сервер считают одинаково.
const alternating = (n: number, a: number, b: number) => Array.from({ length: n }, (_, i) => (i % 2 === 0 ? a : b))

describe('SPC: карта индивидуальных значений', () => {
  it('базовая статистика совпадает с ручным расчётом', () => {
    const r = analyze([1, 2, 3, 4, 5], null, null)
    expect(r.mean).toBeCloseTo(3)
    expect(r.stdDev).toBeCloseTo(Math.sqrt(2.5))
    expect(r.sigmaWithin).toBeCloseTo(1 / 1.128)
    expect(r.ucl).toBeCloseTo(3 + 3 / 1.128)
    expect(r.sufficientData).toBe(false)
    expect(r.cp).toBeNull()
  })

  it('Cp = Cpk для центрированного процесса', () => {
    const r = analyze(alternating(30, 9.9, 10.1), 9, 11)
    const sigma = 0.2 / 1.128
    expect(r.cp).toBeCloseTo(2 / (6 * sigma))
    expect(r.cpk).toBeCloseTo(r.cp!)
  })

  it('смещение среднего снижает Cpk', () => {
    const r = analyze(alternating(30, 10.4, 10.6), 9, 11)
    expect(r.cpk!).toBeLessThan(r.cp!)
  })

  it('одна граница даёт Cpk без Cp', () => {
    const r = analyze(alternating(30, 9.9, 10.1), null, 11)
    expect(r.cp).toBeNull()
    expect(r.cpk).toBeGreaterThan(0)
  })

  it('выброс — сигнал WE1 на нужной точке', () => {
    const r = analyze([...alternating(30, 10, 10.2), 11], null, null)
    expect(r.signals.some((s) => s.rule === 'WE1' && s.index === 30)).toBe(true)
  })

  it('восемь точек по одну сторону — сигнал WE4', () => {
    const data = [...alternating(20, 9.9, 10.1), ...Array.from({ length: 8 }, (_, i) => 10.15 + (i % 2) * 0.02)]
    expect(analyze(data, null, null).signals.some((s) => s.rule === 'WE4')).toBe(true)
  })

  it('стабильный процесс без сигналов, постоянный ряд без σ', () => {
    expect(analyze(alternating(30, 9.9, 10.1), 9, 11).signals).toEqual([])
    const flat = analyze(Array(9).fill(5), 4, 6)
    expect(flat.sigmaWithin).toBe(0)
    expect(flat.cpk).toBeNull()
  })

  it('пустой ряд — ошибка', () => {
    expect(() => analyze([], null, null)).toThrow()
  })
})
