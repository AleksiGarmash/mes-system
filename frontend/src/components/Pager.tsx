export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav className="actions" aria-label="Страницы" style={{ marginTop: 12 }}>
      <button className="btn small" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Назад
      </button>
      <span className="small muted">
        Страница {page + 1} из {totalPages}
      </span>
      <button className="btn small" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Дальше
      </button>
    </nav>
  )
}
