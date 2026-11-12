<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ArrowRight,
  CalendarDays,
  Check,
  ChevronRight,
  ClipboardList,
  Clock3,
  HeartPulse,
  Hospital,
  LogOut,
  MapPin,
  Plus,
  X,
} from 'lucide-vue-next'
import TriagePanel from './components/TriagePanel.vue'
import { api, refreshCsrf } from './api'
import { dateLabel, timeLabel, remaining, statusLabels } from './format'
import type { Appointment, Catalog, Schedule, Slot, User } from './types'

const user = ref<User | null>(null)
const booting = ref(true),
  busy = ref(false),
  error = ref(''),
  notice = ref('')
const tab = ref('booking')
const page = ref(1),
  total = ref(0)
const login = reactive({ tenantCode: 'hospital', username: 'patient', password: '' })
const catalog = ref<Catalog>({ campuses: [], departments: [], doctors: [] })
const schedules = ref<Schedule[]>([]),
  slots = ref<Slot[]>([]),
  appointments = ref<Appointment[]>([])
const campus = ref(''),
  department = ref(''),
  day = ref(''),
  selected = ref<Schedule | null>(null)
const pendingSlot = ref<Slot | null>(null)
let bookingKey = ''
const showCreate = ref(false)
const draft = reactive({
  campusId: '',
  doctorId: '',
  startAt: '',
  endAt: '',
  slotMinutes: 30,
  capacity: 5,
})
const admin = computed(() => user.value?.role === 'ADMIN')
const pageTitle = computed(() =>
  tab.value === 'triage'
    ? '智能导诊'
    : tab.value === 'booking'
      ? admin.value
        ? '排班与号源'
        : '预约挂号'
      : admin.value
        ? '预约管理'
        : '我的预约',
)
const dates = computed(() => [...new Set(schedules.value.map((s) => s.startAt.slice(0, 10)))])
const filtered = computed(() =>
  schedules.value.filter(
    (s) =>
      (!campus.value || s.campusId === Number(campus.value)) &&
      (!day.value || s.startAt.startsWith(day.value)) &&
      (!department.value || doctor(s.doctorId)?.departmentId === Number(department.value)),
  ),
)
const doctor = (id: number) => catalog.value.doctors.find((d) => d.id === id)
const campusName = (id: number) => catalog.value.campuses.find((c) => c.id === id)?.name || '—'
const deptName = (id: number) => catalog.value.departments.find((d) => d.id === id)?.name || '—'

