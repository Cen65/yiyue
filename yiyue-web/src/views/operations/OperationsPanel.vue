<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  operationsApi,
  type Overview,
  type Resource,
  type ResourceKind,
} from '../../apis/operations'
const emit = defineEmits<{ changed: [] }>()
const data = ref<Overview | null>(null),
  kind = ref<ResourceKind | 'staff'>('campuses'),
  busy = ref(false),
  error = ref(''),
  notice = ref(''),
  editing = ref(false),
  id = ref<number | null>(null)
const labels = {
  tenant: '机构信息',
  campuses: '院区管理',
  departments: '科室管理',
  doctors: '医生管理',
  staff: '医护账号',
}
const draft = reactive({
  name: '',
  address: '',
  title: '',
  specialty: '',
  enabled: true,
  departmentIds: [] as number[],
  campusIds: [] as number[],
  username: '',
  password: '',
  role: 'RECEPTION',
  doctorId: '',
})
const rows = computed<Resource[]>(() =>
  !data.value || kind.value === 'staff'
    ? []
    : kind.value === 'tenant'
      ? [data.value.tenant]
      : data.value[kind.value],
)
async function load() {
  data.value = await operationsApi.overview()
}
async function run(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await task()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busy.value = false
  }
}
function selectKind(value: ResourceKind | 'staff') {
  kind.value = value
  editing.value = false
  error.value = ''
  notice.value = ''
}
function cancelEdit() {
  editing.value = false
  draft.password = ''
}
function edit(row?: Resource) {
  id.value = row?.id ?? null
  Object.assign(draft, {
    name: row?.name ?? '',
    address: row?.address ?? '',
    title: row?.title ?? '',
    specialty: row?.specialty ?? '',
    enabled: row?.enabled ?? true,
    username: '',
    password: '',
    role: 'RECEPTION',
    doctorId: '',
  })
  draft.departmentIds = row
    ? data.value!.doctorDepartments.filter((l) => l.doctorId === row.id).map((l) => l.departmentId)
    : []
  draft.campusIds = row
    ? data.value!.doctorCampuses.filter((l) => l.doctorId === row.id).map((l) => l.campusId)
    : []
  editing.value = true
}
async function save() {
  await run(async () => {
    if (kind.value === 'staff') {
      await operationsApi.staff({
        ...draft,
        doctorId: draft.doctorId ? Number(draft.doctorId) : null,
      })
    } else {
      await operationsApi.save(kind.value, id.value, draft)
    }
    draft.password = ''
    editing.value = false
    await load()
    emit('changed')
    notice.value = '保存成功'
  })
}
onMounted(() => run(load))
</script>
<template>
  <section class="panel operations">
    <div class="operations-tabs">
      <button
        v-for="(label, key) in labels"
        :key="key"
        :class="{ active: kind === key }"
        @click="selectKind(key)"
      >
        {{ label }}
      </button>
    </div>
    <div class="panel-body">
      <p v-if="error" class="alert error" role="alert">{{ error }}</p>
      <p v-if="notice" class="alert success" role="status">{{ notice }}</p>
      <div class="section-heading">
        <h3>{{ labels[kind] }}</h3>
        <button v-if="kind !== 'tenant'" class="primary" :disabled="busy" @click="edit()">
          {{ kind === 'staff' ? '新增医护账号' : '新增' }}
        </button>
      </div>
      <p v-if="kind === 'tenant'" class="muted">
        仅维护当前机构名称；当前机构不可在此停用，机构开户与整体停用由平台运维处理。
      </p>
      <div v-if="kind === 'staff'" class="resource-table">
        <div v-for="a in data?.staff" :key="a.id" class="resource-row">
          <div>
            <b>{{ a.name }}</b
            ><small>{{ a.username }}</small>
          </div>
          <span>{{ a.role === 'DOCTOR' ? '医生' : '前台' }}</span>
        </div>
        <p v-if="!data?.staff.length" class="muted">
          暂无医护账号。医生账号仅能处理绑定医生的预约。
        </p>
      </div>
      <div v-else class="resource-table">
        <div v-for="row in rows" :key="row.id" class="resource-row">
          <div>
            <b>{{ row.name }}</b
            ><small>{{ row.address || row.title || '' }}</small>
          </div>
          <span :class="{ muted: !row.enabled }">{{ row.enabled ? '启用' : '停用' }}</span
          ><button class="text-button" :disabled="busy" @click="edit(row)">编辑</button>
        </div>
        <p v-if="!rows.length" class="muted">暂无数据，请新增。</p>
      </div>
      <form v-if="editing" class="resource-editor" @submit.prevent="save">
        <h3>{{ id ? '编辑' : '新增' }}{{ labels[kind] }}</h3>
        <div class="form-grid">
          <label>名称<input v-model="draft.name" maxlength="80" required /></label
          ><label v-if="kind !== 'tenant' && kind !== 'staff'"
            >状态<select v-model="draft.enabled">
              <option :value="true">启用</option>
              <option :value="false">停用</option>
            </select></label
          ><label v-if="kind === 'campuses'"
            >地址<input v-model="draft.address" maxlength="200" /></label
          ><template v-if="kind === 'doctors'"
            ><label>职称<input v-model="draft.title" maxlength="60" required /></label
            ><label>擅长<input v-model="draft.specialty" maxlength="200" /></label
            ><label
              >关联科室（可多选）<select v-model="draft.departmentIds" multiple required>
                <option
                  v-for="d in data?.departments.filter((d) => d.enabled)"
                  :key="d.id"
                  :value="d.id"
                >
                  {{ d.name }}
                </option>
              </select></label
            ><label
              >关联院区（可多选）<select v-model="draft.campusIds" multiple required>
                <option
                  v-for="c in data?.campuses.filter((c) => c.enabled)"
                  :key="c.id"
                  :value="c.id"
                >
                  {{ c.name }}
                </option>
              </select></label
            ></template
          ><template v-if="kind === 'staff'"
            ><label
              >账号<input
                v-model="draft.username"
                pattern="[A-Za-z0-9_-]{3,50}"
                autocomplete="off"
                required /></label
            ><label
              >初始密码<input
                v-model="draft.password"
                type="password"
                minlength="12"
                maxlength="100"
                autocomplete="new-password"
                required /></label
            ><label
              >岗位<select v-model="draft.role">
                <option value="RECEPTION">前台</option>
                <option value="DOCTOR">医生</option>
              </select></label
            ><label v-if="draft.role === 'DOCTOR'"
              >绑定医生<select v-model="draft.doctorId" required>
                <option value="" disabled>请选择</option>
                <option
                  v-for="d in data?.doctors.filter((d) => d.enabled && !d.accountId)"
                  :key="d.id"
                  :value="String(d.id)"
                >
                  {{ d.name }}
                </option>
              </select></label
            ></template
          >
        </div>
        <p v-if="kind === 'doctors'" class="muted">
          存在未结束排班时，不能停用医生或移除相关院区、科室。
        </p>
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="cancelEdit">取消</button
          ><button class="primary" :disabled="busy">保存</button>
        </div>
      </form>
    </div>
  </section>
</template>
