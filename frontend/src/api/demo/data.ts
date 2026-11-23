import type { Batch, Checklist, Lot, Order, QcChecklist, QualitySpec, Recipe } from '../types'

/**
 * Демо-данные. Продукт вымышленный («Тестформ»), чтобы в публичный репозиторий не попала
 * реальная рецептура. Состав повторяет docs/examples/demo-recipe.json бэкенда.
 */

export const MATERIALS = {
  water: '11111111-1111-4111-8111-111111111111',
  compA: '22222222-2222-4222-8222-222222222222',
  compB: '33333333-3333-4333-8333-333333333333',
  modifier: '44444444-4444-4444-8444-444444444444',
  base: '55555555-5555-4555-8555-555555555555',
} as const

export function demoRecipe(): Recipe {
  return {
    id: 'recipe-tf01-v2',
    code: 'TF-01',
    name: 'Тестформ',
    version: 2,
    status: 'ACTIVE',
    qualitySpecCode: 'QC-TF-01',
    requireRawMaterialLot: true,
    stages: [
      { id: 'st1', stageNo: 1, name: 'Раствор-модификатор', plannedQty: 300 },
      { id: 'st2', stageNo: 2, name: 'Готовый продукт', plannedQty: 1000 },
    ],
    items: [
      { id: 'ri-water', materialId: MATERIALS.water, materialCode: 'Вода', targetQty: 150, unit: 'кг', tolerancePlus: 1, toleranceMinus: 0, sequenceNo: 1, stageNo: 1, sourceStageNo: null, mixSpeedNorm: null, mixMinutesNorm: null, note: null },
      { id: 'ri-compA', materialId: MATERIALS.compA, materialCode: 'Компонент А', targetQty: 90, unit: 'кг', tolerancePlus: 2, toleranceMinus: 0, sequenceNo: 2, stageNo: 1, sourceStageNo: null, mixSpeedNorm: 'не более 120', mixMinutesNorm: 5, note: 'вводить медленно' },
      { id: 'ri-compB', materialId: MATERIALS.compB, materialCode: 'Компонент Б', targetQty: 60, unit: 'кг', tolerancePlus: 2, toleranceMinus: 0, sequenceNo: 3, stageNo: 1, sourceStageNo: null, mixSpeedNorm: 'до 180', mixMinutesNorm: 10, note: null },
      { id: 'ri-modifier', materialId: MATERIALS.modifier, materialCode: 'Раствор-модификатор', targetQty: 300, unit: 'кг', tolerancePlus: 1, toleranceMinus: 0, sequenceNo: 1, stageNo: 2, sourceStageNo: 1, mixSpeedNorm: 'высокие', mixMinutesNorm: 20, note: null },
      { id: 'ri-base', materialId: MATERIALS.base, materialCode: 'Основа', targetQty: 700, unit: 'кг', tolerancePlus: 1, toleranceMinus: 0, sequenceNo: 2, stageNo: 2, sourceStageNo: null, mixSpeedNorm: null, mixMinutesNorm: null, note: null },
    ],
    controlPoints: [
      { id: 'cp-ph-solution', stageNo: 1, kind: 'PROCESS', parameterCode: 'pH раствора', parameterName: 'pH раствора', minValue: 10, maxValue: 12, unit: null, sequenceNo: 1 },
      { id: 'cp-ph-product', stageNo: null, kind: 'FINISHED_PRODUCT', parameterCode: 'pH готового продукта', parameterName: 'pH готового продукта', minValue: 10, maxValue: 10.5, unit: null, sequenceNo: 2 },
      { id: 'cp-density', stageNo: null, kind: 'FINISHED_PRODUCT', parameterCode: 'Плотность', parameterName: 'Плотность готового продукта', minValue: 1.1, maxValue: 1.14, unit: 'г/см³', sequenceNo: 3 },
    ],
  }
}

const DAY = 24 * 3600 * 1000
/**
 * Точка отсчёта демо — вчера, 08:00 UTC, а не фиксированная дата. Иначе демо «стареет»:
 * лот с запасом срока через пару недель становится просроченным, и меняется поведение FEFO
 * (так и упал тест в CI через 12 дней после написания). Считается при каждом вызове,
 * чтобы тесты могли подменить текущее время.
 */
function baseTime(): number {
  const d = new Date()
  d.setUTCHours(8, 0, 0, 0)
  return d.getTime() - DAY
}
const at = (daysAgo: number) => new Date(baseTime() - daysAgo * DAY).toISOString()
const isoDate = (daysFromBase: number) => new Date(baseTime() + daysFromBase * DAY).toISOString().slice(0, 10)

