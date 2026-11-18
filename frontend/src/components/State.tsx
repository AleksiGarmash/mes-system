/** Состояние значком, подписью и цветом одновременно: цвет никогда не единственный признак. */
export function State({ tone, children }: { tone: 'ok' | 'review' | 'out' | 'missing' | 'bad' | 'neutral' | 'info'; children: React.ReactNode }) {
  return <span className={`state ${tone}`}>{children}</span>
}

export function StatusLabel({ value }: { value: [string, 'ok' | 'review' | 'bad' | 'neutral' | 'info'] }) {
  return <State tone={value[1]}>{value[0]}</State>
}
