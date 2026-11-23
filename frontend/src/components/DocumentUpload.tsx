import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { ErrorNote } from './Query'

/**
 * Загрузка фото или PDF заполненного документа и запуск распознавания. Общая для чек-листа
 * производства и протокола качества. На планшете кнопка открывает камеру (capture="environment").
 */
export function DocumentUpload<T>({ document, uploaded, upload, recognize, onDone, showHeading = true }: {
  /** Что фотографируют, в винительном падеже: «бланк», «протокол». */
  document: string
  /** Заголовок внутри рамки; выключается, когда экран сам подписывает шаг. */
  showHeading?: boolean
  uploaded: boolean
  upload: (file: File) => Promise<T>
  recognize: () => Promise<T>
  onDone: (result: T) => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const uploadMutation = useMutation({ mutationFn: upload, onSuccess: onDone })
  const recognizeMutation = useMutation({ mutationFn: recognize, onSuccess: onDone })

  return (
    <>
      <div className={`drop${file ? ' has-file' : ''}`}>
        {showHeading && <h2>{uploaded ? 'Фото загружено' : `Сфотографируйте заполненный ${document}`}</h2>}
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
          <button className="btn primary" disabled={!file || uploadMutation.isPending} onClick={() => file && uploadMutation.mutate(file)}>
            {uploadMutation.isPending ? 'Загружаем…' : 'Загрузить'}
          </button>
          {uploaded && (
            <button className="btn primary" disabled={recognizeMutation.isPending} onClick={() => recognizeMutation.mutate()}>
              {recognizeMutation.isPending ? 'Распознаём…' : 'Распознать'}
            </button>
          )}
        </div>
      </div>
      {uploadMutation.isError && <ErrorNote error={uploadMutation.error} />}
      {recognizeMutation.isError && <ErrorNote error={recognizeMutation.error} />}
    </>
  )
}
