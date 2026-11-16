import { describe, it, expect } from 'vitest'
import { remaining, timeLabel } from './format'
describe('号源展示', () => {
  it('余量不显示负数', () => {
    expect(remaining(5, 3)).toBe(2)
    expect(remaining(5, 8)).toBe(0)
  })
  it('按机构时间展示时段，不做 UTC 偏移', () => {
    expect(timeLabel('2027-01-01T09:30:00')).toBe('09:30')
  })
})
