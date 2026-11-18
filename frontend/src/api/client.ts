import type {
  Allocation,
  Batch,
  BatchGenealogy,
  BatchStatus,
  Checklist,
  ChecklistStatus,
  ChecklistSummary,
  ConfirmChecklistRequest,
  Lot,
  Me,
  Order,
  OrderStatus,
  Page,
  QuarantineResult,
  RecallReport,
  Recipe,
  SpcResult,
  SpcSource,
  Uuid,
} from './types'

/**
 * Контракт API, которым пользуется интерфейс. Две реализации:
 * HttpApi — настоящий бэкенд Spring Boot, DemoApi — данные в памяти браузера
 * (тот же контракт, чтобы демо и реальный режим не расходились).
 */
export interface MesApi {
  me(): Promise<Me>

  listOrders(params: { status?: OrderStatus; page?: number }): Promise<Page<Order>>
  getOrder(id: Uuid): Promise<Order>
  /** Ссылка или blob на печатный бланк чек-листа. */
  checklistPdf(orderId: Uuid): Promise<Blob>
  createChecklist(orderId: Uuid): Promise<Checklist>

  getRecipe(id: Uuid): Promise<Recipe>

  listChecklists(params: { status?: ChecklistStatus; page?: number }): Promise<Page<ChecklistSummary>>
  getChecklist(id: Uuid): Promise<Checklist>
  checklistFile(id: Uuid): Promise<Blob>
  uploadChecklist(id: Uuid, file: File): Promise<Checklist>
  recognizeChecklist(id: Uuid): Promise<Checklist>
  confirmChecklist(id: Uuid, request: ConfirmChecklistRequest): Promise<Checklist>
  rejectChecklist(id: Uuid, reason: string): Promise<Checklist>

  listBatches(params: { status?: BatchStatus; page?: number }): Promise<Page<Batch>>
  genealogy(batchNo: string): Promise<BatchGenealogy>
  recallReport(lotNo: string): Promise<RecallReport>
  quarantineLot(lotNo: string, reason: string): Promise<QuarantineResult>

  listLots(params: { materialCode?: string; page?: number }): Promise<Page<Lot>>
  allocate(materialId: Uuid, quantity: number): Promise<Allocation>

  spc(params: { recipeCode: string; parameter: string; source: SpcSource }): Promise<SpcResult>
}
