import type { BatchStatus, ChecklistStatus, OrderStatus, QcChecklistStatus, QcResult } from '../api/types'

type Tone = 'ok' | 'review' | 'bad' | 'neutral' | 'info'

/** Статусы простыми словами (без внутренних кодов) и их тон. */
export const checklistStatus: Record<ChecklistStatus, [string, Tone]> = {
  NEW: ['Ждёт фото', 'neutral'],
  UPLOADED: ['Загружен, не распознан', 'info'],
  OCR_COMPLETE: ['Распознан', 'info'],
  WAITING_CONFIRMATION: ['Ждёт проверки', 'review'],
  CONFIRMED: ['Подтверждён', 'ok'],
  PRODUCTION_COMPLETED: ['Партия создана', 'ok'],
  REJECTED: ['Отклонён', 'bad'],
}

export const batchStatus: Record<BatchStatus, [string, Tone]> = {
  IN_PRODUCTION: ['В производстве', 'info'],
  PENDING_QC: ['Ждёт контроля качества', 'review'],
  RELEASED: ['Выпущена', 'ok'],
  REJECTED: ['Забракована', 'bad'],
  QUARANTINE: ['Карантин', 'bad'],
}

export const orderStatus: Record<OrderStatus, [string, Tone]> = {
  PLANNED: ['Запланировано', 'neutral'],
  IN_PROGRESS: ['В работе', 'info'],
  COMPLETED: ['Выполнено', 'ok'],
  CANCELLED: ['Отменено', 'bad'],
}

export const qcResult: Record<QcResult, [string, Tone]> = {
  PENDING: ['Не проверено', 'neutral'],
  PASS: ['Соответствует', 'ok'],
  FAIL: ['Не соответствует', 'bad'],
}

export const qcChecklistStatus: Record<QcChecklistStatus, [string, Tone]> = {
  NEW: ['Ждёт фото протокола', 'neutral'],
  UPLOADED: ['Загружен, не распознан', 'info'],
  OCR_COMPLETE: ['Распознан', 'info'],
  WAITING_CONFIRMATION: ['Ждёт проверки', 'review'],
  CONFIRMED: ['Подтверждён', 'ok'],
  REJECTED: ['Отклонён', 'bad'],
}