async function run(task: () => Promise<void>) {
  if (busy.value) return
  error.value = ''
  notice.value = ''
  busy.value = true
  try {
    await task()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busy.value = false
  }
}
function chooseDepartment(id: number) {
  department.value = String(id)
  campus.value = ''
  day.value = ''
  tab.value = 'booking'
  notice.value = '已按推荐科室筛选，请选择出诊时间。'
}
function switchTab(value: string) {
  tab.value = value
  // 页面反馈属于当前操作，切换功能后清理，避免导诊页出现预约成功提示。
  error.value = ''
  notice.value = ''
}
function showAppointments() {
  switchTab('appointments')
  void run(load)
}
function changePage(delta: number) {
  page.value += delta
  void run(load)
}
async function load() {
  const [c, s, a] = await Promise.all([
    api<Catalog>('/catalog'),
    api<Schedule[]>('/schedules'),
    api<{ records: Appointment[]; total: number }>(
      `${admin.value ? '/admin/appointments' : '/patient/appointments'}?page=${page.value}`,
    ),
  ])
  catalog.value = c
  schedules.value = s
  appointments.value = a.records
  total.value = a.total
}
async function signIn() {
  await run(async () => {
    user.value = await api<User>('/auth/login', 'POST', login)
    login.password = ''
    await refreshCsrf()
    tab.value = 'booking'
    page.value = 1
    await load()
  })
}
async function signOut() {
  await run(async () => {
    await api('/auth/logout', 'POST')
    user.value = null
    selected.value = null
    pendingSlot.value = null
    tab.value = 'booking'
    await refreshCsrf()
  })
}
async function selectSchedule(schedule: Schedule) {
  await run(async () => {
    slots.value = await api<Slot[]>(`/slots?scheduleId=${schedule.id}`)
    selected.value = schedule
  })
}
function requestBooking(slot: Slot) {
  pendingSlot.value = slot
  bookingKey = crypto.randomUUID()
}
async function confirmBooking() {
  if (!pendingSlot.value) return
  await run(async () => {
    // 网络失败时保留同一个幂等键，重试不会产生第二张预约单。
    await api('/patient/appointments', 'POST', {
      slotId: pendingSlot.value!.id,
      requestKey: bookingKey,
    })
    pendingSlot.value = null
    selected.value = null
    tab.value = 'appointments'
    page.value = 1
    notice.value = '预约成功'
    await load()
  })
}
async function action(appointment: Appointment, name: string) {
  if (!window.confirm(name === 'cancel' ? '确认取消该预约并释放号源？' : '确认执行此操作？')) return
  await run(async () => {
    await api(
      `/${admin.value ? 'admin' : 'patient'}/appointments/${appointment.id}/${name}`,
      'POST',
    )
    await load()
    notice.value = '操作已完成'
  })
}
async function createSchedule() {
  await run(async () => {
    await api('/admin/schedules', 'POST', {
      ...draft,
      campusId: Number(draft.campusId),
      doctorId: Number(draft.doctorId),
      startAt: `${draft.startAt}:00`,
      endAt: `${draft.endAt}:00`,
    })
    showCreate.value = false
    await load()
    notice.value = '排班已发布，号源已生成'
  })
}
onMounted(async () => {
  try {
    await refreshCsrf()
    user.value = await api<User>('/auth/me')
    if (user.value) await load()
  } catch (e) {
    if (e instanceof Error && !e.message.includes('登录已失效')) error.value = e.message
  } finally {
    booting.value = false
  }
})
</script>

