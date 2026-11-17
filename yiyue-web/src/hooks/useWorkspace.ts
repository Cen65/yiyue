import { computed, onMounted, reactive, ref, watch } from 'vue'

import { profilesApi, type PatientProfile } from '../apis/profiles'
import { authApi, refreshCsrf } from '../apis/auth'
import { catalogApi } from '../apis/catalog'
import { appointmentApi } from '../apis/appointment'

import type { Appointment, Catalog, Schedule, Slot, User } from '../types'

/** 管理当前工作区会话、筛选与操作状态，不持久化患者咨询内容。 */
export function useWorkspace() {
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
    departmentId: '',
    startAt: '',
    endAt: '',
    slotMinutes: 30,
    capacity: 5,
  })
  const staff = computed(() => user.value?.role === 'RECEPTION' || user.value?.role === 'DOCTOR')
  const profiles = ref<PatientProfile[]>([]),
    patientId = ref(''),
    schedulePage = ref(1),
    scheduleTotal = ref(0)
  let scheduleRequest = 0
  const platform = computed(() => user.value?.role === 'PLATFORM_ADMIN')
  const roleLabel = computed(
    () =>
      ({
        ADMIN: '机构管理员',
        PATIENT: '就诊人',
        RECEPTION: '前台',
        DOCTOR: '医生',
        PLATFORM_ADMIN: '平台管理员',
      })[user.value?.role || 'PATIENT'],
  )
  const admin = computed(() => user.value?.role === 'ADMIN')
  const pageTitle = computed(() =>
    tab.value === 'waitlist'
      ? '候补与消息'
      : tab.value === 'changes'
        ? admin.value
          ? '排班变更'
          : '变更通知'
        : tab.value === 'platform'
          ? '机构管理'
          : tab.value === 'operations'
            ? '基础运营'
            : tab.value === 'profiles'
              ? '就诊人管理'
              : tab.value === 'triage'
                ? '智能导诊'
                : tab.value === 'booking'
                  ? admin.value
                    ? '排班与号源'
                    : '预约挂号'
                  : admin.value || staff.value
                    ? '预约管理'
                    : '我的预约',
  )
  const dates = computed(() =>
    Array.from({ length: 7 }, (_, i) => {
      const d = new Date()
      d.setDate(d.getDate() + i)
      return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    }),
  )
  const filtered = computed(() =>
    schedules.value.filter(
      (s) =>
        (!campus.value || s.campusId === Number(campus.value)) &&
        (!day.value || s.startAt.startsWith(day.value)) &&
        (!department.value || s.departmentId === Number(department.value)),
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
  async function loadSchedules() {
    const request = ++scheduleRequest
    const query: Record<string, string> = { page: String(schedulePage.value), size: '20' }
    if (campus.value) query.campusId = campus.value
    if (department.value) query.departmentId = department.value
    if (day.value) query.date = day.value
    try {
      const result = await catalogApi.page(query)
      if (request !== scheduleRequest) return
      schedules.value = result.records
      scheduleTotal.value = result.total
    } catch (e) {
      if (request === scheduleRequest) error.value = e instanceof Error ? e.message : '排班加载失败'
    }
  }
  watch([campus, department, day], () => {
    schedulePage.value = 1
    if (user.value) void loadSchedules()
  })
  function changeSchedulePage(delta: number) {
    schedulePage.value += delta
    void loadSchedules()
  }
  async function load() {
    if (platform.value) return
    const [c, a] = await Promise.all([
      catalogApi.catalog(),
      appointmentApi.list(user.value!.role, page.value),
    ])
    catalog.value = c
    await loadSchedules()
    if (user.value?.role === 'PATIENT') profiles.value = await profilesApi.list()
    appointments.value = a.records
    total.value = a.total
  }
  async function signIn() {
    await run(async () => {
      user.value = await authApi.login(login)
      login.password = ''
      await refreshCsrf()
      tab.value = platform.value ? 'platform' : 'booking'
      page.value = 1
      await load()
    })
  }
  async function signOut() {
    await run(async () => {
      await authApi.logout()
      user.value = null
      selected.value = null
      pendingSlot.value = null
      tab.value = 'booking'
      await refreshCsrf()
    })
  }
  async function selectSchedule(schedule: Schedule) {
    await run(async () => {
      slots.value = await catalogApi.slots(schedule.id)
      selected.value = schedule
    })
  }
  function requestBooking(slot: Slot) {
    pendingSlot.value = slot
    bookingKey = crypto.randomUUID()
    if (user.value?.role === 'PATIENT')
      void profilesApi
        .list()
        .then((values) => {
          profiles.value = values
        })
        .catch(() => {
          error.value = '无法加载就诊人，请稍后重试'
        })
  }
  async function confirmBooking() {
    if (!pendingSlot.value) return
    await run(async () => {
      // 网络失败时保留同一个幂等键，重试不会产生第二张预约单。
      await appointmentApi.book({
        slotId: pendingSlot.value!.id,
        requestKey: bookingKey,
        patientId: patientId.value ? Number(patientId.value) : undefined,
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
    if (!window.confirm(name === 'cancel' ? '确认取消该预约并释放号源？' : '确认执行此操作？'))
      return
    await run(async () => {
      await appointmentApi.transition(user.value!.role, appointment.id, name)
      await load()
      notice.value = '操作已完成'
    })
  }
  async function createSchedule() {
    await run(async () => {
      await catalogApi.create({
        ...draft,
        campusId: Number(draft.campusId),
        doctorId: Number(draft.doctorId),
        departmentId: draft.departmentId ? Number(draft.departmentId) : undefined,
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
      user.value = await authApi.me()
      if (platform.value) tab.value = 'platform'
      if (user.value) await load()
    } catch (e) {
      if (e instanceof Error && !e.message.includes('登录已失效')) error.value = e.message
    } finally {
      booting.value = false
    }
  })

  return {
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
  }
}
