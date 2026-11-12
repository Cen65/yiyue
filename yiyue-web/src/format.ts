export function dateLabel(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(value))
}
export function timeLabel(value: string) {
  return value.slice(11, 16)
}
export function remaining(capacity: number, reserved: number) {
  return Math.max(0, capacity - reserved)
}
export const statusLabels: Record<string, string> = {
  BOOKED: '预约成功',
  CANCELLED: '已取消',
  CHECKED_IN: '已签到',
  COMPLETED: '已完成',
}
