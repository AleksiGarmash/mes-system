import type { MesApi } from '../client'
import { ApiError } from '../types'
import type {
  Allocation,
  Batch,
  BatchGenealogy,
  Checklist,
  ChecklistItem,
  ChecklistSummary,
  ConfirmChecklistRequest,
  Lot,
  Page,
  QuarantineResult,
  RecallReport,
  SpcResult,
} from '../types'
import { analyze } from '../../lib/spc'
import { isDosageWithin, isWithinRange } from '../../lib/tolerance'
import { demoChecklists, demoHistory, demoLots, demoOrders, demoRecipe, demoUsage } from './data'

const PAGE_SIZE = 20

function page<T>(all: T[], pageNo = 0): Page<T> {
  const start = pageNo * PAGE_SIZE
  return {
    items: all.slice(start, start + PAGE_SIZE),
    page: pageNo,
    size: PAGE_SIZE,
    totalElements: all.length,
    totalPages: Math.max(1, Math.ceil(all.length / PAGE_SIZE)),
  }
}

const clone = <T,>(value: T): T => structuredClone(value)
const notFound = (what: string) => new ApiError(404, `${what} не найден(а)`)

/**
 * Демо-реализация API: те же правила, что на сервере, но данные живут в памяти вкладки
 * и сбрасываются при перезагрузке. Позволяет показать интерфейс без бэкенда и Keycloak.
 */
export class DemoApi implements MesApi {
  private recipe = demoRecipe()
  private orders = demoOrders()
  private checklists = demoChecklists()
  private lots = demoLots()
  private history = demoHistory()
  private batches: Batch[] = this.history.batches
  private usage = demoUsage(this.batches)
  private files = new Map<string, Blob>()
  private batchSeq = 201

  constructor(private readonly latencyMs = 250) {}

  private async delay<T>(value: T): Promise<T> {
    if (this.latencyMs > 0) await new Promise((r) => setTimeout(r, this.latencyMs))
    return clone(value)
  }

  async me() {
    return this.delay({ username: 'demo.operator', roles: ['ADMIN' as const, 'OPERATOR' as const, 'TECHNOLOGIST' as const] })
  }

  async listOrders(p: { status?: string; page?: number }) {
    return this.delay(page(this.orders.filter((o) => !p.status || o.status === p.status), p.page))
  }

  async getOrder(id: string) {
    const order = this.orders.find((o) => o.id === id)
    if (!order) throw notFound('Задание')
    return this.delay(order)
  }

  async checklistPdf(orderId: string) {
    await this.getOrder(orderId)
    const res = await fetch(new URL('demo/checklist-blank.svg', document.baseURI))
    return res.blob()
  }

  async createChecklist(orderId: string) {
    await this.getOrder(orderId)
    const existing = this.checklists.find((c) => c.productionOrderId === orderId && c.status !== 'REJECTED')
    if (existing) return this.delay(existing)
    const checklist: Checklist = {
      id: `cl-${orderId}-${Date.now()}`, productionOrderId: orderId, status: 'NEW', imagePath: null, ocrConfidence: null,
      uploadedAt: null, batchId: null, qrVerified: null, pageCount: null, reviewSummary: null, items: [], controlRecords: [],
    }
    this.checklists.unshift(checklist)
    return this.delay(checklist)
  }

  async getRecipe(id: string) {
    if (id !== this.recipe.id) throw notFound('Рецептура')
    return this.delay(this.recipe)
  }

  async listChecklists(p: { status?: string; page?: number }) {
    const summaries: ChecklistSummary[] = this.checklists
      .filter((c) => !p.status || c.status === p.status)
      .map((c) => {
        const order = this.orders.find((o) => o.id === c.productionOrderId)
        return {
          id: c.id, productionOrderId: c.productionOrderId, orderNo: order?.orderNo ?? '—', recipeCode: this.recipe.code,
          status: c.status, uploadedAt: c.uploadedAt, pageCount: c.pageCount, itemCount: c.items.length,
          needsReviewCount: c.items.filter((i) => i.needsReview).length, createdAt: c.uploadedAt ?? new Date().toISOString(),
        }
      })
    return this.delay(page(summaries, p.page))
  }

