<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { waitlistApi, type WaitEntry, type Inbox } from '../../apis/waitlist'
const entries = ref<WaitEntry[]>([]),
  messages = ref<Inbox[]>([]),
  busy = ref(false),
  error = ref('')
const labels: Record<string, string> = {
  WAITING: '排队中',
  OFFERED: '待确认',
  ACCEPTED: '已确认',
  CANCELLED: '已取消',
  EXPIRED: '已超时',
  CLOSED: '已终止',
}
async function load() {
  entries.value = await waitlistApi.list()
  messages.value = await waitlistApi.inbox()
}
async function run(fn: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busy.value = false
  }
}
async function accept(e: WaitEntry) {
  if (
    !window.confirm(
      `确认将候补 #${e.id} 转为原就诊人的正式预约？${e.doctorName} · ${e.campusName} · ${e.startAt}—${e.endAt} · ${e.patientName}。`,
    )
  )
    return
  await run(async () => {
    await waitlistApi.accept(e.id)
    await load()
  })
}
async function cancel(e: WaitEntry) {
  if (!window.confirm('确认退出候补并释放保留号源？')) return
  await run(async () => {
    await waitlistApi.cancel(e.id)
    await load()
  })
}
async function read(m: Inbox) {
  await run(async () => {
    await waitlistApi.read(m.id)
    await load()
  })
}
let timer: ReturnType<typeof setInterval>
onMounted(() => {
  void run(load)
  timer = setInterval(() => void run(load), 5000)
})
onUnmounted(() => clearInterval(timer))
</script>
<template>
  <section class="panel wait-panel">
    <div class="section-heading">
      <h3>我的候补</h3>
      <button :disabled="busy" @click="run(load)">刷新</button>
    </div>
    <p class="muted">
      按加入顺序分配号源。获得号源后最多保留10分钟，确认后生成正式预约；逾期自动释放。最近100条。
    </p>
    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <article v-for="e in entries" :key="e.id" class="wait-row">
      <b>候补 #{{ e.id }} · {{ labels[e.status] }}</b>
      <p>{{ e.doctorName }} · {{ e.campusName }} · {{ e.patientName }}</p>
      <p>{{ e.startAt.replace('T', ' ') }}—{{ e.endAt.slice(11, 16) }}</p>
      <p v-if="e.status === 'OFFERED'">
        确认截止：{{ e.expiresAt?.replace('T', ' ') }}（北京时间）
      </p>
      <p v-if="e.appointmentId">新预约 #{{ e.appointmentId }}</p>
      <div class="actions">
        <button v-if="e.status === 'OFFERED'" class="primary" :disabled="busy" @click="accept(e)">
          确认候补</button
        ><button
          v-if="['WAITING', 'OFFERED'].includes(e.status)"
          :disabled="busy"
          @click="cancel(e)"
        >
          退出候补
        </button>
      </div>
    </article>
    <p v-if="!entries.length">暂无候补。号源已满时可在选择时段页面加入候补。</p>
    <h3>消息中心</h3>
    <article v-for="m in messages" :key="m.id" class="wait-row">
      <p>{{ m.message }}</p>
      <button v-if="!m.readAt" :disabled="busy" @click="read(m)">标为已读</button
      ><span v-else class="muted">已读</span>
    </article>
    <p v-if="!messages.length">暂无消息。</p>
  </section>
</template>
<style scoped>
.wait-panel {
  padding: 24px;
}
.wait-row {
  padding: 18px 0;
  border-top: 1px solid #e2e8f0;
  overflow-wrap: anywhere;
}
.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
