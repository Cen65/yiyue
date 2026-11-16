<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { profilesApi, type PatientProfile } from '../../apis/profiles'
const rows = ref<PatientProfile[]>([]),
  busy = ref(false),
  error = ref(''),
  editing = ref(false),
  id = ref<number | null>(null)
const labels: Record<string, string> = {
  SELF: '本人',
  CHILD: '子女',
  PARENT: '父母',
  SPOUSE: '配偶',
  OTHER: '其他',
}
const draft = reactive({ name: '', relationship: 'OTHER', consent: false })
async function run(task: () => Promise<void>) {
  busy.value = true
  error.value = ''
  try {
    await task()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busy.value = false
  }
}
async function load() {
  rows.value = await profilesApi.list()
}
function edit(row?: PatientProfile) {
  id.value = row?.id ?? null
  draft.name = row?.name ?? ''
  draft.relationship = row?.relationship ?? 'OTHER'
  draft.consent = false
  editing.value = true
}
async function save() {
  await run(async () => {
    await profilesApi.save(id.value, draft)
    editing.value = false
    await load()
  })
}
async function revoke(row: PatientProfile) {
  if (!window.confirm('撤销授权后不能再为此就诊人预约，确认继续？')) return
  await run(async () => {
    await profilesApi.revoke(row.id)
    await load()
  })
}
onMounted(() => run(load))
</script>
<template>
  <section class="panel">
    <div class="panel-heading">
      <h2>就诊人档案</h2>
      <button class="primary" :disabled="busy" @click="edit()">新增就诊人</button>
    </div>
    <div class="panel-body">
      <p class="muted">仅管理本人或已获得授权的就诊人。请勿将真实身份资料填写到公开演示环境。</p>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p>
      <div class="resource-row" v-for="p in rows" :key="p.id">
        <div>
          <b>{{ p.name }}</b
          ><small>{{ labels[p.relationship] }} · {{ p.authorized ? '已授权' : '已撤销' }}</small>
        </div>
        <button v-if="p.authorized" class="text-button" :disabled="busy" @click="edit(p)">
          编辑</button
        ><button
          v-if="!p.selfProfile && p.authorized"
          class="text-button danger"
          :disabled="busy"
          @click="revoke(p)"
        >
          撤销授权
        </button>
      </div>
      <p v-if="!rows.length" class="muted">暂无就诊人档案。未选择时，预约会自动使用本人档案。</p>
      <form v-if="editing" class="resource-editor" @submit.prevent="save">
        <div class="form-grid">
          <label>就诊人姓名<input v-model="draft.name" maxlength="80" required /></label
          ><label
            >关系<select v-model="draft.relationship" :disabled="id !== null" required>
              <option v-for="(label, value) in labels" :key="value" :value="value">
                {{ label }}
              </option>
            </select></label
          >
        </div>
        <label class="consent-label"
          ><input
            v-model="draft.consent"
            type="checkbox"
            required
          />我确认本人填写，或已取得该就诊人的档案管理与代预约授权。</label
        >
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="editing = false">取消</button
          ><button class="primary" :disabled="busy || !draft.consent">保存档案</button>
        </div>
      </form>
    </div>
  </section>
</template>