  private findChecklist(id: string): Checklist {
    const c = this.checklists.find((x) => x.id === id)
    if (!c) throw notFound('Чек-лист')
    return c
  }

  async getChecklist(id: string) {
    return this.delay(this.findChecklist(id))
  }

  async checklistFile(id: string) {
    const stored = this.files.get(id)
    if (stored) return stored
    if (id === 'cl-245') return (await fetch(new URL('demo/checklist-245.svg', document.baseURI))).blob()
    throw notFound('Файл')
  }

  async uploadChecklist(id: string, file: File) {
    const c = this.findChecklist(id)
    if (!['NEW', 'UPLOADED', 'REJECTED'].includes(c.status)) {
      throw new ApiError(409, 'Подтверждённый документ нельзя заменить.')
    }
    this.files.set(id, file)
    Object.assign(c, {
      status: 'UPLOADED', imagePath: `checklists/${id}/${file.name}`, uploadedAt: new Date().toISOString(),
      pageCount: 1, items: [], qrVerified: null, reviewSummary: null,
    })
    return this.delay(c)
  }

  /** Имитация распознавания: строки по рецептуре с правдоподобными отклонениями и разной уверенностью. */
  async recognizeChecklist(id: string) {
    const c = this.findChecklist(id)
    if (c.status !== 'UPLOADED') throw new ApiError(409, `Распознавание доступно только из статуса UPLOADED, текущий: ${c.status}`)
    const offsets = [0.4, 1.9, -0.6, 2.4, 0.9]
    const confidences = [0.96, 0.92, 0.66, 0.94, 0.9]
    c.items = this.recipe.items.map((ri, i): ChecklistItem => {
      const weight = Math.round(ri.targetQty * (1 + offsets[i % offsets.length] / 100) * 10) / 10
      const within = isDosageWithin(ri.targetQty, weight, ri.tolerancePlus, ri.toleranceMinus)
      const confidence = confidences[i % confidences.length]
      const lot = this.lots.find((l) => l.materialId === ri.materialId && (!l.expiry || l.expiry >= new Date().toISOString().slice(0, 10)))
      const notes: string[] = []
      if (confidence < 0.9) notes.push(`Низкая уверенность распознавания (${Math.round(confidence * 100)}%).`)
      if (!within) notes.push('Вес вне допуска рецептуры.')
      return {
        id: `${id}-it-${i}`, recipeItemId: ri.id, materialId: ri.materialId, materialNameRaw: ri.materialCode,
        batchNumberRaw: ri.sourceStageNo ? null : lot?.lotNo ?? null,
        rawMaterialBatchId: ri.sourceStageNo ? null : lot?.id ?? null,
        weight, confidence, withinTolerance: within, needsReview: notes.length > 0, reviewNote: notes.join(' ') || null,
        correctedByOperator: false, mixSpeedActual: null, mixMinutesActual: null, note: null,
      }
    })
    c.qrVerified = true
    c.ocrConfidence = confidences.reduce((a, b) => a + b, 0) / confidences.length
    c.status = 'WAITING_CONFIRMATION'
    return this.delay(c)
  }

