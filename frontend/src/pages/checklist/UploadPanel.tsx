import { useQueryClient } from '@tanstack/react-query'
import { useApi } from '../../api/ContextHooks'
import type { Checklist } from '../../api/types'
import { DocumentUpload } from '../../components/DocumentUpload'

/** Загрузка фото заполненного бланка производства и запуск распознавания. */
export function UploadPanel({ checklist }: { checklist: Checklist }) {
  const api = useApi()
  const queryClient = useQueryClient()
  const done = (c: Checklist) => {
    queryClient.setQueryData(['checklist', c.id], c)
    queryClient.invalidateQueries({ queryKey: ['checklists'] })
  }

  return (
    <div className="stack" style={{ maxWidth: 720 }}>
      {checklist.status === 'REJECTED' && checklist.reviewSummary && (
        <div className="note warn">Лист отклонён: {checklist.reviewSummary}. Загрузите фото нового бланка.</div>
      )}
      <DocumentUpload
        document="бланк"
        uploaded={checklist.status === 'UPLOADED'}
        upload={(file) => api.uploadChecklist(checklist.id, file)}
        recognize={() => api.recognizeChecklist(checklist.id)}
        onDone={done}
      />
    </div>
  )
}
