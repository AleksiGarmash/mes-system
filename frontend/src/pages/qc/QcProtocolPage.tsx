import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { useApi } from '../../api/ContextHooks'
import type { QcChecklist, QualitySpec } from '../../api/types'
import { DocumentUpload } from '../../components/DocumentUpload'
import { ErrorNote, QueryView } from '../../components/Query'
import { StatusLabel } from '../../components/State'
import { qcChecklistStatus } from '../../components/labels'
import { QcCompletedView } from './QcCompletedView'
import { QcReviewWorkspace } from './QcReviewWorkspace'

export function QcProtocolPage({ labTechnician }: { labTechnician: string }) {
  const { id = '' } = useParams()
  const api = useApi()
  const protocol = useQuery({ queryKey: ['qc', 'one', id], queryFn: () => api.getQcChecklist(id) })
  const specId = protocol.data?.qualitySpecId
  const spec = useQuery({
    queryKey: ['quality-spec', specId],
    queryFn: () => api.getQualitySpec(specId!),
    enabled: !!specId,
    staleTime: 5 * 60_000,
  })

  return (
    <QueryView query={protocol}>
      {(p) => {
        const specReady = !p.qualitySpecId || spec.isSuccess
        return (
          <>
            <div className="page-head">
              <div>
                <p className="small" style={{ margin: 0 }}>
                  <Link to="/qc">Контроль качества</Link>
                </p>
                <h1>Протокол партии {p.batchNo}</h1>
                <p>
                  {!p.qualitySpecId
                    ? 'Спецификация качества для рецептуры не задана'
                    : spec.data
                      ? `${spec.data.name}, спецификация ${spec.data.code} версия ${spec.data.version}`
                      : 'Загрузка спецификации…'}
                </p>
              </div>
              <StatusLabel value={qcChecklistStatus[p.status]} />
            </div>
            {spec.isError && <ErrorNote error={spec.error} onRetry={() => spec.refetch()} />}
            {!specReady ? (
              <div className="loading" role="status">Загрузка…</div>
            ) : p.status === 'WAITING_CONFIRMATION' ? (
              <QcReviewWorkspace protocol={p} spec={spec.data} labTechnician={labTechnician} />
            ) : p.status === 'CONFIRMED' ? (
              <QcCompletedView protocol={p} spec={spec.data} />
            ) : (
              <QcStart protocol={p} spec={spec.data} />
            )}
          </>
        )
      }}
    </QueryView>
  )
}

/** Шаги до проверки: напечатать бланк с QR-кодом партии, заполнить, сфотографировать и распознать. */
function QcStart({ protocol, spec }: { protocol: QcChecklist; spec: QualitySpec | undefined }) {
  const api = useApi()
  const queryClient = useQueryClient()
  const blank = useMutation({
    mutationFn: () => api.qcChecklistPdf(protocol.batchId),
    onSuccess: (blob) => window.open(URL.createObjectURL(blob), '_blank', 'noopener'),
  })
  const done = (updated: QcChecklist) => {
    queryClient.setQueryData(['qc', 'one', updated.id], updated)
    queryClient.invalidateQueries({ queryKey: ['qc'] })
  }

  return (
    <div className="stack" style={{ maxWidth: 760 }}>
      {protocol.status === 'REJECTED' && (
        <div className="note warn">
          Протокол отклонён{protocol.rejectReason ? `: ${protocol.rejectReason}` : ''}. Заполните бланк заново и загрузите новое фото.
        </div>
      )}
      {!spec && (
        <div className="note warn">
          Для рецептуры нет активной спецификации качества. Протокол можно загрузить, но подтвердить его не получится,
          пока технолог не активирует спецификацию.
        </div>
      )}
      <section className="stack step" aria-labelledby="qc-step-1">
        <h2 id="qc-step-1">1. Распечатайте бланк</h2>
        <div className="panel">
          <div className="body stack" style={{ gap: 12 }}>
            <p style={{ margin: 0 }}>
              На бланке QR-код партии {protocol.batchNo} и параметры из спецификации
              {spec ? ` (${spec.items.length})` : ''}. По QR-коду система проверит, что фото относится к этой партии.
            </p>
            <div className="actions">
              <button className="btn" disabled={blank.isPending} onClick={() => blank.mutate()}>
                {blank.isPending ? 'Готовим бланк…' : 'Открыть бланк для печати'}
              </button>
            </div>
            {blank.isError && <ErrorNote error={blank.error} />}
          </div>
        </div>
      </section>
      <section className="stack step" aria-labelledby="qc-step-2">
        <h2 id="qc-step-2">
          {protocol.status === 'UPLOADED' ? '2. Фото загружено — запустите распознавание' : '2. Сфотографируйте заполненный протокол'}
        </h2>
        <DocumentUpload
          document="протокол"
          showHeading={false}
          uploaded={protocol.status === 'UPLOADED'}
          upload={(file) => api.uploadQcChecklist(protocol.id, file)}
          recognize={() => api.recognizeQcChecklist(protocol.id)}
          onDone={done}
        />
      </section>
    </div>
  )
}