  async confirmChecklist(id: string, request: ConfirmChecklistRequest) {
    const c = this.findChecklist(id)
    if (c.status !== 'WAITING_CONFIRMATION') throw new ApiError(409, 'Подтверждение доступно только из статуса WAITING_CONFIRMATION')
    for (const corr of request.corrections) {
      const item = c.items.find((i) => i.id === corr.checklistItemId)
      if (!item) throw notFound('Строка чек-листа')
      if (corr.weight !== undefined) item.weight = corr.weight
      if (corr.rawMaterialBatchId !== undefined) item.rawMaterialBatchId = corr.rawMaterialBatchId
      if (corr.mixSpeedActual !== undefined) item.mixSpeedActual = corr.mixSpeedActual
      if (corr.mixMinutesActual !== undefined) item.mixMinutesActual = corr.mixMinutesActual
      item.correctedByOperator = true
      item.needsReview = false
    }
    if (c.items.length === 0) throw new ApiError(409, 'В чек-листе нет ни одной строки — подтверждать нечего.')
    const incomplete = c.items.some((i) => i.weight === null || i.materialId === null)
    if (incomplete) throw new ApiError(409, 'Есть незаполненные строки чек-листа — заполните материал и вес.')
    const missingControls = this.recipe.controlPoints.filter((p) => !request.controlValues.some((v) => v.controlPointId === p.id))
    if (missingControls.length) throw new ApiError(409, `Не введены значения контрольных точек: ${missingControls.map((p) => p.parameterName).join(', ')}`)

    let quarantine = false
    c.controlRecords = this.recipe.controlPoints.map((p) => {
      const value = request.controlValues.find((v) => v.controlPointId === p.id)!.measuredValue
      const within = isWithinRange(value, p.minValue, p.maxValue)
      if (!within && p.kind === 'FINISHED_PRODUCT') quarantine = true
      return { id: `${id}-cr-${p.id}`, controlPointId: p.id, kind: p.kind, stageNo: p.stageNo, parameterName: p.parameterName,
        expectedMin: p.minValue, expectedMax: p.maxValue, measuredValue: value, withinRange: within }
    })
    for (const item of c.items) {
      const lot = this.lots.find((l) => l.id === item.rawMaterialBatchId)
      if (lot && lot.quantityAvailable !== null && item.weight !== null) {
        lot.quantityAvailable = Math.max(0, Math.round((lot.quantityAvailable - item.weight) * 1000) / 1000)
      }
    }
    const order = this.orders.find((o) => o.id === c.productionOrderId)!
    const batch: Batch = {
      id: `batch-new-${this.batchSeq}`, batchNo: `B-2026-${String(this.batchSeq++).padStart(6, '0')}`, productionOrderId: order.id,
      orderNo: order.orderNo, qty: c.items.filter((i) => this.recipe.items.find((r) => r.id === i.recipeItemId)?.stageNo === 2)
        .reduce((a, i) => a + (i.weight ?? 0), 0),
      status: quarantine ? 'QUARANTINE' : 'PENDING_QC', operator: request.operator, producedAt: new Date().toISOString(),
    }
    this.batches.unshift(batch)
    order.status = 'COMPLETED'
    c.batchId = batch.id
    c.status = 'PRODUCTION_COMPLETED'
    return this.delay(c)
  }

  async rejectChecklist(id: string, reason: string) {
    const c = this.findChecklist(id)
    if (c.status === 'CONFIRMED' || c.status === 'PRODUCTION_COMPLETED') throw new ApiError(409, 'Подтверждённый чек-лист нельзя отклонить.')
    c.status = 'REJECTED'
    c.reviewSummary = reason
    return this.delay(c)
  }

  async listBatches(p: { status?: string; page?: number }) {
    return this.delay(page(this.batches.filter((b) => !p.status || b.status === p.status), p.page))
  }

  async genealogy(batchNo: string): Promise<BatchGenealogy> {
    const batch = this.batches.find((b) => b.batchNo === batchNo)
    if (!batch) throw notFound('Партия')
    const usedLot = this.usage.find((u) => u.batchNo === batchNo)
    const idx = this.history.ph.findIndex((p) => p.label === batchNo)
    const components = this.recipe.items.map((ri) => {
      const isRecalledComponent = ri.materialCode === 'Компонент А' && usedLot !== undefined
      const lot = isRecalledComponent
        ? this.lots.find((l) => l.lotNo === usedLot!.lotNo)
        : ri.sourceStageNo ? undefined : this.lots.find((l) => l.materialId === ri.materialId)
      const actual = isRecalledComponent ? usedLot!.qty : Math.round(ri.targetQty * 1.004 * 10) / 10
      return {
        materialCode: ri.materialCode, stageNo: ri.stageNo, lotNo: lot?.lotNo ?? null, supplier: lot?.supplier ?? null,
        expiry: lot?.expiry ?? null, targetQty: ri.targetQty, actualQty: actual,
        withinTolerance: isDosageWithin(ri.targetQty, actual, ri.tolerancePlus, ri.toleranceMinus),
        mixSpeedActual: ri.mixSpeedNorm ? 115 : null, mixMinutesActual: ri.mixMinutesNorm,
      }
    })
    const qualityChecks = idx < 0 ? [] : [
      { parameter: 'Внешний вид', result: 'PASS' as const, measuredValue: null, textValue: 'однородная жидкость', expectedMin: null, expectedMax: null, expectedText: 'однородная жидкость;однородная жидкость без осадка' },
      { parameter: 'Водородный показатель pH', result: (this.history.ph[idx].value > 10.5 ? 'FAIL' : 'PASS') as 'PASS' | 'FAIL', measuredValue: this.history.ph[idx].value, textValue: null, expectedMin: 9.5, expectedMax: 10.5, expectedText: null },
      { parameter: 'Плотность', result: 'PASS' as const, measuredValue: this.history.density[idx].value, textValue: null, expectedMin: 1.05, expectedMax: 1.15, expectedText: null },
    ]
    return this.delay({
      batchNo: batch.batchNo, status: batch.status, orderNo: batch.orderNo, recipeCode: this.recipe.code,
      recipeVersion: this.recipe.version, producedAt: batch.producedAt, components, qualityChecks,
    })
  }

