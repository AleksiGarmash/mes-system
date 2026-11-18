import { DemoApi } from './demoApi'

const file = new File(['fake'], 'photo.jpg', { type: 'image/jpeg' })

describe('демо-API ведёт себя как сервер', () => {
  it('полный цикл: фото → распознавание → подтверждение создаёт партию и списывает сырьё', async () => {
    const api = new DemoApi(0)
    const before = (await api.listLots({ materialCode: 'Компонент Б' })).items[0].quantityAvailable!

    await api.uploadChecklist('cl-246', file)
    const recognized = await api.recognizeChecklist('cl-246')
    expect(recognized.status).toBe('WAITING_CONFIRMATION')
    expect(recognized.items).toHaveLength(5)
    expect(recognized.items.some((i) => i.needsReview)).toBe(true)

    const done = await api.confirmChecklist('cl-246', {
      operator: 'Тест',
      corrections: recognized.items.map((i) => ({ checklistItemId: i.id })),
      controlValues: [
        { controlPointId: 'cp-ph-solution', measuredValue: 11 },
        { controlPointId: 'cp-ph-product', measuredValue: 10.2 },
        { controlPointId: 'cp-density', measuredValue: 1.12 },
      ],
    })
    expect(done.status).toBe('PRODUCTION_COMPLETED')
    expect(done.batchId).not.toBeNull()
    const after = (await api.listLots({ materialCode: 'Компонент Б' })).items[0].quantityAvailable!
    expect(after).toBeLessThan(before)
  })

  it('замер готового продукта вне допуска отправляет партию на карантин', async () => {
    const api = new DemoApi(0)
    const c = await api.getChecklist('cl-245')
    await api.confirmChecklist('cl-245', {
      operator: 'Тест',
      corrections: c.items.map((i) => ({ checklistItemId: i.id, ...(i.weight === null ? { weight: 701 } : {}) })),
      controlValues: [
        { controlPointId: 'cp-ph-solution', measuredValue: 11 },
        { controlPointId: 'cp-ph-product', measuredValue: 11.4 },
        { controlPointId: 'cp-density', measuredValue: 1.12 },
      ],
    })
    const batches = await api.listBatches({ status: 'QUARANTINE' })
    expect(batches.totalElements).toBe(1)
  })

  it('без контрольных замеров подтвердить нельзя (409)', async () => {
    const api = new DemoApi(0)
    const c = await api.getChecklist('cl-245')
    await expect(
      api.confirmChecklist('cl-245', { operator: 'Тест', corrections: c.items.map((i) => ({ checklistItemId: i.id, weight: i.weight ?? 700 })), controlValues: [] }),
    ).rejects.toMatchObject({ status: 409 })
  })

  it('подтверждённый документ нельзя заменить', async () => {
    const api = new DemoApi(0)
    await api.uploadChecklist('cl-246', file)
    await api.rejectChecklist('cl-246', 'размыто')
    await expect(api.uploadChecklist('cl-246', file)).resolves.toMatchObject({ status: 'UPLOADED' })
  })

  it('FEFO: просроченный лот пропускается, первым идёт ближайший срок', async () => {
    const api = new DemoApi(0)
    const a = await api.allocate('22222222-2222-4222-8222-222222222222', 200)
    expect(a.picks.map((p) => p.lotNo)).toEqual(['RM-2026-0390', 'RM-2026-0455'])
    expect(a.complete).toBe(true)
  })

  it('отзыв лота: карантин только для подходящих партий', async () => {
    const api = new DemoApi(0)
    const report = await api.recallReport('RM-2026-0390')
    expect(report.affectedBatches).toHaveLength(6)
    const result = await api.quarantineLot('RM-2026-0390', 'проверка')
    expect(result.quarantinedBatchNos.length + result.skippedBatchNos.length).toBe(6)
    const again = await api.quarantineLot('RM-2026-0390', 'повторно')
    expect(again.quarantinedBatchNos).toEqual([])
  })

  it('SPC по истории показывает сдвиг среднего', async () => {
    const api = new DemoApi(0)
    const spc = await api.spc({ recipeCode: 'TF-01', parameter: 'Водородный показатель pH', source: 'QC' })
    expect(spc.points).toHaveLength(30)
    expect(spc.report.signals.length).toBeGreaterThan(0)
  })
})
