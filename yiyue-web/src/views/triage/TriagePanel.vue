<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, CircleAlert, RefreshCw } from 'lucide-vue-next'
import { triageApi } from '../../apis/triage'
import type { TriageResult } from '../../types'

const emit = defineEmits<{ chooseDepartment: [id: number] }>()
const enabled = ref(false),
  checking = ref(true),
  busy = ref(false),
  consent = ref(false)
const symptoms = ref(''),
  error = ref(''),
  result = ref<TriageResult | null>(null)
const urgencyLabels = {
  EMERGENCY: '请立即寻求急救',
  PROMPT: '建议及时线下评估',
  UNDETERMINED: '需要补充信息',
  ROUTINE: '科室匹配参考',
}
async function checkStatus() {
  checking.value = true
  error.value = ''
  try {
    enabled.value = (await triageApi.status()).enabled
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法连接导诊服务'
  } finally {
    checking.value = false
  }
}
async function submit() {
  if (busy.value || !enabled.value || !consent.value) return
  busy.value = true
  error.value = ''
  result.value = null
  try {
    // 仅在用户主动提交并同意后发送，不在浏览器或服务端保存咨询记录。
    result.value = await triageApi.recommend({
      symptoms: symptoms.value.trim(),
      consent: consent.value,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '导诊失败，请咨询人工导诊'
  } finally {
    busy.value = false
  }
}
onMounted(checkStatus)
</script>

<template>
  <div class="triage-layout">
    <section class="panel triage-form">
      <div class="panel-heading">
        <h2>症状描述</h2>
        <span>DeepSeek 辅助导诊</span>
      </div>
      <div class="panel-body">
        <p class="triage-intro">
          不知道挂哪个科？请说明主要不适、出现时间和伴随症状。系统仅提供科室匹配参考，不做诊断、开药或排除急症。
        </p>
        <div v-if="checking" class="service-note" role="status">正在检查导诊服务…</div>
        <div v-else-if="!enabled" class="service-note">
          <b>智能导诊尚未启用</b>
          <p>暂时无法提供 AI 科室匹配。您仍可直接预约，或咨询机构人工导诊。</p>
          <button class="text-button" @click="checkStatus"><RefreshCw :size="14" />重新检查</button>
        </div>
        <form @submit.prevent="submit">
          <label class="symptom-label" for="symptoms">请描述您的不适</label>
          <textarea
            id="symptoms"
            v-model="symptoms"
            :disabled="busy"
            minlength="5"
            maxlength="1500"
            rows="7"
            required
            placeholder="例如：成年人，最近几天反复头痛，想咨询应先到哪个科室就诊。请不要填写姓名、证件号码、电话等身份信息。"
          ></textarea>
          <div class="textarea-meta">
            <span>补充年龄段、持续时间及不适部位，有助于科室匹配。</span
            ><span>{{ symptoms.length }}/1500</span>
          </div>
          <label class="consent-label"
            ><input v-model="consent" type="checkbox" :disabled="busy" required /><span
              >我同意将本次症状描述发送至 DeepSeek，用于生成导诊参考。请勿填写身份信息。</span
            ></label
          >
          <p v-if="error" class="alert error" role="alert">{{ error }}</p>
          <div class="triage-submit">
            <span>每分钟最多提交 8 次</span
            ><button
              class="primary"
              :disabled="checking || !enabled || busy || !consent || symptoms.trim().length < 5"
            >
              {{ busy ? '正在匹配科室…' : '获取导诊参考' }}<ArrowRight :size="16" />
            </button>
          </div>
        </form>
      </div>
    </section>
    <aside class="triage-help">
      <h3><CircleAlert :size="17" />就医提醒</h3>
      <p>如有严重呼吸困难、持续剧烈胸痛伴呼吸困难或意识异常，请立即寻求急救，不要等待在线回复。</p>
      <h3>如何描述更清楚</h3>
      <ol>
        <li>主要不适在哪里，持续多久。</li>
        <li>是否突然出现、是否加重。</li>
        <li>是否伴随其他症状。</li>
      </ol>
      <p class="triage-scope">
        导诊结果仅供参考，不能替代医护人员评估。模型可能判断错误；即使给出科室建议，也不能排除急症。
      </p>
    </aside>
  </div>
  <section
    v-if="result"
    class="panel triage-result"
    :class="result.urgency.toLowerCase()"
    aria-live="polite"
  >
    <div class="panel-heading">
      <h2>{{ urgencyLabels[result.urgency] }}</h2>
    </div>
    <div class="panel-body">
      <p>{{ result.message }}</p>
      <div v-if="result.departments.length" class="department-recommendations">
        <div v-for="(d, index) in result.departments" :key="d.id">
          <span class="recommendation-order">{{ index + 1 }}</span>
          <div>
            <b>{{ d.name }}</b
            ><small>当前机构科室</small>
          </div>
          <button class="text-button" @click="emit('chooseDepartment', d.id)">
            查看该科排班<ArrowRight :size="15" />
          </button>
        </div>
      </div>
      <div v-if="result.questions.length" class="followup">
        <h3>可继续补充</h3>
        <ul>
          <li v-for="question in result.questions" :key="question">{{ question }}</li>
        </ul>
        <p>在上方描述中补充信息后，可重新提交。</p>
      </div>
    </div>
  </section>
</template>
