import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApi } from '../api/ContextHooks'
import { Pager } from '../components/Pager'
import { ErrorNote, QueryView } from '../components/Query'
import { StatusLabel } from '../components/State'
import { orderStatus } from '../components/labels'
import { date, num } from '../lib/format'

export function OrdersPage() {
  const api = useApi()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const query = useQuery({ queryKey: ['orders', page], queryFn: () => api.listOrders({ page }) })
  const openChecklist = useMutation({
    mutationFn: (orderId: string) => api.createChecklist(orderId),
    onSuccess: (c) => navigate(`/checklists/${c.id}`),
  })
  const printBlank = useMutation({
    mutationFn: (orderId: string) => api.checklistPdf(orderId),
    onSuccess: (blob) => window.open(URL.createObjectURL(blob), '_blank', 'noopener'),
  })

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Задания</h1>
          <p>Напечатайте бланк с QR-кодом задания: по нему система узнает лист на фото без распознавания текста.</p>
        </div>
      </div>
      {(openChecklist.isError || printBlank.isError) && <ErrorNote error={openChecklist.error ?? printBlank.error} />}
      <QueryView query={query}>
        {(data) => (
          <>
            <div className="table-wrap panel">
              <table className="grid">
                <thead>
                  <tr>
                    <th>Задание</th><th>Рецептура</th><th className="n">План, кг</th><th>Дата</th><th>Состояние</th>
                    <th className="n"><span className="sr-only">Действия</span></th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((o) => (
                    <tr key={o.id}>
                      <td>№{o.orderNo}</td>
                      <td>{o.recipeCode} в.{o.recipeVersion}</td>
                      <td className="n">{num(o.plannedQty)}</td>
                      <td>{date(o.plannedDate)}</td>
                      <td><StatusLabel value={orderStatus[o.status]} /></td>
                      <td className="n">
                        <div className="row-actions" style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                          <button className="btn small" onClick={() => printBlank.mutate(o.id)}>Печать бланка</button>
                          <button className="btn small primary" onClick={() => openChecklist.mutate(o.id)}>Чек-лист</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={data.page} totalPages={data.totalPages} onPage={setPage} />
          </>
        )}
      </QueryView>
    </>
  )
}
