<script setup lang="ts">
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
import { ref } from 'vue'
import type { Schedule } from '../../types'
import ResourcesPanel from '../resources/ResourcesPanel.vue'
import PaymentsPanel from '../payments/PaymentsPanel.vue'
import WaitlistPanel from '../waitlist/WaitlistPanel.vue'
import { waitlistApi } from '../../apis/waitlist'
import ChangesPanel from '../changes/ChangesPanel.vue'
import PlatformPanel from '../platform/PlatformPanel.vue'
import OperationsPanel from '../operations/OperationsPanel.vue'
import PatientProfiles from '../patients/PatientProfiles.vue'
import TriagePanel from '../triage/TriagePanel.vue'
import { useWorkspace } from '../../hooks/useWorkspace'
import { dateLabel, timeLabel, remaining, statusLabels } from '../../utils/format'
const {
  resourceOptions,
  selectedResources,
  mockPayments,
  user,
  booting,
  busy,
  error,
  notice,
  tab,
  page,
  total,
  login,
  catalog,
  schedules,
  slots,
  appointments,
  campus,
  department,
  day,
  selected,
  pendingSlot,
  showCreate,
  draft,
  admin,
  roleLabel,
  platform,
  staff,
  profiles,
  patientId,
  schedulePage,
  scheduleTotal,
  changeSchedulePage,
  pageTitle,
  dates,
  filtered,
  doctor,
  campusName,
  deptName,
  run,
  load,
  chooseDepartment,
  switchTab,
  showAppointments,
  changePage,
  signIn,
  signOut,
  selectSchedule,
  requestBooking,
  confirmBooking,
  action,
  createSchedule,
} = useWorkspace()
const changeSource = ref<Schedule | null>(null)
async function joinWaitlist(id: number) {
  await run(async () => {
    await waitlistApi.join(id, patientId.value ? Number(patientId.value) : undefined)
    selected.value = null
    switchTab('waitlist')
  })
}
function onScheduleChanged() {
  void run(load)
  switchTab('changes')
}
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
            >所属机构<input
              v-model="login.tenantCode"
              list="institution-codes"
              required
              placeholder="输入机构编码"
            /><datalist id="institution-codes">
              <option value="hospital">医约示范医院</option>
              <option value="clinic">青禾示范诊所</option>
              <option value="platform">平台管理</option>
            </datalist></label
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
        <button
          v-if="admin"
          :class="{ active: tab === 'resources' }"
          @click="switchTab('resources')"
        >
          <Hospital :size="19" />诊室与设备
        </button>
        <button
          v-if="!staff && !platform && mockPayments"
          :class="{ active: tab === 'payments' }"
          @click="switchTab('payments')"
        >
          <ClipboardList :size="19" />{{ admin ? '支付对账' : '支付订单' }}
        </button>
        <button
          v-if="!admin && !staff && !platform"
          :class="{ active: tab === 'waitlist' }"
          @click="switchTab('waitlist')"
        >
          <Clock3 :size="19" />候补与消息
        </button>
        <button
          v-if="!platform && !staff"
          :class="{ active: tab === 'changes' }"
          @click="switchTab('changes')"
        >
          <CalendarDays :size="19" />{{ admin ? '排班变更' : '变更通知' }}
        </button>
        <button
          v-if="!platform"
          :class="{ active: tab === 'booking' }"
          @click="switchTab('booking')"
        >
          <CalendarDays :size="19" />{{ admin ? '排班与号源' : '预约挂号'
          }}<ChevronRight :size="15" /></button
        ><button
          v-if="!platform"
          :class="{ active: tab === 'appointments' }"
          @click="showAppointments()"
        >
          <ClipboardList :size="19" />{{ admin || staff ? '预约管理' : '我的预约' }}
        </button>
        <button
          v-if="!admin && !staff && !platform"
          :class="{ active: tab === 'triage' }"
          @click="switchTab('triage')"
        >
          <HeartPulse :size="19" />智能导诊
        </button>
        <button
          v-if="admin"
          :class="{ active: tab === 'operations' }"
          @click="switchTab('operations')"
        >
          <Hospital :size="19" />基础运营
        </button>
        <button
          v-if="!admin && !staff && !platform"
          :class="{ active: tab === 'profiles' }"
          @click="switchTab('profiles')"
        >
          <ClipboardList :size="19" />就诊人管理
        </button>
        <button
          v-if="platform"
          :class="{ active: tab === 'platform' }"
          @click="switchTab('platform')"
        >
          <Hospital :size="19" />机构管理
        </button>
      </nav>
      <button class="profile" :disabled="busy" @click="signOut">
        <span class="avatar">{{ user.name.slice(0, 1) }}</span
        ><span
          ><b>{{ user.name }}</b
          ><small>{{ roleLabel }}</small></span
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
          <button v-if="admin && tab === 'booking'" class="primary" @click="showCreate = true">
            <Plus :size="17" />发布排班
          </button>
        </div>
        <p v-if="error" class="alert error" role="alert">{{ error }}</p>
        <p v-if="notice" class="alert success" role="status">{{ notice }}</p>
        <p v-if="!mockPayments && !platform" class="alert">
          当前环境仅开放免费预约，尚未接入收费渠道。
        </p>
        <PlatformPanel v-if="platform" /><OperationsPanel
          v-else-if="tab === 'operations'"
          @changed="run(load)"
        />
        <ChangesPanel
          v-else-if="tab === 'changes'"
          :admin="admin"
          :catalog="catalog"
          @changed="run(load)"
        />
        <WaitlistPanel v-else-if="tab === 'waitlist'" />
        <PaymentsPanel v-else-if="tab === 'payments'" :admin="admin" />
        <ResourcesPanel v-else-if="tab === 'resources'" :catalog="catalog" @changed="run(load)" />
        <PatientProfiles v-else-if="tab === 'profiles'" />
        <TriagePanel v-else-if="tab === 'triage'" @choose-department="chooseDepartment" />
        <template v-else-if="tab === 'booking'">
          <div class="summary-strip">
            <span
              >服务院区 <b>{{ catalog.campuses.length }}</b></span
            ><span
              >出诊医生 <b>{{ catalog.doctors.length }}</b></span
            ><span
              >近期排班 <b>{{ scheduleTotal }}</b></span
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
              出诊安排 <span>{{ scheduleTotal }}</span>
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
            <article v-for="s in filtered" :key="s.id" :data-schedule-id="s.id" class="doctor-card">
              <div class="doctor-info">
                <div class="doctor-heading">
                  <h3>{{ doctor(s.doctorId)?.name }}</h3>
                  <small>{{ doctor(s.doctorId)?.title }}</small
                  ><span class="department-name">{{
                    deptName(s.departmentId || doctor(s.doctorId)?.departmentId || 0)
                  }}</span>
                </div>
                <p class="specialty">擅长：{{ doctor(s.doctorId)?.specialty }}</p>
                <small>{{
                  (s.feeCents || 0) > 0
                    ? `模拟挂号费 ¥${((s.feeCents || 0) / 100).toFixed(2)}`
                    : '免费预约'
                }}</small>
              </div>
              <div class="schedule-details">
                <span><MapPin :size="14" />{{ campusName(s.campusId) }}</span
                ><span><CalendarDays :size="14" />{{ dateLabel(s.startAt) }}</span
                ><span
                  ><Clock3 :size="14" />{{ timeLabel(s.startAt) }} — {{ timeLabel(s.endAt) }}</span
                >
              </div>
              <div class="schedule-actions">
                <button v-if="admin" class="text-button danger" @click="changeSource = s">
                  排班变更</button
                ><button class="slot-button" :disabled="busy" @click="selectSchedule(s)">
                  {{ admin ? '查看号源' : '选择时段' }}<ChevronRight :size="15" />
                </button>
              </div>
            </article>
          </div>
          <div class="pagination">
            <span>共 {{ scheduleTotal }} 场 · 第 {{ schedulePage }} 页</span
            ><button :disabled="busy || schedulePage === 1" @click="changeSchedulePage(-1)">
              上一页</button
            ><button
              :disabled="busy || schedulePage * 20 >= scheduleTotal"
              @click="changeSchedulePage(1)"
            >
              下一页
            </button>
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
                {{ a.patientName || '就诊人' }} · {{ a.campusName }} · {{ dateLabel(a.startAt) }}
                {{ timeLabel(a.startAt) }}—{{ timeLabel(a.endAt)
                }}<template v-if="admin"> · 就诊人 #{{ a.userId }}</template>
              </p>
            </div>
            <span class="badge" :class="a.status.toLowerCase()">{{
              statusLabels[a.status] || a.status
            }}</span
            ><button
              v-if="!admin && !staff && a.status === 'BOOKED'"
              class="text-button danger"
              :disabled="busy"
              @click="action(a, 'cancel')"
            >
              取消预约</button
            ><button
              v-if="(admin || user.role === 'RECEPTION') && a.status === 'BOOKED'"
              class="text-button"
              :disabled="busy"
              @click="action(a, 'check-in')"
            >
              办理签到</button
            ><button
              v-if="(admin || user.role === 'DOCTOR') && a.status === 'CHECKED_IN'"
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
  <div v-if="changeSource" class="overlay">
    <section class="modal">
      <ChangesPanel
        :key="changeSource.id"
        :admin="true"
        :catalog="catalog"
        :source="changeSource"
        @close="changeSource = null"
        @changed="onScheduleChanged"
      />
    </section>
  </div>
  <div v-if="selected" class="overlay" @click.self="!busy && (selected = null)">
    <section class="modal">
      <button class="close" aria-label="关闭" :disabled="busy" @click="selected = null">
        <X />
      </button>
      <h2>{{ doctor(selected.doctorId)?.name }} · 选择时段</h2>
      <p class="muted">{{ campusName(selected.campusId) }} · {{ dateLabel(selected.startAt) }}</p>
      <p v-if="selectedResources.length" class="muted">
        出诊资源：{{ selectedResources.map((r) => r.name).join('、') }}
      </p>
      <div class="slot-grid">
        <button
          v-for="slot in slots"
          :key="slot.id"
          :disabled="
            admin ||
            staff ||
            ((selected.feeCents || 0) > 0 && !mockPayments) ||
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
      <template v-if="!admin && !staff && !(selected.feeCents || 0)"
        ><label
          >候补就诊人<select v-model="patientId">
            <option value="">本人（默认）</option>
            <option
              v-for="p in profiles.filter((p) => p.authorized)"
              :key="p.id"
              :value="String(p.id)"
            >
              {{ p.name }}
            </option>
          </select></label
        >
        <div class="slot-grid">
          <button
            v-for="s in slots.filter(
              (s) => remaining(s.capacity, s.reserved) === 0 && new Date(s.startAt) > new Date(),
            )"
            :key="s.id"
            :disabled="busy"
            @click="joinWaitlist(s.id)"
          >
            {{ timeLabel(s.startAt) }}—{{ timeLabel(s.endAt) }} 加入候补
          </button>
        </div></template
      >
      <p v-if="admin" class="muted">管理端仅查看号源；请使用患者账号预约。</p>
      <p v-if="error" class="alert error">{{ error }}</p>
    </section>
  </div>
  <div v-if="pendingSlot" class="overlay confirm-layer">
    <section class="modal small">
      <h2>{{ (selected?.feeCents || 0) > 0 ? '创建模拟支付订单' : '确认预约' }}</h2>
      <p v-if="(selected?.feeCents || 0) > 0">
        模拟挂号费 ¥{{
          ((selected?.feeCents || 0) / 100).toFixed(2)
        }}，仅演示，不会真实扣款；订单最多占号15分钟。
      </p>
      <p>{{ doctor(selected?.doctorId || 0)?.name }} · {{ dateLabel(pendingSlot.startAt) }}</p>
      <p>{{ timeLabel(pendingSlot.startAt) }} — {{ timeLabel(pendingSlot.endAt) }}</p>
      <label
        >预约就诊人<select v-model="patientId">
          <option value="">本人（默认）</option>
          <option
            v-for="p in profiles.filter((p) => p.authorized)"
            :key="p.id"
            :value="String(p.id)"
          >
            {{ p.name }} · {{ p.relationship === 'SELF' ? '本人' : '已授权就诊人' }}
          </option>
        </select></label
      >
      <p class="muted">仅可为本人或已授权管理的就诊人预约。</p>
      <p v-if="error" class="alert error">{{ error }}</p>
      <div class="modal-actions">
        <button :disabled="busy" @click="pendingSlot = null">返回</button
        ><button class="primary" :disabled="busy" @click="confirmBooking">
          <Check :size="17" />{{
            busy ? '正在提交…' : (selected?.feeCents || 0) > 0 ? '创建支付订单' : '确认预约'
          }}
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
        <label
          >联合占用资源（可多选）<select v-model="draft.resourceIds" multiple>
            <option
              v-for="r in resourceOptions.filter(
                (r) => r.enabled && String(r.campusId) === String(draft.campusId),
              )"
              :key="r.id"
              :value="r.id"
            >
              {{ r.kind === 'ROOM' ? '诊室' : '设备' }} · {{ r.name }}
            </option>
          </select></label
        >
        <p class="muted">资源按整场排班独占，与医生排班同一事务提交；不选择则沿用仅医生排班。</p>
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
          ><label
            >出诊科室<select v-model="draft.departmentId">
              <option value="">医生默认科室</option>
              <option v-for="d in catalog.departments" :key="d.id" :value="String(d.id)">
                {{ d.name }}
              </option>
            </select></label
          ><label v-if="mockPayments"
            >模拟挂号费（分，0为免费）<input
              v-model.number="draft.feeCents"
              type="number"
              min="0"
              max="1000000"
              required /></label
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
