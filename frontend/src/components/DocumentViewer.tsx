import { useEffect, useState } from 'react'

/**
 * Показ оригинала документа (фото, SVG или PDF) из blob. Ссылка на объект освобождается
 * при смене документа, чтобы не копить память в долгой смене оператора.
 */
export function DocumentViewer({ load, title }: { load: () => Promise<Blob>; title: string }) {
  const [state, setState] = useState<{ url: string; type: string } | { error: string } | null>(null)

  useEffect(() => {
    let url: string | null = null
    let cancelled = false
    load()
      .then((blob) => {
        if (cancelled) return
        url = URL.createObjectURL(blob)
        setState({ url, type: blob.type })
      })
      .catch((e: unknown) => !cancelled && setState({ error: e instanceof Error ? e.message : 'Файл недоступен' }))
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [load])

  if (state === null) return <div className="loading">Загрузка документа…</div>
  if ('error' in state) return <div className="empty">Оригинал недоступен: {state.error}</div>
  if (state.type === 'application/pdf') return <iframe src={state.url} title={title} />
  return <img src={state.url} alt={title} />
}
