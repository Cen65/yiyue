<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { platformApi, type Tenant } from '../../apis/platform'
const rows = ref<Tenant[]>([]),
  error = ref(''),
  notice = ref(''),
  busy = ref(false),
  editing = ref(false),
  id = ref<number | null>(null)
const draft = reactive({
  code: '',
  name: '',
  enabled: true,
  adminUsername: 'admin',
  adminPassword: '',
})
async function load() {
  rows.value = await platformApi.list()
}
async function run(task: () => Promise<void>) {
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
function edit(row?: Tenant) {
  id.value = row?.id ?? null
  Object.assign(draft, {
    code: row?.code ?? '',
    name: row?.name ?? '',
    enabled: row?.enabled ?? true,
    adminUsername: 'admin',
    adminPassword: '',
  })
  editing.value = true
}
async function save() {
  await run(async () => {
    if (id.value === null) await platformApi.create(draft)
    else await platformApi.update(id.value, draft)
    draft.adminPassword = ''
    editing.value = false
    await load()
    notice.value = '机构信息已保存'
  })
}
onMounted(() => run(load))
</script>
<template>
  <section class="panel">
    <div class="panel-heading">
      <h2>机构开户与启停</h2>
      <button class="primary" :disabled="busy" @click="edit()">新建机构</button>
    </div>
    <div class="panel-body">
      <p class="muted">
        平台账号仅用于机构管理，不提供患者或医护业务权限。停用机构后，其账号的登录及已有会话均失效。
      </p>
      <p v-if="error" class="alert error" role="alert">{{ error }}</p>
      <p v-if="notice" class="alert success" role="status">{{ notice }}</p>
      <div class="resource-row" v-for="t in rows" :key="t.id">
        <div>
          <b>{{ t.name }}</b
          ><small>机构编码：{{ t.code }}</small>
        </div>
        <span>{{ t.enabled ? '启用' : '停用' }}</span
        ><button class="text-button" @click="edit(t)">编辑</button>
      </div>
      <form v-if="editing" class="resource-editor" @submit.prevent="save">
        <div class="form-grid">
          <label
            >机构编码<input
              v-model="draft.code"
              pattern="[a-z0-9_-]{3,40}"
              :disabled="id !== null"
              required /></label
          ><label>机构名称<input v-model="draft.name" maxlength="100" required /></label
          ><label v-if="id !== null"
            >机构状态<select v-model="draft.enabled">
              <option :value="true">启用</option>
              <option :value="false">停用</option>
            </select></label
          ><template v-else
            ><label
              >管理员账号<input
                v-model="draft.adminUsername"
                pattern="[A-Za-z0-9_-]{3,50}"
                required /></label
            ><label
              >管理员初始密码<input
                v-model="draft.adminPassword"
                type="password"
                minlength="12"
                maxlength="100"
                autocomplete="new-password"
                required /></label
          ></template>
        </div>
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="editing = false">取消</button
          ><button class="primary" :disabled="busy">保存</button>
        </div>
      </form>
    </div>
  </section>
</template>