export function demoLots(): Lot[] {
  return [
    { id: 'lot-w1', lotNo: 'RM-2026-0412', materialId: MATERIALS.water, materialCode: 'Вода', supplier: 'Собственная водоподготовка', quantityAvailable: 2400, expiry: null },
    { id: 'lot-a1', lotNo: 'RM-2026-0390', materialId: MATERIALS.compA, materialCode: 'Компонент А', supplier: 'ООО «ХимСнаб»', quantityAvailable: 120, expiry: isoDate(12) },
    { id: 'lot-a2', lotNo: 'RM-2026-0455', materialId: MATERIALS.compA, materialCode: 'Компонент А', supplier: 'ООО «ХимСнаб»', quantityAvailable: 640, expiry: isoDate(95) },
    { id: 'lot-a0', lotNo: 'RM-2026-0301', materialId: MATERIALS.compA, materialCode: 'Компонент А', supplier: 'ООО «ХимСнаб»', quantityAvailable: 35, expiry: isoDate(-4) },
    { id: 'lot-b1', lotNo: 'RM-2026-0418', materialId: MATERIALS.compB, materialCode: 'Компонент Б', supplier: 'АО «Полимер»', quantityAvailable: 410, expiry: isoDate(40) },
    { id: 'lot-base1', lotNo: 'RM-2026-0377', materialId: MATERIALS.base, materialCode: 'Основа', supplier: 'АО «Кремний»', quantityAvailable: 1900, expiry: isoDate(160) },
    { id: 'lot-base2', lotNo: 'RM-2026-0466', materialId: MATERIALS.base, materialCode: 'Основа', supplier: 'АО «Кремний»', quantityAvailable: 6000, expiry: isoDate(250) },
  ]
}

export function demoOrders(): Order[] {
  const recipe = demoRecipe()
  const base = { recipeId: recipe.id, recipeCode: recipe.code, recipeVersion: recipe.version, mixerId: null }
  return [
    { ...base, id: 'order-246', orderNo: '246', plannedQty: 1000, actualQty: null, status: 'PLANNED', plannedDate: at(-1) },
    { ...base, id: 'order-245', orderNo: '245', plannedQty: 1000, actualQty: null, status: 'IN_PROGRESS', plannedDate: at(0) },
    { ...base, id: 'order-244', orderNo: '244', plannedQty: 1000, actualQty: 1004, status: 'COMPLETED', plannedDate: at(1) },
  ]
}

/** Чек-лист №245 после распознавания: три строки требуют внимания оператора. */
export function demoChecklists(): Checklist[] {
  const row = (id: string, recipeItemId: string, materialId: string, name: string, lotRaw: string | null, lotId: string | null,
    weight: number | null, confidence: number, needsReview: boolean, withinTolerance: boolean | null, reviewNote: string | null) => ({
    id, recipeItemId, materialId, materialNameRaw: name, batchNumberRaw: lotRaw, rawMaterialBatchId: lotId, weight, confidence,
    needsReview, withinTolerance, reviewNote, correctedByOperator: false, mixSpeedActual: null, mixMinutesActual: null, note: null,
  })
  return [
    {
      id: 'cl-245',
      productionOrderId: 'order-245',
      status: 'WAITING_CONFIRMATION',
      imagePath: 'checklists/cl-245/demo.svg',
      ocrConfidence: 0.88,
      uploadedAt: at(0.05),
      batchId: null,
      qrVerified: true,
      pageCount: 1,
      reviewSummary: null,
      controlRecords: [],
      items: [
        row('it-1', 'ri-water', MATERIALS.water, 'Вода', 'RM-2026-0412', 'lot-w1', 150.8, 0.97, false, true, null),
        row('it-2', 'ri-compA', MATERIALS.compA, 'Компонент А', 'RM-2026-0390', 'lot-a1', 91.2, 0.71, true, true,
          'Низкая уверенность распознавания (71%).'),
        row('it-3', 'ri-compB', MATERIALS.compB, 'Компонент Б', 'RM-2026-0418', 'lot-b1', 63.4, 0.95, true, false,
          'Вес вне допуска рецептуры.'),
        row('it-4', 'ri-modifier', MATERIALS.modifier, 'Раствор-модификатор', null, null, 301.5, 0.93, false, true, null),
        row('it-5', 'ri-base', MATERIALS.base, 'Основа', 'RM-2026-0466', 'lot-base2', null, 0, true, null,
          'Компонент не найден на листе — внесите значения вручную.'),
      ],
    },
    {
      id: 'cl-246',
      productionOrderId: 'order-246',
      status: 'NEW',
      imagePath: null,
      ocrConfidence: null,
      uploadedAt: null,
      batchId: null,
      qrVerified: null,
      pageCount: null,
      reviewSummary: null,
      controlRecords: [],
      items: [],
    },
  ]
}

