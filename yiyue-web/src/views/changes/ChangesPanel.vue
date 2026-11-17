<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { changesApi, type ChangeJob, type ChangeItem } from '../../apis/changes'
import { catalogApi } from '../../apis/catalog'
import type { Catalog, Schedule, Slot } from '../../types'
import { dateLabel, timeLabel } from '../../utils/format'
const props = defineProps<{ admin: boolean; catalog: Catalog; source?: Schedule | null }>()
const emit = defineEmits<{ changed: []; close: [] }>()
const jobs = ref<ChangeJob[]>([]),
  records = ref<ChangeItem[]>([]),
  schedules = ref<Schedule[]>([])
const active = ref<ChangeJob | null>(null),
  busy = ref(false),
  error = ref(''),
  notice = ref('')
const kind = ref('STOP'),
  reason = ref(''),
  replacement = ref(''),
  targets = ref<Record<number, string>>({})
const selected = ref<ChangeItem | null>(null),
  slots = ref<Slot[]>([]),
  slotId = ref(''),
  consent = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
const status = (value: string) =>
  ({
    PENDING: '待处理',
    PROCESSING: '处理中',
    DONE: '已处理',
    FAILED: '处理失败',
    ACCEPTED: '已确认改约',
    DECLINED: '已拒绝建议',
  })[value] || value
