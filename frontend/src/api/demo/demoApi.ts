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
  ConfirmQcChecklistRequest,
  Page,
  QcChecklist,
  QcChecklistItem,
  QcChecklistSummary,
  QuarantineResult,
  RecallReport,
  SpcResult,
} from '../types'
import { analyze } from '../../lib/spc'
import { isDosageWithin, isWithinRange } from '../../lib/tolerance'
import { allowedValues, isAllowedValue } from '../../lib/qcReview'
import { demoChecklists, demoHistory, demoLots, demoOrders, demoQcChecklists, demoQualitySpec, demoRecipe, demoUsage } from './data'

const PAGE_SIZE = 20

function page<T>(all: T[], pageNo = 0, size = PAGE_SIZE): Page<T> {
  const safeSize = Math.min(Math.max(size, 1), 100)
  const start = pageNo * safeSize
  return {
    items: all.slice(start, start + safeSize),
    page: pageNo,
    size: safeSize,
    totalElements: all.length,
    totalPages: Math.max(1, Math.ceil(all.length / safeSize)),
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
  private spec = demoQualitySpec()
  private qc = demoQcChecklists()
  private qcCreated = new Map<string, string>(this.qc.map((c) => [c.id, c.uploadedAt ?? new Date().toISOString()]))

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
    const protocol = this.qc.find((c) => c.batchId === batch.id && c.status === 'CONFIRMED')
    const qualityChecks: BatchGenealogy['qualityChecks'] = protocol
      ? this.spec.items.map((si) => {
          const item = protocol.items.find((i) => i.qualitySpecItemId === si.id)!
          return {
            parameter: si.parameterName, result: item.withinRange ? 'PASS' : 'FAIL',
            measuredValue: si.parameterType === 'QUALITATIVE' ? null : item.measuredValue,
            textValue: si.parameterType === 'QUALITATIVE' ? item.textValue : null,
            expectedMin: si.minValue, expectedMax: si.maxValue,
            expectedText: si.parameterType === 'QUALITATIVE' ? si.allowedValues : null,
          }
        })
      : idx < 0 || batch.status === 'PENDING_QC' ? [] : [
      { parameter: 'Внешний вид', result: 'PASS' as const, measuredValue: null, textValue: 'однородная жидкость', expectedMin: null, expectedMax: null, expectedText: 'однородная жидкость;однородная жидкость без осадка' },
      { parameter: 'Плотность', result: 'PASS' as const, measuredValue: this.history.density[idx].value, textValue: null, expectedMin: 1.05, expectedMax: 1.15, expectedText: null },
      { parameter: 'Водородный показатель pH', result: (this.history.ph[idx].value > 10.5 ? 'FAIL' : 'PASS') as 'PASS' | 'FAIL', measuredValue: this.history.ph[idx].value, textValue: null, expectedMin: 9.5, expectedMax: 10.5, expectedText: null },
      { parameter: 'Сухой остаток', result: 'PASS' as const, measuredValue: Math.round((21.4 + (idx % 5) * 0.35) * 10) / 10, textValue: null, expectedMin: 20, expectedMax: 24, expectedText: null },
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
    const specId = isPh ? 'si-ph' : 'si-density'
    const [lsl, usl] = isPh ? [9.5, 10.5] : [1.05, 1.15]
    // На карте только результаты контроля: партии, которые ещё ждут протокола, сюда не попадают.
    const series = (isPh ? this.history.ph : this.history.density).flatMap((point) => {
      const batch = this.batches.find((b) => b.batchNo === point.label)
      const protocol = batch && this.qc.find((c) => c.batchId === batch.id && c.status === 'CONFIRMED')
      const measured = protocol?.items.find((i) => i.qualitySpecItemId === specId)?.measuredValue
      if (measured != null) return [{ ...point, value: measured }]
      return batch?.status === 'PENDING_QC' ? [] : [point]
    })
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

  // ---------- протоколы контроля качества ----------

  private findQc(id: string): QcChecklist {
    const c = this.qc.find((x) => x.id === id)
    if (!c) throw notFound('Протокол QC')
    return c
  }

  private specItem(id: string | null) {
    return id ? this.spec.items.find((si) => si.id === id) : undefined
  }

  /** Как на сервере: качественный параметр сравнивается со списком значений, числовой — с диапазоном. */
  private evaluate(item: QcChecklistItem) {
    const si = this.specItem(item.qualitySpecItemId)
    if (!si) return
    if (si.parameterType === 'QUALITATIVE') {
      if (item.textValue && item.textValue.trim()) item.withinRange = isAllowedValue(item.textValue, si)
    } else if (item.measuredValue !== null) {
      item.withinRange = isWithinRange(item.measuredValue, si.minValue, si.maxValue)
    }
  }

  async listQcChecklists(p: { status?: string; page?: number; size?: number }) {
    const summaries: QcChecklistSummary[] = this.qc
      .filter((c) => !p.status || c.status === p.status)
      .map((c) => ({
        id: c.id, batchId: c.batchId, batchNo: c.batchNo, status: c.status, uploadedAt: c.uploadedAt,
        itemCount: c.items.length, needsReviewCount: c.items.filter((i) => i.needsReview).length,
        createdAt: this.qcCreated.get(c.id) ?? new Date().toISOString(),
      }))
    return this.delay(page(summaries, p.page, p.size))
  }

  async getQcChecklist(id: string) {
    return this.delay(this.findQc(id))
  }

  async createQcChecklist(batchId: string) {
    const batch = this.batches.find((b) => b.id === batchId)
    if (!batch) throw notFound('Партия')
    const existing = this.qc.find((c) => c.batchId === batchId)
    if (existing) throw new ApiError(409, `Протокол QC для партии ${batch.batchNo} уже существует: ${existing.id}`)
    const checklist: QcChecklist = {
      id: `qc-${batch.batchNo}`, batchId, batchNo: batch.batchNo, qualitySpecId: this.spec.id, status: 'NEW',
      imagePath: null, ocrConfidence: null, uploadedAt: null, qrVerified: null, pageCount: null, rejectReason: null,
      confirmedBy: null, confirmedAt: null, decisionNote: null, nonconformityActions: null, secondSignatory: null, items: [],
    }
    this.qc.unshift(checklist)
    this.qcCreated.set(checklist.id, new Date().toISOString())
    return this.delay(checklist)
  }

  async qcChecklistPdf(batchId: string) {
    if (!this.batches.some((b) => b.id === batchId)) throw notFound('Партия')
    return (await fetch(new URL('demo/qc-blank.svg', document.baseURI))).blob()
  }

  async qcChecklistFile(id: string) {
    const stored = this.files.get(id)
    if (stored) return stored
    if (id === 'qc-200') return (await fetch(new URL('demo/qc-protocol-200.svg', document.baseURI))).blob()
    throw notFound('Файл')
  }

  async uploadQcChecklist(id: string, file: File) {
    const c = this.findQc(id)
    if (!['NEW', 'UPLOADED', 'REJECTED'].includes(c.status)) {
      throw new ApiError(409, `Загрузка файла недоступна для протокола в статусе ${c.status}: подтверждённый документ нельзя заменить.`)
    }
    this.files.set(id, file)
    Object.assign(c, {
      status: 'UPLOADED', imagePath: `qc-checklists/${id}/${file.name}`, uploadedAt: new Date().toISOString(),
      pageCount: 1, items: [], qrVerified: null, rejectReason: null, ocrConfidence: null,
    })
    return this.delay(c)
  }

  /** Имитация распознавания: значения по спецификации, одно неуверенное и одно не найденное на листе. */
  async recognizeQcChecklist(id: string) {
    const c = this.findQc(id)
    if (c.status !== 'UPLOADED') throw new ApiError(409, `Распознавание доступно только для протокола в статусе UPLOADED, текущий: ${c.status}`)
    const phHistory = this.history.ph.find((p) => p.label === c.batchNo)?.value ?? 10.31
    const recognized: Record<string, { value: number | null; text: string | null; confidence: number }> = {
      'si-appearance': { value: null, text: allowedValues(this.specItem('si-appearance'))[1] ?? null, confidence: 0.95 },
      'si-density': { value: 1.118, text: null, confidence: 0.96 },
      'si-ph': { value: phHistory, text: null, confidence: 0.72 },
      'si-dry': { value: null, text: null, confidence: 0 },
    }
    c.items = this.spec.items.map((si, i): QcChecklistItem => {
      const r = recognized[si.id]
      const item: QcChecklistItem = {
        id: `${id}-qi-${i}`, qualitySpecItemId: si.id, parameterNameRaw: si.parameterCode ?? si.parameterName,
        measuredValue: r.value, textValue: r.text, confidence: r.confidence, needsReview: false, reviewNote: null,
        withinRange: null, correctedByOperator: false,
      }
      this.evaluate(item)
      const notes: string[] = []
      if (r.value === null && !r.text) notes.push('Параметр не найден на листе — внесите значение вручную.')
      else if (r.confidence < 0.9) notes.push(`Низкая уверенность распознавания (${Math.round(r.confidence * 100)}%).`)
      if (item.withinRange === false) notes.push('Значение вне допуска QC-спецификации.')
      item.needsReview = notes.length > 0
      item.reviewNote = notes.join(' ') || null
      return item
    })
    c.qrVerified = true
    c.ocrConfidence = 0.81
    c.status = 'WAITING_CONFIRMATION'
    return this.delay(c)
  }

  async confirmQcChecklist(id: string, request: ConfirmQcChecklistRequest) {
    const c = this.findQc(id)
    if (c.status !== 'WAITING_CONFIRMATION') {
      throw new ApiError(409, `Подтверждение доступно только из статуса WAITING_CONFIRMATION, текущий: ${c.status}`)
    }
    for (const corr of request.corrections) {
      const item = c.items.find((i) => i.id === corr.checklistItemId)
      if (!item) throw notFound('Строка протокола QC')
      if (corr.qualitySpecItemId != null) item.qualitySpecItemId = corr.qualitySpecItemId
      if (corr.measuredValue != null) item.measuredValue = corr.measuredValue
      if (corr.textValue != null) item.textValue = corr.textValue
      this.evaluate(item)
      item.correctedByOperator = true
      item.needsReview = false
    }
    if (c.items.length === 0) throw new ApiError(409, 'В протоколе нет ни одной строки — подтверждать нечего. Проверьте распознавание.')
    const unmatched = c.items.filter((i) => !this.specItem(i.qualitySpecItemId)).map((i) => i.parameterNameRaw ?? 'строка без названия')
    if (unmatched.length) {
      throw new ApiError(409, `Строки не сопоставлены с параметрами спецификации: ${unmatched.join(', ')}. Выберите параметр для каждой строки.`)
    }
    const ids = c.items.map((i) => i.qualitySpecItemId)
    const duplicated = [...new Set(ids.filter((x, i) => ids.indexOf(x) !== i))].map((x) => this.specItem(x)!.parameterName)
    if (duplicated.length) {
      throw new ApiError(409, `Параметр указан в протоколе несколько раз: ${duplicated.join(', ')}. У каждого параметра должна быть одна строка.`)
    }
    const incomplete = c.items.some((i) =>
      this.specItem(i.qualitySpecItemId)!.parameterType === 'QUALITATIVE' ? !i.textValue?.trim() : i.measuredValue === null)
    if (incomplete) throw new ApiError(409, 'Есть незаполненные строки протокола — заполните значения всех параметров перед подтверждением.')

    Object.assign(c, {
      status: 'CONFIRMED', confirmedBy: request.labTechnician, confirmedAt: new Date().toISOString(),
      decisionNote: request.decisionNote, nonconformityActions: request.nonconformityActions, secondSignatory: request.secondSignatory,
    })
    const batch = this.batches.find((b) => b.id === c.batchId)
    if (batch) batch.status = c.items.some((i) => i.withinRange !== true) ? 'REJECTED' : 'RELEASED'
    return this.delay(c)
  }

  async rejectQcChecklist(id: string, reason: string) {
    const c = this.findQc(id)
    if (c.status === 'CONFIRMED') throw new ApiError(409, 'Подтверждённый протокол нельзя отклонить.')
    c.status = 'REJECTED'
    c.rejectReason = reason
    return this.delay(c)
  }

  async getQualitySpec(id: string) {
    if (id !== this.spec.id) throw notFound('Спецификация качества')
    return this.delay(this.spec)
  }
}