/** История партий: 30 выпусков; последние смещены вверх по pH — на карте видно сдвиг. */
export function demoHistory() {
  const batches: Batch[] = []
  const ph: { at: string; label: string; value: number }[] = []
  const density: { at: string; label: string; value: number }[] = []
  // детерминированный «шум», чтобы демо выглядело одинаково при каждой загрузке
  const noise = (i: number, k: number) => Math.sin(i * 12.9898 + k * 78.233) * 0.5 + Math.sin(i * 4.1 + k) * 0.25
  for (let i = 0; i < 30; i++) {
    const daysAgo = 30 - i + 1
    const batchNo = `B-2026-${String(171 + i).padStart(6, '0')}`
    const drift = i >= 22 ? 0.11 : 0
    const phValue = Math.round((10.22 + drift + noise(i, 1) * 0.12) * 100) / 100
    const densityValue = Math.round((1.122 + noise(i, 2) * 0.006) * 1000) / 1000
    const rejected = phValue > 10.5
    batches.push({
      id: `batch-${i}`,
      batchNo,
      productionOrderId: `order-h${i}`,
      orderNo: String(214 + i),
      qty: 1000 + Math.round(noise(i, 3) * 8),
      // две последние партии ещё ждут контроля качества: у одной протокол уже распознан, у другой его нет
      status: i >= 28 ? 'PENDING_QC' : rejected ? 'REJECTED' : 'RELEASED',
      operator: i % 2 === 0 ? 'Иванов И. И.' : 'Петров П. С.',
      producedAt: at(daysAgo),
    })
    ph.push({ at: at(daysAgo - 0.2), label: batchNo, value: phValue })
    density.push({ at: at(daysAgo - 0.2), label: batchNo, value: densityValue })
  }
  return { batches: batches.reverse(), ph, density }
}

/** Какие лоты сырья ушли в какие партии (для родословной и отзыва). */
export function demoUsage(batches: Batch[]) {
  // лот Компонента А RM-2026-0390 использован в последних шести партиях — его можно «отозвать» в демо
  return batches.slice(0, 6).map((b) => ({ batchNo: b.batchNo, lotNo: 'RM-2026-0390', qty: 90 + (b.qty ?? 1000) % 3 }))
}

/** Спецификация качества «Тестформ»: те же параметры, что в родословной партий и на контрольной карте. */
export function demoQualitySpec(): QualitySpec {
  return {
    id: 'spec-qc-tf01',
    code: 'QC-TF-01',
    name: 'Контроль качества: Тестформ',
    version: 1,
    status: 'ACTIVE',
    items: [
      { id: 'si-appearance', parameterCode: 'Внешний вид', parameterName: 'Внешний вид', minValue: null, maxValue: null, unit: null,
        sequenceNo: 1, parameterType: 'QUALITATIVE', allowedValues: 'однородная жидкость;однородная жидкость без осадка' },
      { id: 'si-density', parameterCode: 'Плотность', parameterName: 'Плотность', minValue: 1.05, maxValue: 1.15, unit: 'г/см³',
        sequenceNo: 2, parameterType: 'NUMERIC', allowedValues: null },
      { id: 'si-ph', parameterCode: 'pH', parameterName: 'Водородный показатель pH', minValue: 9.5, maxValue: 10.5, unit: null,
        sequenceNo: 3, parameterType: 'NUMERIC', allowedValues: null },
      { id: 'si-dry', parameterCode: 'Сухой остаток', parameterName: 'Сухой остаток', minValue: 20, maxValue: 24, unit: '%',
        sequenceNo: 4, parameterType: 'NUMERIC', allowedValues: null },
    ],
  }
}

/**
 * Протокол партии B-2026-000200 после распознавания. На бланке pH записан как 10,38,
 * а OCR прочитал 10,88 — значение «вне нормы»; лаборант сверяет с фото и исправляет.
 * Плотность распознана неуверенно, сухой остаток OCR не нашёл.
 */
export function demoQcChecklists(): QcChecklist[] {
  const row = (id: string, specId: string, name: string, value: number | null, text: string | null, confidence: number,
    needsReview: boolean, withinRange: boolean | null, reviewNote: string | null) => ({
    id, qualitySpecItemId: specId, parameterNameRaw: name, measuredValue: value, textValue: text, confidence,
    needsReview, withinRange, reviewNote, correctedByOperator: false,
  })
  return [
    {
      id: 'qc-200',
      batchId: 'batch-29',
      batchNo: 'B-2026-000200',
      qualitySpecId: 'spec-qc-tf01',
      status: 'WAITING_CONFIRMATION',
      imagePath: 'qc-checklists/qc-200/demo.svg',
      ocrConfidence: 0.84,
      uploadedAt: at(0.02),
      qrVerified: true,
      pageCount: 1,
      rejectReason: null,
      confirmedBy: null,
      confirmedAt: null,
      decisionNote: null,
      nonconformityActions: null,
      secondSignatory: null,
      items: [
        row('qi-1', 'si-appearance', 'Внешний вид', null, 'однородная жидкость', 0.93, false, true, null),
        row('qi-2', 'si-density', 'Плотность', 1.121, null, 0.74, true, true, 'Низкая уверенность распознавания (74%).'),
        row('qi-3', 'si-ph', 'pH', 10.88, null, 0.91, true, false, 'Значение вне допуска QC-спецификации.'),
        row('qi-4', 'si-dry', 'Сухой остаток', null, null, 0, true, null, 'Параметр не найден на листе — внесите значение вручную.'),
      ],
    },
  ]
}
