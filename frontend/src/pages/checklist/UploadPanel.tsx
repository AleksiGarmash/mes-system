import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useApi } from '../../api/ContextHooks'
import type { Checklist } from '../../api/types'
import { ErrorNote } from '../../components/Query'

/**
 * Загрузка фото или PDF заполненного бланка и запуск распознавания.
 * На планшете кнопка открывает камеру (capture="environment").
 */
export function UploadPanel({ checklist }: { checklist: Checklist }) {
  const api = useApi()
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const done = (c: Checklist) => {
    queryClient.setQueryData(['checklist', c.id], c)
    queryClient.invalidateQueries({ queryKey: ['checklists'] })
  }
  const upload = useMutation({ mutationFn: (f: File) => api.uploadChecklist(checklist.id, f), onSuccess: done })
  const recognize = useMutation({ mutationFn: () => api.recognizeChecklist(checklist.id), onSuccess: done })

  const uploaded = checklist.status === 'UPLOADED'

  return (
    <div className="stack" style={{ maxWidth: 720 }}>
      {checklist.status === 'REJECTED' && checklist.reviewSummary && (
        <div className="note warn">Лист отклонён: {checklist.reviewSummary}. Загрузите фото нового бланка.</div>
      )}
      <div className={`drop${file ? ' has-file' : ''}`}>
        <h2>{uploaded ? 'Фото загружено' : 'Сфотографируйте заполненный бланк'}</h2>
        <p className="muted" style={{ margin: 0 }}>
          Лист целиком в кадре, без бликов и теней. Подойдёт фото или PDF-скан до 10 страниц.
        </p>
        <label className="btn">
          {file ? 'Выбрать другой файл' : 'Сделать фото или выбрать файл'}
          <input
            type="file"
            accept="image/*,application/pdf"
            capture="environment"
            className="sr-only"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        {file && <span className="small">Выбран файл: {file.name}</span>}
        <div className="actions">
          <button className="btn primary" disabled={!file || upload.isPending} onClick={() => file && upload.mutate(file)}>
            {upload.isPending ? 'Загружаем…' : 'Загрузить'}
          </button>
          {uploaded && (
            <button className="btn primary" disabled={recognize.isPending} onClick={() => recognize.mutate()}>
              {recognize.isPending ? 'Распознаём…' : 'Распознать'}
            </button>
          )}
        </div>
      </div>
      {upload.isError && <ErrorNote error={upload.error} />}
      {recognize.isError && <ErrorNote error={recognize.error} />}
    </div>
  )
}
