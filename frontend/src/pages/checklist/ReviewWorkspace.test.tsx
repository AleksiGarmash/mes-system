import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiProvider } from '../../api/ApiContext'
import { DemoApi } from '../../api/demo/demoApi'
import { demoChecklists, demoOrders, demoRecipe } from '../../api/demo/data'
import { ReviewWorkspace } from './ReviewWorkspace'

function setup() {
  const api = new DemoApi(0)
  const confirmSpy = vi.spyOn(api, 'confirmChecklist')
  const checklist = demoChecklists()[0]
  const order = demoOrders().find((o) => o.id === checklist.productionOrderId)!
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ApiProvider api={api}>
        <ReviewWorkspace checklist={checklist} order={order} recipe={demoRecipe()} operator="Тестовый оператор" />
      </ApiProvider>
    </QueryClientProvider>,
  )
  return { confirmSpy, user: userEvent.setup() }
}

const rowOf = (material: string) => screen.getByText(material, { selector: '.material' }).closest('tr')!

describe('экран проверки распознанного листа', () => {
  it('показывает состояние каждой строки словами, а не только цветом', () => {
    setup()
    expect(within(rowOf('Вода')).getByText('Норма')).toBeInTheDocument()
    expect(within(rowOf('Компонент А')).getByText('Проверить')).toBeInTheDocument()
    expect(within(rowOf('Компонент Б')).getByText('Вне допуска')).toBeInTheDocument()
    expect(within(rowOf('Основа')).getByText('Нет значения')).toBeInTheDocument()
  })

  it('не даёт подтвердить, пока строки не разобраны, и объясняет почему', () => {
    setup()
    expect(screen.getByRole('button', { name: 'Подтвердить и создать партию' })).toBeDisabled()
    expect(screen.getByText('Осталось проверить строк: 3')).toBeInTheDocument()
  })

  it('после разбора строк и ввода замеров отправляет исправления на сервер', async () => {
    const { confirmSpy, user } = setup()

    await user.click(within(rowOf('Компонент А')).getByRole('button', { name: 'Верно' }))
    await user.click(within(rowOf('Компонент Б')).getByRole('button', { name: 'Так на бланке' }))
    await user.click(within(rowOf('Основа')).getByRole('button', { name: 'Исправить' }))
    await user.type(screen.getByLabelText('Фактический вес, кг'), '702,5')
    await user.click(screen.getByRole('button', { name: 'Готово' }))

    await user.type(screen.getByLabelText('pH раствора'), '10,9')
    await user.type(screen.getByLabelText('pH готового продукта'), '10,3')
    await user.type(screen.getByLabelText('Плотность готового продукта'), '1,121')

    const confirm = screen.getByRole('button', { name: 'Подтвердить и создать партию' })
    expect(confirm).toBeEnabled()
    expect(screen.getByText('Вне допуска строк: 1. Они будут записаны как отклонения.')).toBeInTheDocument()
    await user.click(confirm)

    expect(confirmSpy).toHaveBeenCalledWith('cl-245', {
      operator: 'Тестовый оператор',
      corrections: [
        { checklistItemId: 'it-2' },
        { checklistItemId: 'it-3' },
        { checklistItemId: 'it-5', weight: 702.5 },
      ],
      controlValues: [
        { controlPointId: 'cp-ph-solution', measuredValue: 10.9 },
        { controlPointId: 'cp-ph-product', measuredValue: 10.3 },
        { controlPointId: 'cp-density', measuredValue: 1.121 },
      ],
    })
  })

  it('отмена исправления возвращает распознанное значение', async () => {
    const { user } = setup()
    await user.click(within(rowOf('Компонент Б')).getByRole('button', { name: 'Исправить' }))
    const input = screen.getByLabelText('Фактический вес, кг')
    await user.clear(input)
    await user.type(input, '60')
    await user.click(screen.getByRole('button', { name: 'Отмена' }))
    expect(within(rowOf('Компонент Б')).getByText('63,4')).toBeInTheDocument()
    expect(within(rowOf('Компонент Б')).getByText('Вне допуска')).toBeInTheDocument()
  })
})
