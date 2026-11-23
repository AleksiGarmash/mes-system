// Типы повторяют DTO бэкенда (com.mes.dto.response / request). Числа BigDecimal приходят как number,
// Instant и LocalDate — как ISO-строки.

export type Uuid = string

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Me {
  username: string
  roles: Role[]
}

export type Role = 'ADMIN' | 'TECHNOLOGIST' | 'PLANNER' | 'OPERATOR' | 'LAB_TECHNICIAN' | 'STOREKEEPER'

export type OrderStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type BatchStatus = 'IN_PRODUCTION' | 'PENDING_QC' | 'RELEASED' | 'REJECTED' | 'QUARANTINE'
export type ChecklistStatus =
  | 'NEW'
  | 'UPLOADED'
  | 'OCR_COMPLETE'
  | 'WAITING_CONFIRMATION'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'PRODUCTION_COMPLETED'
export type QcChecklistStatus = 'NEW' | 'UPLOADED' | 'OCR_COMPLETE' | 'WAITING_CONFIRMATION' | 'CONFIRMED' | 'REJECTED'
export type QcResult = 'PENDING' | 'PASS' | 'FAIL'
export type ControlPointKind = 'PROCESS' | 'FINISHED_PRODUCT'

export interface Order {
  id: Uuid
  orderNo: string
  recipeId: Uuid
  recipeCode: string
  recipeVersion: number
  plannedQty: number
  actualQty: number | null
  status: OrderStatus
  mixerId: Uuid | null
  plannedDate: string | null
}

export interface RecipeStage {
  id: Uuid
  stageNo: number
  name: string
  plannedQty: number | null
}

export interface RecipeItem {
  id: Uuid
  materialId: Uuid
  materialCode: string | null
  targetQty: number
  unit: string | null
  tolerancePlus: number
  toleranceMinus: number
  sequenceNo: number | null
  stageNo: number | null
  sourceStageNo: number | null
  mixSpeedNorm: string | null
  mixMinutesNorm: number | null
  note: string | null
}

export interface RecipeControlPoint {
  id: Uuid
  stageNo: number | null
  kind: ControlPointKind
  parameterCode: string | null
  parameterName: string
  minValue: number | null
  maxValue: number | null
  unit: string | null
  sequenceNo: number | null
}

export interface Recipe {
  id: Uuid
  code: string
  name: string
  version: number
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  qualitySpecCode: string | null
  requireRawMaterialLot: boolean
  stages: RecipeStage[]
  items: RecipeItem[]
  controlPoints: RecipeControlPoint[]
}

export interface ChecklistSummary {
  id: Uuid
  productionOrderId: Uuid
  orderNo: string
  recipeCode: string
  status: ChecklistStatus
  uploadedAt: string | null
  pageCount: number | null
  itemCount: number
  needsReviewCount: number
  createdAt: string
}

export interface ChecklistItem {
  id: Uuid
  materialNameRaw: string | null
  materialId: Uuid | null
  batchNumberRaw: string | null
  rawMaterialBatchId: Uuid | null
  weight: number | null
  confidence: number | null
  needsReview: boolean
  reviewNote: string | null
  withinTolerance: boolean | null
  correctedByOperator: boolean
  recipeItemId: Uuid | null
  mixSpeedActual: number | null
  mixMinutesActual: number | null
  note: string | null
}

export interface ControlRecord {
  id: Uuid
  controlPointId: Uuid | null
  kind: ControlPointKind
  stageNo: number | null
  parameterName: string
  expectedMin: number | null
  expectedMax: number | null
  measuredValue: number | null
  withinRange: boolean
}

export interface Checklist {
  id: Uuid
  productionOrderId: Uuid
  status: ChecklistStatus
  imagePath: string | null
  ocrConfidence: number | null
  uploadedAt: string | null
  batchId: Uuid | null
  qrVerified: boolean | null
  pageCount: number | null
  reviewSummary: string | null
  items: ChecklistItem[]
  controlRecords: ControlRecord[]
}

export interface ChecklistCorrection {
  checklistItemId: Uuid
  materialId?: Uuid | null
  rawMaterialBatchId?: Uuid | null
  weight?: number | null
  mixSpeedActual?: number | null
  mixMinutesActual?: number | null
  note?: string | null
}

export interface ConfirmChecklistRequest {
  operator: string
  corrections: ChecklistCorrection[]
  controlValues: { controlPointId: Uuid; measuredValue: number }[]
}