  async recallReport(lotNo: string): Promise<RecallReport> {
    const lot = this.lots.find((l) => l.lotNo === lotNo)
    if (!lot) throw notFound(`Партия сырья ${lotNo}`)
    const affected = this.usage
      .filter((u) => u.lotNo === lotNo)
      .map((u) => {
        const b = this.batches.find((x) => x.batchNo === u.batchNo)!
        return { batchNo: b.batchNo, status: b.status, producedAt: b.producedAt, orderNo: b.orderNo, recipeCode: this.recipe.code, usedQty: u.qty }
      })
      .sort((a, b) => (a.producedAt ?? '').localeCompare(b.producedAt ?? ''))
    return this.delay({ lotNo: lot.lotNo, materialCode: lot.materialCode, supplier: lot.supplier, expiry: lot.expiry, affectedBatches: affected })
  }

  async quarantineLot(lotNo: string, reason: string): Promise<QuarantineResult> {
    void reason
    const report = await this.recallReport(lotNo)
    const quarantined: string[] = []
    const skipped: string[] = []
    for (const a of report.affectedBatches) {
      const b = this.batches.find((x) => x.batchNo === a.batchNo)!
      if (b.status === 'REJECTED' || b.status === 'QUARANTINE') skipped.push(b.batchNo)
      else { b.status = 'QUARANTINE'; quarantined.push(b.batchNo) }
    }
    return this.delay({ lotNo, quarantinedBatchNos: quarantined, skippedBatchNos: skipped })
  }

  async listLots(p: { materialCode?: string; page?: number }) {
    const q = p.materialCode?.trim().toLowerCase()
    const lots = this.lots
      .filter((l) => !q || (l.materialCode ?? '').toLowerCase().includes(q))
      .sort((a, b) => (a.expiry ?? '9999').localeCompare(b.expiry ?? '9999'))
    return this.delay(page<Lot>(lots, p.page))
  }

  async allocate(materialId: string, quantity: number): Promise<Allocation> {
    const today = new Date().toISOString().slice(0, 10)
    const usable = this.lots
      .filter((l) => l.materialId === materialId && (l.quantityAvailable ?? 0) > 0 && (!l.expiry || l.expiry >= today))
      .sort((a, b) => (a.expiry ?? '9999').localeCompare(b.expiry ?? '9999') || a.lotNo.localeCompare(b.lotNo))
    const picks: Allocation['picks'] = []
    let remaining = quantity
    for (const l of usable) {
      if (remaining <= 0) break
      const take = Math.min(l.quantityAvailable ?? 0, remaining)
      picks.push({ lotId: l.id, lotNo: l.lotNo, expiry: l.expiry, quantity: take })
      remaining = Math.round((remaining - take) * 1000) / 1000
    }
    return this.delay({ picks, shortage: remaining, complete: remaining === 0 })
  }

  async spc(p: { recipeCode: string; parameter: string; source: string }): Promise<SpcResult> {
    const isPh = p.parameter.toLowerCase().includes('ph')
    const series = isPh ? this.history.ph : this.history.density
    const [lsl, usl] = isPh ? [9.5, 10.5] : [1.05, 1.15]
    return this.delay({
      recipeCode: p.recipeCode,
      parameter: p.parameter,
      source: p.source as SpcResult['source'],
      lsl,
      usl,
      points: series.map((s) => ({ ...s, outOfSpec: s.value < lsl || s.value > usl })),
      report: analyze(series.map((s) => s.value), lsl, usl),
    })
  }
}
