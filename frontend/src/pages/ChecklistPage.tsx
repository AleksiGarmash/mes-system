import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../api/ContextHooks'
import { QueryView } from '../components/Query'
import { StatusLabel } from '../components/State'
import { checklistStatus } from '../components/labels'
import { date } from '../lib/format'
import { CompletedView } from './checklist/CompletedView'
import { ReviewWorkspace } from './checklist/ReviewWorkspace'
import { UploadPanel } from './checklist/UploadPanel'

export function ChecklistPage({ operator }: { operator: string }) {
  const { id = '' } = useParams()
  const api = useApi()
  const checklist = useQuery({ queryKey: ['checklist', id], queryFn: () => api.getChecklist(id) })
  const order = useQuery({
    queryKey: ['order', checklist.data?.productionOrderId],
    queryFn: () => api.getOrder(checklist.data!.productionOrderId),
    enabled: !!checklist.data,
  })
  const recipe = useQuery({
    queryKey: ['recipe', order.data?.recipeId],
    queryFn: () => api.getRecipe(order.data!.recipeId),
    enabled: !!order.data,
  })

  return (
    <QueryView query={checklist}>
      {(c) => (
        <>
          <div className="page-head">
            <div>
              <p className="small" style={{ margin: 0 }}>
                <Link to="/checklists">Чек-листы</Link>
              </p>
              <h1>Задание №{order.data?.orderNo ?? '…'}</h1>
              <p>
                {recipe.data ? `${recipe.data.name}, рецептура ${recipe.data.code} версия ${recipe.data.version}` : 'Загрузка рецептуры…'}
                {order.data && ` · план ${order.data.plannedQty} кг · ${date(order.data.plannedDate)}`}
              </p>
            </div>
            <StatusLabel value={checklistStatus[c.status]} />
          </div>
          {!order.data || !recipe.data ? (
            <div className="loading">Загрузка…</div>
          ) : c.status === 'WAITING_CONFIRMATION' ? (
            <ReviewWorkspace checklist={c} order={order.data} recipe={recipe.data} operator={operator} />
          ) : c.status === 'PRODUCTION_COMPLETED' || c.status === 'CONFIRMED' ? (
            <CompletedView checklist={c} recipe={recipe.data} />
          ) : (
            <UploadPanel checklist={c} />
          )}
        </>
      )}
    </QueryView>
  )
}
