import { useMemo, useState } from 'react'
import type { SpcResult } from '../api/types'
import { dateTime, num } from '../lib/format'

const W = 760
const H = 340
const M = { top: 18, right: 120, bottom: 34, left: 52 }

interface RefLine {
  key: string
  y: number
  label: string
  kind: 'center' | 'control' | 'spec'
}

/** Разводит подписи линий по вертикали, чтобы близкие линии (UCL и верхний допуск) не налезали. */
export function spreadLabels(ys: number[], minGap: number): number[] {
  const order = ys.map((y, i) => ({ y, i })).sort((a, b) => a.y - b.y)
  const placed: number[] = []
  for (const item of order) {
    const prev = placed.length ? placed[placed.length - 1] : -Infinity
    placed.push(Math.max(item.y, prev + minGap))
  }
  const result = new Array<number>(ys.length)
  order.forEach((item, k) => (result[item.i] = placed[k]))
  return result
}

/**
 * Карта индивидуальных значений. Одна серия (синяя), контрольные границы пунктиром,
 * границы допуска сплошной линией, подписи прямо у линий. Точка вне допуска — красная с кольцом,
 * точка с сигналом нестабильности — с восклицательным кольцом; цвет никогда не единственный признак.
 */
export function ControlChart({ data }: { data: SpcResult }) {
  const [hover, setHover] = useState<number | null>(null)
  const { points, report, lsl, usl } = data
  const signalIdx = useMemo(() => new Set(report.signals.map((s) => s.index)), [report.signals])

  const values = points.map((p) => p.value)
  const candidates = [...values, report.ucl, report.lcl, ...(lsl !== null ? [lsl] : []), ...(usl !== null ? [usl] : [])]
  const lo = Math.min(...candidates)
  const hi = Math.max(...candidates)
  const pad = (hi - lo) * 0.08 || 1
  const y0 = lo - pad
  const y1 = hi + pad
  const plotW = W - M.left - M.right
  const plotH = H - M.top - M.bottom
  const x = (i: number) => M.left + (points.length <= 1 ? plotW / 2 : (i / (points.length - 1)) * plotW)
  const y = (v: number) => M.top + (1 - (v - y0) / (y1 - y0)) * plotH

  const refs: RefLine[] = [
    { key: 'cl', y: report.centerLine, label: `среднее ${num(report.centerLine, 2)}`, kind: 'center' },
    { key: 'ucl', y: report.ucl, label: `UCL ${num(report.ucl, 2)}`, kind: 'control' },
    { key: 'lcl', y: report.lcl, label: `LCL ${num(report.lcl, 2)}`, kind: 'control' },
  ]
  if (usl !== null) refs.push({ key: 'usl', y: usl, label: `допуск ≤ ${num(usl)}`, kind: 'spec' })
  if (lsl !== null) refs.push({ key: 'lsl', y: lsl, label: `допуск ≥ ${num(lsl)}`, kind: 'spec' })
  const labelY = spreadLabels(refs.map((r) => y(r.y)), 15)

  const ticks = Array.from({ length: 5 }, (_, k) => y0 + ((y1 - y0) * k) / 4)
  const path = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
  const hovered = hover !== null ? points[hover] : null

  return (
    <div className="chart-box">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Контрольная карта: ${data.parameter}, ${points.length} измерений`}>
        {ticks.map((t) => (
          <g key={t}>
            <line x1={M.left} x2={M.left + plotW} y1={y(t)} y2={y(t)} stroke="var(--rule-soft)" strokeWidth={1} />
            <text x={M.left - 8} y={y(t)} textAnchor="end" dominantBaseline="middle" fontSize={11} fill="var(--ink-3)">
              {num(t, 2)}
            </text>
          </g>
        ))}

        {refs.map((r, k) => (
          <g key={r.key}>
            <line
              x1={M.left}
              x2={M.left + plotW}
              y1={y(r.y)}
              y2={y(r.y)}
              stroke={r.kind === 'spec' ? 'var(--ink)' : 'var(--ink-3)'}
              strokeWidth={r.kind === 'spec' ? 1.5 : 1}
              strokeDasharray={r.kind === 'control' ? '5 4' : undefined}
            />
            <text x={M.left + plotW + 8} y={labelY[k]} dominantBaseline="middle" fontSize={11} fill={r.kind === 'spec' ? 'var(--ink)' : 'var(--ink-2)'}>
              {r.label}
            </text>
          </g>
        ))}

        <path d={path} fill="none" stroke="var(--series)" strokeWidth={2} strokeLinejoin="round" />

        {hover !== null && (
          <line x1={x(hover)} x2={x(hover)} y1={M.top} y2={M.top + plotH} stroke="var(--ink-3)" strokeWidth={1} />
        )}

        {points.map((p, i) => {
          const flagged = p.outOfSpec || signalIdx.has(i)
          const color = p.outOfSpec ? 'var(--bad)' : signalIdx.has(i) ? 'var(--warn)' : 'var(--series)'
          return (
            <g key={i}>
              {flagged && <circle cx={x(i)} cy={y(p.value)} r={8} fill="none" stroke={color} strokeWidth={2} />}
              <circle cx={x(i)} cy={y(p.value)} r={hover === i ? 5.5 : 4} fill={color} stroke="var(--surface)" strokeWidth={2} />
            </g>
          )
        })}

        {/* зоны наведения шире точек */}
        {points.map((_, i) => (
          <rect
            key={`hit-${i}`}
            x={x(i) - plotW / Math.max(points.length - 1, 1) / 2}
            y={M.top}
            width={plotW / Math.max(points.length - 1, 1)}
            height={plotH}
            fill="transparent"
            onMouseEnter={() => setHover(i)}
            onMouseLeave={() => setHover(null)}
          />
        ))}

        <text x={M.left} y={H - 8} fontSize={11} fill="var(--ink-3)">
          {dateTime(points[0]?.at)}
        </text>
        <text x={M.left + plotW} y={H - 8} fontSize={11} fill="var(--ink-3)" textAnchor="end">
          {dateTime(points[points.length - 1]?.at)}
        </text>
      </svg>
      {hovered && hover !== null && (
        <div className="chart-tip" style={{ left: `${(x(hover) / W) * 100}%`, top: `${(y(hovered.value) / H) * 100}%` }}>
          <b>{num(hovered.value, 3)}</b> · {hovered.label ?? '—'}
          <br />
          {dateTime(hovered.at)}
          {hovered.outOfSpec && <><br />вне допуска</>}
          {signalIdx.has(hover) && <><br />сигнал нестабильности</>}
        </div>
      )}
    </div>
  )
}
