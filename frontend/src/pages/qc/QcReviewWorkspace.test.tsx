import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiProvider } from '../../api/ApiContext'
import { DemoApi } from '../../api/demo/demoApi'
import { demoQcChecklists, demoQualitySpec } from '../../api/demo/data'
import { QcReviewWorkspace } from './QcReviewWorkspace'

function setup() {
  const api = new DemoApi(0)
  const confirmSpy = vi.spyOn(api, 'confirmQcChecklist')
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ApiProvider api={api}>
        <QcReviewWorkspace protocol={demoQcChecklists()[0]} spec={demoQualitySpec()} labTechnician="Тестовый лаборант" />
      </ApiProvider>
    </QueryClientProvider>,
  )
  return { confirmSpy, user: userEvent.setup() }
}

const rowOf = (parameter: string) => screen.getByText(parameter, { selector: '.material' }).closest('tr')!

describe('экран проверки протокола качества', () => {
  it('показывает состояние параметров словами и норму рядом со значением', () => {
    setup()
    expect(within(rowOf('Внешний вид')).getByText('Норма')).toBeInTheDocument()
    expect(within(rowOf('Плотность')).getByText('Проверить')).toBeInTheDocument()
    expect(within(rowOf('Водородный показатель pH')).getByText('Вне нормы')).toBeInTheDocument()
    expect(within(rowOf('Водородный показатель pH')).getByText('9,5 – 10,5')).toBeInTheDocument()
    expect(within(rowOf('Сухой остаток')).getByText('Нет значения')).toBeInTheDocument()
  })

  it('до разбора строк подтверждение недоступно', () => {
    setup()
    expect(screen.getByRole('button', { name: 'Подтвердить протокол' })).toBeDisabled()
    expect(screen.getByText('Осталось проверить строк: 2')).toBeInTheDocument()
  })

  it('исправление ошибки OCR меняет итог на «выпустить» и уходит на сервер', async () => {
    const { confirmSpy, user } = setup()

    await user.click(within(rowOf('Плотность')).getByRole('button', { name: 'Верно' }))
    await user.click(within(rowOf('Водородный показатель pH')).getByRole('button', { name: 'Исправить' }))
    await user.clear(screen.getByLabelText('Значение'))
    await user.type(screen.getByLabelText('Значение'), '10,38')
    await user.click(screen.getByRole('button', { name: 'Готово' }))
    await user.click(within(rowOf('Сухой остаток')).getByRole('button', { name: 'Исправить' }))
    await user.type(screen.getByLabelText('Значение'), '22,4')
    await user.click(screen.getByRole('button', { name: 'Готово' }))

    expect(screen.getByText('Все параметры в норме — партия будет выпущена')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Подтвердить и выпустить партию' }))

    expect(confirmSpy).toHaveBeenCalledWith('qc-200', expect.objectContaining({
      labTechnician: 'Тестовый лаборант',
      corrections: [
        { checklistItemId: 'qi-2' },
        { checklistItemId: 'qi-3', measuredValue: 10.38 },
        { checklistItemId: 'qi-4', measuredValue: 22.4 },
      ],
    }))
  })

  it('если значение действительно вне нормы, кнопка прямо говорит, что партия будет забракована', async () => {
    const { user } = setup()
    await user.click(within(rowOf('Плотность')).getByRole('button', { name: 'Верно' }))
    await user.click(within(rowOf('Водородный показатель pH')).getByRole('button', { name: 'Так в протоколе' }))
    await user.click(within(rowOf('Сухой остаток')).getByRole('button', { name: 'Исправить' }))
    await user.type(screen.getByLabelText('Значение'), '22,4')
    await user.click(screen.getByRole('button', { name: 'Готово' }))

    expect(screen.getByText('Партия будет забракована: Водородный показатель pH')).toBeInTheDocument()
    expect(screen.getByLabelText('Мероприятия по несоответствиям')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Подтвердить и забраковать партию' })).toBeEnabled()
  })
})
