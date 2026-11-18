import type { UseQueryResult } from '@tanstack/react-query'
import type { ReactNode } from 'react'

/** Единая обработка загрузки и ошибок для запросов. */
export function QueryView<T>({ query, children }: { query: UseQueryResult<T>; children: (data: T) => ReactNode }) {
  if (query.isPending) return <div className="loading" role="status">Загрузка…</div>
  if (query.isError) return <ErrorNote error={query.error} onRetry={() => query.refetch()} />
  return <>{children(query.data)}</>
}

export function ErrorNote({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Неизвестная ошибка'
  return (
    <div className="note bad" role="alert">
      <strong>Не получилось выполнить действие.</strong> {message}
      {onRetry && (
        <>
          {' '}
          <button className="btn quiet small" onClick={onRetry}>
            Повторить
          </button>
        </>
      )}
    </div>
  )
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <strong>{title}</strong>
      {children}
    </div>
  )
}
