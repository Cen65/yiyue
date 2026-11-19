<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { resourcesApi, type Resource } from '../../apis/resources'
import type { Catalog } from '../../types'
const props = defineProps<{ catalog: Catalog }>()
const emit = defineEmits<{ changed: [] }>()
const rows = ref<Resource[]>([]),
  draft = ref<Omit<Resource, 'id'>>({ name: '', kind: 'ROOM', campusId: 0, enabled: true }),
  editing = ref<number | null>(null),
  show = ref(false),
  busy = ref(false),
  error = ref('')
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
async function load() {
  rows.value = await resourcesApi.list()
}
function edit(r?: Resource) {
  editing.value = r?.id || null
  draft.value = r
    ? { name: r.name, kind: r.kind, campusId: r.campusId, enabled: r.enabled }
    : { name: '', kind: 'ROOM', campusId: props.catalog.campuses[0]?.id || 0, enabled: true }
  show.value = true
}
async function save() {
  await run(async () => {
    await resourcesApi.save(editing.value, draft.value)
    show.value = false
    await load()
    emit('changed')
  })
}
onMounted(() => void run(load))
</script>
<template>
  <section class="panel resources-panel">
    <div class="section-heading">
      <h3>诊室与设备</h3>
      <button class="primary" :disabled="busy" @click="edit()">新增资源</button>
    </div>
    <p class="muted">资源按整场排班独占；有正在进行或未来占用时不能编辑停用。</p>
    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <article v-for="r in rows" :key="r.id" class="resource-item">
      <div>
        <b>{{ r.name }}</b>
        <p>
          {{ r.kind === 'ROOM' ? '诊室' : '设备' }} ·
          {{ catalog.campuses.find((c) => c.id === r.campusId)?.name }} ·
          {{ r.enabled ? '启用' : '停用' }}
        </p>
      </div>
      <button :disabled="busy" @click="edit(r)">编辑</button>
    </article>
    <p v-if="!rows.length">暂无资源。</p>
  </section>
  <div v-if="show" class="overlay">
    <section class="modal">
      <h2>{{ editing ? '编辑资源' : '新增资源' }}</h2>
      <form @submit.prevent="save">
        <label>资源名称<input v-model="draft.name" required maxlength="80" /></label
        ><label
          >资源类型<select v-model="draft.kind">
            <option value="ROOM">诊室</option>
            <option value="EQUIPMENT">设备</option>
          </select></label
        ><label
          >所属院区<select v-model.number="draft.campusId" required>
            <option v-for="c in catalog.campuses" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select></label
        ><label
          >启用状态<select v-model="draft.enabled">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select></label
        >
        <p v-if="error" class="alert error">{{ error }}</p>
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="show = false">返回</button
          ><button class="primary" :disabled="busy">保存资源</button>
        </div>
      </form>
    </section>
  </div>
</template>
<style scoped>
.resources-panel {
  padding: 24px;
}
.resource-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid #e2e8f0;
  padding: 18px 0;
  overflow-wrap: anywhere;
}
</style>
