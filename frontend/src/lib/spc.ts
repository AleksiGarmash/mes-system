import type { SpcResult } from '../api/types'

/**
 * Порт серверного SpcCalculator (карта индивидуальных значений I-MR) для демо-режима.
 * σ внутри процесса = MR̄ / 1,128; Cp/Cpk по нему, Ppk по общему стандартному отклонению;
 * правила Western Electric WE1–WE4. Тесты сверяют результат с теми же примерами, что и на сервере.
 */
const D2 = 1.128
export const MIN_RELIABLE_POINTS = 20

type Report = SpcResult['report']
type Signal = Report['signals'][number]

export function analyze(values: number[], lsl: number | null, usl: number | null): Report {
  if (values.length === 0) throw new Error('Нет данных для анализа')
  const n = values.length
  const mean = values.reduce((a, b) => a + b, 0) / n
  const stdDev = n < 2 ? 0 : Math.sqrt(values.reduce((acc, v) => acc + (v - mean) ** 2, 0) / (n - 1))

  let sigmaWithin = 0
  if (n >= 2) {
    let sum = 0
    for (let i = 1; i < n; i++) sum += Math.abs(values[i] - values[i - 1])
    sigmaWithin = sum / (n - 1) / D2
  }

  const cp = lsl !== null && usl !== null && sigmaWithin > 0 ? (usl - lsl) / (6 * sigmaWithin) : null
  const signals = sigmaWithin > 0 ? detect(values, mean, sigmaWithin) : []

  return {
    n,
    mean,
    stdDev,
    sigmaWithin,
    centerLine: mean,
    ucl: mean + 3 * sigmaWithin,
    lcl: mean - 3 * sigmaWithin,
    cp,
    cpk: capability(mean, sigmaWithin, lsl, usl),
    ppk: capability(mean, stdDev, lsl, usl),
    sufficientData: n >= MIN_RELIABLE_POINTS,
    signals,
  }
}

function capability(mean: number, sigma: number, lsl: number | null, usl: number | null): number | null {
  if (sigma <= 0 || (lsl === null && usl === null)) return null
  const upper = usl !== null ? (usl - mean) / (3 * sigma) : Infinity
  const lower = lsl !== null ? (mean - lsl) / (3 * sigma) : Infinity
  return Math.min(upper, lower)
}

function detect(v: number[], mean: number, sigma: number): Signal[] {
  const signals: Signal[] = []
  const side = (x: number) => Math.sign(x - mean)

  const countBeyond = (from: number, to: number, k: number) => {
    const lastSide = side(v[to])
    let count = 0
    for (let i = from; i <= to; i++) {
      if (side(v[i]) === lastSide && Math.abs(v[i] - mean) > k * sigma) count++
    }
    return count
  }

  for (let i = 0; i < v.length; i++) {
    const z = (v[i] - mean) / sigma
    if (Math.abs(z) > 3) signals.push({ rule: 'WE1', index: i, description: 'Точка за границами 3σ' })
    if (i >= 2 && countBeyond(i - 2, i, 2) >= 2 && Math.abs(v[i] - mean) > 2 * sigma) {
      signals.push({ rule: 'WE2', index: i, description: '2 из 3 последних точек за 2σ по одну сторону' })
    }
    if (i >= 4 && countBeyond(i - 4, i, 1) >= 4 && Math.abs(v[i] - mean) > sigma) {
      signals.push({ rule: 'WE3', index: i, description: '4 из 5 последних точек за 1σ по одну сторону' })
    }
    if (i >= 7) {
      const s = side(v[i - 7])
      let all = s !== 0
      for (let j = i - 7; j <= i && all; j++) all = side(v[j]) === s
      if (all) {
        signals.push({
          rule: 'WE4',
          index: i,
          description: '8 последних точек по одну сторону от центральной линии (сдвиг среднего)',
        })
      }
    }
  }
  return signals
}
