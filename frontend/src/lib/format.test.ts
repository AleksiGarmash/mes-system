import { num, parseNumber, range } from './format'

describe('разбор чисел, введённых оператором', () => {
  it.each([
    ['91,2', 91.2],
    ['91.2', 91.2],
    [' 1 004,5 ', 1004.5],
    ['1 004', 1004],
    ['-0,5', -0.5],
  ])('%s → %s', (input, expected) => {
    expect(parseNumber(input)).toBe(expected)
  })
  it.each(['', 'abc', '1,2,3', '-', '.', '12кг'])('«%s» → null', (input) => {
    expect(parseNumber(input)).toBeNull()
  })
})

describe('форматирование', () => {
  it('русская запятая и тире для пустого значения', () => {
    expect(num(91.25)).toBe('91,25')
    expect(num(null)).toBe('—')
    expect(num(10, 2)).toBe('10,00')
  })
  it('диапазон допуска', () => {
    expect(range(10, 10.5)).toBe('10 – 10,5')
    expect(range(9, null, 'г/см³')).toBe('≥ 9 г/см³')
    expect(range(null, 11)).toBe('≤ 11')
    expect(range(null, null)).toBe('—')
  })
})