function label(id: number | null) {
  const s = schedules.value.find((s) => s.id === id)
  if (!s) return '建议排班已关闭，请联系机构'
  return `${props.catalog.doctors.find((d) => d.id === s.doctorId)?.name || '医生'} · ${props.catalog.campuses.find((c) => c.id === s.campusId)?.name || '院区'} · ${dateLabel(s.startAt)} ${timeLabel(s.startAt)}—${timeLabel(s.endAt)}`
}
async function load() {
  schedules.value = await catalogApi.schedules()
  if (props.admin) {
    jobs.value = await changesApi.list()
    if (active.value) {
      active.value = jobs.value.find((j) => j.id === active.value?.id) || null
      if (active.value) records.value = await changesApi.items(active.value.id)
    }
  } else records.value = await changesApi.mine()
}
async function run(fn: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busy.value = false
  }
}
async function create() {
  await run(async () => {
    if (!props.source) return
    // 停诊与清单由后端同一事务提交；页面只在成功后切换。
    await changesApi.create(props.source.id, {
      kind: kind.value,
      reason: reason.value,
      replacementDoctorId: replacement.value ? Number(replacement.value) : undefined,
    })
    emit('changed')
    emit('close')
  })
}
async function open(job: ChangeJob) {
  await run(async () => {
    active.value = job
    records.value = await changesApi.items(job.id)
  })
}
async function process(job: ChangeJob, retry = false) {
  await run(async () => {
    if (retry) await changesApi.retry(job.id)
    await changesApi.process(job.id)
    await load()
    notice.value = '处理结果已更新'
  })
}
function candidates() {
  return schedules.value.filter(
    (s) =>
      s.campusId === active.value?.campusId &&
      s.departmentId === active.value?.departmentId &&
      new Date(s.startAt) > new Date(),
  )
}
async function propose(item: ChangeItem) {
  await run(async () => {
    await changesApi.propose(item.id, Number(targets.value[item.id]))
    await load()
    notice.value = '改约建议已保存，等待患者选择'
  })
}
async function choose(item: ChangeItem) {
  await run(async () => {
    if (!item.targetScheduleId) return
    slots.value = await catalogApi.slots(item.targetScheduleId)
    selected.value = item
    consent.value = false
    slotId.value = ''
  })
}
async function accept() {
  await run(async () => {
    if (!selected.value || !consent.value || !slotId.value) return
    // 明确同意后才发起占号，失败时保留选择供患者调整。
    await changesApi.accept(selected.value.id, Number(slotId.value))
    selected.value = null
    await load()
    notice.value = '改约成功，请在我的预约查看新预约'
    emit('changed')
  })
}
async function decline(item: ChangeItem) {
  if (!window.confirm('确认不接受本次改约建议？原预约仍将取消，您可自行重新预约。')) return
  await run(async () => {
    await changesApi.decline(item.id)
    await load()
  })
}
onMounted(() => {
  if (props.source) return
  void run(load)
  // 页面离开时停止轮询；确认弹窗期间不覆盖患者正在查看的建议。
  if (!props.source)
    timer = setInterval(() => {
      if (!busy.value && !selected.value) void run(load)
    }, 5000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
<template>
  <section class="panel change-panel">
    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <p v-if="notice" class="alert success" role="status">{{ notice }}</p>
    <form v-if="source" @submit.prevent="create">
      <h2>排班变更</h2>
      <p>排班 #{{ source.id }} · {{ dateLabel(source.startAt) }} {{ timeLabel(source.startAt) }}</p>
      <p class="muted">
        提交后立即关闭原排班，逐单取消未签到预约。替诊仅提供新排班建议，须经患者明确确认。
      </p>
      <label
        >变更方式<select v-model="kind">
          <option value="STOP">停诊</option>
          <option value="SUBSTITUTE">替诊</option>
        </select></label
      >
      <label v-if="kind === 'SUBSTITUTE'"
        >替诊医生<select v-model="replacement" required>
          <option value="">请选择</option>
          <option
            v-for="d in catalog.doctors.filter((d) => d.id !== source?.doctorId)"
            :key="d.id"
            :value="String(d.id)"
          >
            {{ d.name }}
          </option>
        </select></label
      >
      <label
        >变更原因<input v-model="reason" required maxlength="200" placeholder="向患者说明变更原因"
      /></label>
      <div class="modal-actions">
        <button type="button" :disabled="busy" @click="emit('close')">返回</button
        ><button class="primary" :disabled="busy || !reason.trim()">确认关闭原排班</button>
      </div>
    </form>
    <template v-else>
      <div class="section-heading">
        <h3>{{ admin ? '排班变更任务' : '我的变更通知' }}</h3>
        <button :disabled="busy" @click="run(load)">刷新</button>
      </div>
      <p class="muted">
        {{
          admin
            ? '最近100个任务。系统每5秒处理一批，失败3次后可人工重试。'
            : '原预约取消后可选择接受或拒绝建议。新号源在您确认时占用，实时余量以提交结果为准。'
        }}
      </p>
      <div v-if="admin" class="change-jobs">
        <article v-for="job in jobs" :key="job.id" class="change-record">
          <b
            >#{{ job.id }} · {{ job.kind === 'STOP' ? '停诊' : '替诊' }} ·
            {{ status(job.status) }}</b
          >
          <p>{{ job.reason }}</p>
          <p>已处理 {{ job.done }} / {{ job.total }} · 失败 {{ job.failed }}</p>
          <div class="change-actions">
            <button :disabled="busy" @click="open(job)">影响清单</button
            ><button v-if="job.status === 'PROCESSING'" :disabled="busy" @click="process(job)">
              处理下一批</button
            ><button v-if="job.failed" :disabled="busy" @click="process(job, true)">
              重试失败项
            </button>
          </div>
        </article>
        <p v-if="!jobs.length">暂无排班变更。</p>
      </div>
      <h3 v-if="admin && active">任务 #{{ active.id }} · 影响清单</h3>
      <article v-for="item in records" :key="item.id" class="change-record">
        <b>原预约 #{{ item.appointmentId }} · {{ status(item.status) }}</b>
        <p>{{ item.reason }}</p>
        <p v-if="item.lastError" class="alert error">
          {{ item.lastError }}（尝试{{ item.attempts }}次）
        </p>
        <p>
          {{ item.decision === 'PENDING' ? '尚未确认改约' : status(item.decision)
          }}<span v-if="item.newAppointmentId"> · 新预约 #{{ item.newAppointmentId }}</span>
        </p>
        <p v-if="item.targetScheduleId">改约建议：{{ label(item.targetScheduleId) }}</p>
        <div v-if="admin && item.decision === 'PENDING'" class="change-actions">
          <select v-model="targets[item.id]" aria-label="建议排班">
            <option value="">选择同院区同科室排班</option>
            <option v-for="s in candidates()" :key="s.id" :value="String(s.id)">
              {{ label(s.id) }}
            </option></select
          ><button :disabled="busy || !targets[item.id]" @click="propose(item)">
            提供改约建议
          </button>
        </div>
        <div v-if="!admin && item.decision === 'PENDING'" class="change-actions">
          <button
            v-if="item.targetScheduleId && item.status === 'DONE'"
            class="primary"
            :disabled="busy"
            @click="choose(item)"
          >
            查看建议并选择时段</button
          ><button :disabled="busy" @click="decline(item)">不接受改约建议</button>
        </div>
      </article>
      <p v-if="!admin && !records.length">暂无变更通知。</p>
    </template>
  </section>
  <div v-if="selected" class="overlay">
    <section class="modal">
      <h2>确认改约</h2>
      <p>{{ label(selected.targetScheduleId) }}</p>
      <label
        >新就诊时段<select v-model="slotId">
          <option value="">请选择</option>
          <option
            v-for="s in slots"
            :key="s.id"
            :value="String(s.id)"
            :disabled="s.reserved >= s.capacity || new Date(s.startAt) <= new Date()"
          >
            {{ timeLabel(s.startAt) }}—{{ timeLabel(s.endAt) }} · 剩余 {{ s.capacity - s.reserved }}
          </option>
        </select></label
      ><label class="consent"
        ><input
          v-model="consent"
          type="checkbox"
        />我已了解新医生和就诊时间，同意为原就诊人改约。</label
      >
      <p v-if="error" class="alert error">{{ error }}</p>
      <div class="modal-actions">
        <button :disabled="busy" @click="selected = null">返回</button
        ><button class="primary" :disabled="busy || !consent || !slotId" @click="accept">
          确认改约
        </button>
      </div>
    </section>
  </div>
</template>
<style scoped>
.change-panel {
  padding: 24px;
}
.change-record {
  border-top: 1px solid #e2e8f0;
  padding: 18px 0;
}
.change-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.change-actions select {
  max-width: 100%;
  flex: 1;
  min-width: 0;
}
.change-panel label {
  display: grid;
  gap: 8px;
  margin: 16px 0;
}
.consent {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 20px 0;
}
.consent input {
  width: auto;
}
.change-record p {
  overflow-wrap: anywhere;
}
</style>