<template>
  <div v-if="booting" class="boot">正在连接医约服务…</div>
  <div v-else-if="!user" class="login-layout">
    <section class="login-story">
      <a class="brand"
        ><span class="brand-icon"><HeartPulse :size="24" /></span
        ><b>医约 <small>YiYue</small></b></a
      >
      <div class="story-content">
        <span class="service-label">门诊预约服务</span>
        <h1>预约挂号<br />与门诊服务</h1>
        <p>查询医生排班，选择就诊时段，<br />在线管理您的预约记录。</p>
        <ol class="login-steps">
          <li>
            <span>01</span>
            <div>
              <b>选择就诊机构</b>
              <p>支持医院院区及社区诊所</p>
            </div>
          </li>
          <li>
            <span>02</span>
            <div>
              <b>查询门诊排班</b>
              <p>按科室、医生与日期选择</p>
            </div>
          </li>
          <li>
            <span>03</span>
            <div>
              <b>确认预约信息</b>
              <p>就诊前可在线查看或取消</p>
            </div>
          </li>
        </ol>
      </div>
      <footer>医约 · 门诊预约与资源调度系统</footer>
    </section>
    <section class="login-form">
      <div class="login-card">
        <div class="login-card-heading">账号登录</div>
        <h2>欢迎使用医约</h2>
        <p class="muted">请选择机构并使用账号登录。</p>
        <form @submit.prevent="signIn">
          <label
            >所属机构<select v-model="login.tenantCode">
              <option value="hospital">医约示范医院 · 多院区</option>
              <option value="clinic">青禾示范诊所 · 单门店</option>
            </select></label
          >
          <label
            >账号<input
              v-model="login.username"
              required
              autocomplete="username"
              placeholder="请输入账号" /></label
          ><label
            >密码<input
              v-model="login.password"
              type="password"
              required
              autocomplete="current-password"
              placeholder="请输入密码"
          /></label>
          <p v-if="error" class="alert error" role="alert">{{ error }}</p>
          <button class="primary full" :disabled="busy">
            {{ busy ? '正在登录…' : '登录' }}<ArrowRight :size="18" />
          </button>
        </form>
        <div class="login-hint">
          <span>演示账号：patient / admin<br />密码由本地环境配置；仅使用虚构演示数据。</span>
        </div>
      </div>
    </section>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a class="brand"
        ><span class="brand-icon"><HeartPulse :size="24" /></span
        ><b>医约 <small>YiYue</small></b></a
      >
      <div class="workspace">
        <Hospital :size="20" />
        <div>
          <b>{{ login.tenantCode === 'clinic' ? '青禾示范诊所' : '医疗服务平台' }}</b
          ><small>{{ admin ? '机构管理端' : '患者服务端' }}</small>
        </div>
      </div>
      <span class="nav-label">门诊服务</span>
      <nav>
        <button :class="{ active: tab === 'booking' }" @click="switchTab('booking')">
          <CalendarDays :size="19" />{{ admin ? '排班与号源' : '预约挂号'
          }}<ChevronRight :size="15" /></button
        ><button :class="{ active: tab === 'appointments' }" @click="showAppointments()">
          <ClipboardList :size="19" />{{ admin ? '预约管理' : '我的预约' }}
        </button>
        <button v-if="!admin" :class="{ active: tab === 'triage' }" @click="switchTab('triage')">
          <HeartPulse :size="19" />智能导诊
        </button>
      </nav>
      <button class="profile" :disabled="busy" @click="signOut">
        <span class="avatar">{{ user.name.slice(0, 1) }}</span
        ><span
          ><b>{{ user.name }}</b
          ><small>{{ admin ? '机构管理员' : '就诊人' }}</small></span
        ><LogOut :size="17" />
      </button>
    </aside>
    <main>
      <header class="topbar">
        <span>门诊服务 <ChevronRight :size="14" />{{ pageTitle }}</span
        ><span class="system-status">就诊时间均为北京时间</span>
      </header>
      <div class="content">
        <div class="page-title">
          <div>
            <h1>{{ pageTitle }}</h1>
            <p class="muted">
              {{
                tab === 'triage'
                  ? '描述不适，获取科室匹配参考。'
                  : tab === 'booking'
                    ? admin
                      ? '发布门诊排班，查看各时段号源。'
                      : '选择院区、科室和日期，查询出诊安排。'
                    : '查看预约状态与就诊安排。'
              }}
            </p>
          </div>
          <button v-if="admin" class="primary" @click="showCreate = true">
            <Plus :size="17" />发布排班
          </button>
        </div>
        <p v-if="error" class="alert error" role="alert">{{ error }}</p>
        <p v-if="notice" class="alert success" role="status">{{ notice }}</p>
        <TriagePanel v-if="tab === 'triage'" @choose-department="chooseDepartment" />
        <template v-else-if="tab === 'booking'">
          <div class="summary-strip">
            <span
              >服务院区 <b>{{ catalog.campuses.length }}</b></span
            ><span
              >出诊医生 <b>{{ catalog.doctors.length }}</b></span
            ><span
              >近期排班 <b>{{ schedules.length }}</b></span
            ><small>查询范围：未来 30 天</small>
          </div>
          <section class="panel filters">
            <div class="filter-row">
              <label
                >就诊院区<select v-model="campus">
                  <option value="">全部院区</option>
                  <option v-for="c in catalog.campuses" :key="c.id" :value="String(c.id)">
                    {{ c.name }}
                  </option>
                </select></label
              ><label
                >就诊科室<select v-model="department">
                  <option value="">全部科室</option>
                  <option v-for="d in catalog.departments" :key="d.id" :value="String(d.id)">
                    {{ d.name }}
                  </option>
                </select></label
              ><button class="text-button" :disabled="busy" @click="run(load)">刷新排班</button>
            </div>
            <div class="date-strip">
              <button :class="{ selected: !day }" @click="day = ''">全部日期</button
              ><button
                v-for="d in dates"
                :key="d"
                :class="{ selected: day === d }"
                @click="day = d"
              >
                {{ dateLabel(d + 'T00:00:00') }}
              </button>
            </div>
          </section>
          <div class="section-heading">
            <h3>
              出诊安排 <span>{{ filtered.length }}</span>
            </h3>
            <small class="muted">时间均为北京时间 · 提交时确认实时余量</small>
          </div>
          <div v-if="!filtered.length" class="empty panel">
            <CalendarDays :size="35" />
            <h3>暂无符合条件的排班</h3>
            <p>请调整筛选条件，或由管理员发布新的排班。</p>
          </div>
          <div v-if="filtered.length" class="schedule-list">
            <div class="schedule-list-head">
              <span>医生 / 科室</span><span>院区 / 出诊时间</span><span>操作</span>
            </div>
            <article v-for="s in filtered" :key="s.id" class="doctor-card">
              <div class="doctor-info">
                <div class="doctor-heading">
                  <h3>{{ doctor(s.doctorId)?.name }}</h3>
                  <small>{{ doctor(s.doctorId)?.title }}</small
                  ><span class="department-name">{{
                    deptName(doctor(s.doctorId)?.departmentId || 0)
                  }}</span>
                </div>
                <p class="specialty">擅长：{{ doctor(s.doctorId)?.specialty }}</p>
              </div>
              <div class="schedule-details">
                <span><MapPin :size="14" />{{ campusName(s.campusId) }}</span
                ><span><CalendarDays :size="14" />{{ dateLabel(s.startAt) }}</span
                ><span
                  ><Clock3 :size="14" />{{ timeLabel(s.startAt) }} — {{ timeLabel(s.endAt) }}</span
                >
              </div>
              <button class="slot-button" :disabled="busy" @click="selectSchedule(s)">
                {{ admin ? '查看号源' : '选择时段' }}<ChevronRight :size="15" />
              </button>
            </article>
          </div>
        </template>
        <section v-else class="panel appointments">
          <div class="section-heading">
            <h3>{{ admin ? '机构预约记录' : '预约记录' }}</h3>
            <button class="text-button" :disabled="busy" @click="run(load)">刷新记录</button>
          </div>
          <div v-if="!appointments.length" class="empty">
            <ClipboardList :size="36" />
            <h3>还没有预约记录</h3>
            <p>完成预约后，可在这里查看和管理。</p>
          </div>
          <div v-for="a in appointments" :key="a.id" class="appointment-row">
            <span class="appointment-icon"><ClipboardList :size="22" /></span>
            <div>
              <b>预约单 #{{ a.id }} · {{ a.doctorName }}</b>
              <p>
                {{ a.campusName }} · {{ dateLabel(a.startAt) }} {{ timeLabel(a.startAt) }}—{{
                  timeLabel(a.endAt)
                }}<template v-if="admin"> · 就诊人 #{{ a.userId }}</template>
              </p>
            </div>
            <span class="badge" :class="a.status.toLowerCase()">{{
              statusLabels[a.status] || a.status
            }}</span
            ><button
              v-if="!admin && a.status === 'BOOKED'"
              class="text-button danger"
              :disabled="busy"
              @click="action(a, 'cancel')"
            >
              取消预约</button
            ><button
              v-if="admin && a.status === 'BOOKED'"
              class="text-button"
              :disabled="busy"
              @click="action(a, 'check-in')"
            >
              办理签到</button
            ><button
              v-if="admin && a.status === 'CHECKED_IN'"
              class="text-button"
              :disabled="busy"
              @click="action(a, 'complete')"
            >
              完成接诊
            </button>
          </div>
          <div class="pagination">
            <span>共 {{ total }} 条 · 第 {{ page }} 页</span
            ><button :disabled="busy || page === 1" @click="changePage(-1)">上一页</button
            ><button :disabled="busy || page * 20 >= total" @click="changePage(1)">下一页</button>
          </div>
        </section>
        <footer class="page-footer">
          <span>医约 · 门诊预约系统</span><span>演示环境 · 所有人员与机构信息均为虚构</span>
        </footer>
      </div>
    </main>
  </div>
  <div v-if="selected" class="overlay" @click.self="!busy && (selected = null)">
    <section class="modal">
      <button class="close" aria-label="关闭" :disabled="busy" @click="selected = null">
        <X />
      </button>
      <h2>{{ doctor(selected.doctorId)?.name }} · 选择时段</h2>
      <p class="muted">{{ campusName(selected.campusId) }} · {{ dateLabel(selected.startAt) }}</p>
      <div class="slot-grid">
        <button
          v-for="slot in slots"
          :key="slot.id"
          :disabled="
            admin ||
            busy ||
            remaining(slot.capacity, slot.reserved) === 0 ||
            new Date(slot.startAt) <= new Date()
          "
          @click="requestBooking(slot)"
        >
          <b>{{ timeLabel(slot.startAt) }}–{{ timeLabel(slot.endAt) }}</b
          ><span>剩余 {{ remaining(slot.capacity, slot.reserved) }} / {{ slot.capacity }}</span>
        </button>
      </div>
      <p v-if="admin" class="muted">管理端仅查看号源；请使用患者账号预约。</p>
      <p v-if="error" class="alert error">{{ error }}</p>
    </section>
  </div>
  <div v-if="pendingSlot" class="overlay confirm-layer">
    <section class="modal small">
      <h2>确认预约</h2>
      <p>{{ doctor(selected?.doctorId || 0)?.name }} · {{ dateLabel(pendingSlot.startAt) }}</p>
      <p>{{ timeLabel(pendingSlot.startAt) }} — {{ timeLabel(pendingSlot.endAt) }}</p>
      <p class="muted">本次使用当前登录就诊人预约。预约成功后可在开始前取消。</p>
      <p v-if="error" class="alert error">{{ error }}</p>
      <div class="modal-actions">
        <button :disabled="busy" @click="pendingSlot = null">返回</button
        ><button class="primary" :disabled="busy" @click="confirmBooking">
          <Check :size="17" />{{ busy ? '正在提交…' : '确认预约' }}
        </button>
      </div>
    </section>
  </div>
  <div v-if="showCreate" class="overlay" @click.self="!busy && (showCreate = false)">
    <section class="modal">
      <button class="close" aria-label="关闭" :disabled="busy" @click="showCreate = false">
        <X />
      </button>
      <h2>发布排班</h2>
      <p class="muted">发布后自动按时段生成号源，医生跨院区排班不可重叠。</p>
      <form @submit.prevent="createSchedule">
        <div class="form-grid">
          <label
            >院区<select v-model="draft.campusId" required>
              <option disabled value="">选择院区</option>
              <option v-for="c in catalog.campuses" :key="c.id" :value="String(c.id)">
                {{ c.name }}
              </option>
            </select></label
          ><label
            >医生<select v-model="draft.doctorId" required>
              <option disabled value="">选择医生</option>
              <option v-for="d in catalog.doctors" :key="d.id" :value="String(d.id)">
                {{ d.name }}
              </option>
            </select></label
          ><label>开始时间<input v-model="draft.startAt" type="datetime-local" required /></label
          ><label>结束时间<input v-model="draft.endAt" type="datetime-local" required /></label
          ><label
            >每时段分钟数<input
              v-model.number="draft.slotMinutes"
              type="number"
              min="5"
              max="120"
              required /></label
          ><label
            >每时段容量<input
              v-model.number="draft.capacity"
              type="number"
              min="1"
              max="100"
              required
          /></label>
        </div>
        <p v-if="error" class="alert error">{{ error }}</p>
        <button class="primary full" :disabled="busy">
          {{ busy ? '正在发布…' : '发布排班与号源' }}
        </button>
      </form>
    </section>
  </div>
</template>