export type QcParameterType = 'NUMERIC' | 'QUALITATIVE'

export interface QualitySpecItem {
  id: Uuid
  parameterCode: string | null
  parameterName: string
  minValue: number | null
  maxValue: number | null
  unit: string | null
  sequenceNo: number | null
  parameterType: QcParameterType
  /** Допустимые значения качественного параметра через «;». */
  allowedValues: string | null
}

export interface QualitySpec {
  id: Uuid
  code: string
  name: string
  version: number
  status: 'DRAFT' | 'ACTIVE' | 'ARCHIVED'
  items: QualitySpecItem[]
}

export interface QcChecklistItem {
  id: Uuid
  parameterNameRaw: string | null
  qualitySpecItemId: Uuid | null
  measuredValue: number | null
  textValue: string | null
  confidence: number | null
  needsReview: boolean
  reviewNote: string | null
  withinRange: boolean | null
  correctedByOperator: boolean
}

export interface QcChecklist {
  id: Uuid
  batchId: Uuid
  batchNo: string
  qualitySpecId: Uuid | null
  status: QcChecklistStatus
  imagePath: string | null
  ocrConfidence: number | null
  uploadedAt: string | null
  qrVerified: boolean | null
  pageCount: number | null
  rejectReason: string | null
  confirmedBy: string | null
  confirmedAt: string | null
  decisionNote: string | null
  nonconformityActions: string | null
  secondSignatory: string | null
  items: QcChecklistItem[]
}

export interface QcChecklistSummary {
  id: Uuid
  batchId: Uuid
  batchNo: string
  status: QcChecklistStatus
  uploadedAt: string | null
  itemCount: number
  needsReviewCount: number
  createdAt: string
}

export interface QcCorrection {
  checklistItemId: Uuid
  qualitySpecItemId?: Uuid | null
  measuredValue?: number | null
  textValue?: string | null
}

export interface ConfirmQcChecklistRequest {
  labTechnician: string
  corrections: QcCorrection[]
  decisionNote: string | null
  nonconformityActions: string | null
  secondSignatory: string | null
}

export interface Batch {
  id: Uuid
  batchNo: string
  productionOrderId: Uuid
  orderNo: string
  qty: number | null
  status: BatchStatus
  operator: string | null
  producedAt: string | null
}

export interface BatchGenealogy {
  batchNo: string
  status: BatchStatus
  orderNo: string
  recipeCode: string
  recipeVersion: number
  producedAt: string | null
  components: {
    materialCode: string | null
    stageNo: number | null
    lotNo: string | null
    supplier: string | null
    expiry: string | null
    targetQty: number
    actualQty: number
    withinTolerance: boolean
    mixSpeedActual: number | null
    mixMinutesActual: number | null
  }[]
  qualityChecks: {
    parameter: string
    result: QcResult
    measuredValue: number | null
    textValue: string | null
    expectedMin: number | null
    expectedMax: number | null
    expectedText: string | null
  }[]
}

export interface RecallReport {
  lotNo: string
  materialCode: string | null
  supplier: string | null
  expiry: string | null
  affectedBatches: {
    batchNo: string
    status: BatchStatus
    producedAt: string | null
    orderNo: string
    recipeCode: string
    usedQty: number
  }[]
}

export interface QuarantineResult {
  lotNo: string
  quarantinedBatchNos: string[]
  skippedBatchNos: string[]
}

export interface Lot {
  id: Uuid
  lotNo: string
  materialId: Uuid
  materialCode: string | null
  supplier: string | null
  quantityAvailable: number | null
  expiry: string | null
}

export interface Allocation {
  picks: { lotId: Uuid; lotNo: string; expiry: string | null; quantity: number }[]
  shortage: number
  complete: boolean
}

export type SpcSource = 'QC' | 'PROCESS'

export interface SpcResult {
  recipeCode: string
  parameter: string
  source: SpcSource
  lsl: number | null
  usl: number | null
  points: { at: string; label: string | null; value: number; outOfSpec: boolean }[]
  report: {
    n: number
    mean: number
    stdDev: number
    sigmaWithin: number
    centerLine: number
    ucl: number
    lcl: number
    cp: number | null
    cpk: number | null
    ppk: number | null
    sufficientData: boolean
    signals: { rule: string; index: number; description: string }[]
  }
}

/** Ошибка API в формате ApiError бэкенда. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}
