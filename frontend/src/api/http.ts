import type { MesApi } from './client'
import { ApiError } from './types'
import type {
  Allocation,
  Batch,
  BatchGenealogy,
  Checklist,
  ChecklistSummary,
  ConfirmChecklistRequest,
  Lot,
  Me,
  Order,
  Page,
  QuarantineResult,
  RecallReport,
  Recipe,
  SpcResult,
  SpcSource,
} from './types'

type TokenProvider = () => Promise<string | undefined>

function query(params: Record<string, string | number | undefined>): string {
  const q = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') q.set(key, String(value))
  }
  const s = q.toString()
  return s ? `?${s}` : ''
}

/** Реализация API поверх настоящего бэкенда. Токен Keycloak добавляется к каждому запросу. */
export class HttpApi implements MesApi {
  constructor(
    private readonly baseUrl: string,
    private readonly token: TokenProvider,
  ) {}

  private async request(path: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers)
    const token = await this.token()
    if (token) headers.set('Authorization', `Bearer ${token}`)
    const response = await fetch(`${this.baseUrl}${path}`, { ...init, headers })
    if (!response.ok) {
      let message = `Ошибка ${response.status}`
      try {
        const body = await response.json()
        if (body?.message) message = body.message
      } catch {
        // тело не JSON — оставляем код ответа
      }
      throw new ApiError(response.status, message)
    }
    return response
  }

  private async json<T>(path: string, init?: RequestInit): Promise<T> {
    return (await this.request(path, init)).json() as Promise<T>
  }

  private post<T>(path: string, body?: unknown): Promise<T> {
    return this.json<T>(path, {
      method: 'POST',
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  }

  private async blob(path: string): Promise<Blob> {
    return (await this.request(path)).blob()
  }

  me() {
    return this.json<Me>('/api/me')
  }

  listOrders(p: Parameters<MesApi['listOrders']>[0]) {
    return this.json<Page<Order>>(`/api/orders${query({ status: p.status, page: p.page })}`)
  }
  getOrder(id: string) {
    return this.json<Order>(`/api/orders/${id}`)
  }
  checklistPdf(orderId: string) {
    return this.blob(`/api/orders/${orderId}/checklist-pdf`)
  }
  createChecklist(orderId: string) {
    return this.post<Checklist>('/api/checklists', { productionOrderId: orderId })
  }

  getRecipe(id: string) {
    return this.json<Recipe>(`/api/recipes/${id}`)
  }

  listChecklists(p: Parameters<MesApi['listChecklists']>[0]) {
    return this.json<Page<ChecklistSummary>>(`/api/checklists${query({ status: p.status, page: p.page })}`)
  }
  getChecklist(id: string) {
    return this.json<Checklist>(`/api/checklists/${id}`)
  }
  checklistFile(id: string) {
    return this.blob(`/api/checklists/${id}/file`)
  }
  uploadChecklist(id: string, file: File) {
    const form = new FormData()
    form.append('file', file)
    return this.json<Checklist>(`/api/checklists/${id}/upload`, { method: 'POST', body: form })
  }
  recognizeChecklist(id: string) {
    return this.post<Checklist>(`/api/checklists/${id}/recognize`)
  }
  confirmChecklist(id: string, request: ConfirmChecklistRequest) {
    return this.post<Checklist>(`/api/checklists/${id}/confirm`, request)
  }
  rejectChecklist(id: string, reason: string) {
    return this.post<Checklist>(`/api/checklists/${id}/reject`, { reason })
  }

  listBatches(p: Parameters<MesApi['listBatches']>[0]) {
    return this.json<Page<Batch>>(`/api/batches${query({ status: p.status, page: p.page })}`)
  }
  genealogy(batchNo: string) {
    return this.json<BatchGenealogy>(`/api/traceability/batches/${encodeURIComponent(batchNo)}`)
  }
  recallReport(lotNo: string) {
    return this.json<RecallReport>(`/api/traceability/lots/${encodeURIComponent(lotNo)}/batches`)
  }
  quarantineLot(lotNo: string, reason: string) {
    return this.post<QuarantineResult>(`/api/traceability/lots/${encodeURIComponent(lotNo)}/quarantine`, { reason })
  }

  listLots(p: { materialCode?: string; page?: number }) {
    return this.json<Page<Lot>>(`/api/stock/lots${query({ materialCode: p.materialCode, page: p.page })}`)
  }
  allocate(materialId: string, quantity: number) {
    return this.post<Allocation>('/api/stock/allocate', { materialId, quantity })
  }

  spc(p: { recipeCode: string; parameter: string; source: SpcSource }) {
    return this.json<SpcResult>(`/api/analytics/spc${query(p)}`)
  }
}
