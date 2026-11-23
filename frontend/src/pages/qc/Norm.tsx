import type { QualitySpecItem } from '../../api/types'
import { num } from '../../lib/format'
import { allowedValues, normText } from '../../lib/qcReview'

/** Норма параметра: диапазон одной строкой, допустимые значения — каждое своей строкой. */
export function Norm({ spec }: { spec: QualitySpecItem | undefined }) {
  if (spec?.parameterType === 'QUALITATIVE') {
    const values = allowedValues(spec)
    return values.length ? (
      <ul className="norm-values" aria-label="Допустимые значения">
        {values.map((v) => (
          <li key={v}>{v}</li>
        ))}
      </ul>
    ) : (
      <>—</>
    )
  }
  return <span className="nowrap">{normText(spec, (n) => num(n))}</span>
}
