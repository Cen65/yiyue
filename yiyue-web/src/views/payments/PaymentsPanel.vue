<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { paymentsApi, type Payment, type Reconciliation } from '../../apis/payments'
const props = defineProps<{ admin: boolean }>()
const orders = ref<Payment[]>([]),
  rows = ref<Reconciliation[]>([]),
  busy = ref(false),
  error = ref('')
const labels: Record<string, string> = {
  PENDING: '待支付',
  PAID: '已支付',
  CLOSED: '已关闭',
  REFUND_PENDING: '退款处理中',
  REFUNDED: '已退款',
}
async function load() {
  if (props.admin) rows.value = await paymentsApi.reconcile()
  else orders.value = await paymentsApi.list()
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
async function pay(o: Payment) {
  if (!window.confirm(`确认模拟支付 ¥${(o.amountCents / 100).toFixed(2)}？仅演示，不会真实扣款。`))
    return
  await run(async () => {
    await paymentsApi.pay(o.id)
    await load()
  })
}
async function cancel(o: Payment) {
  if (!window.confirm('确认关闭订单并释放保留号源？')) return
  await run(async () => {
    await paymentsApi.cancel(o.id)
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
  <section class="panel payments-panel">
    <div class="section-heading">
      <h3>{{ admin ? '模拟渠道对账' : '我的支付订单' }}</h3>
      <button :disabled="busy" @click="run(load)">刷新</button>
    </div>
    <p class="alert">
      模拟支付演示，不产生真实扣款或退款。待支付最多保留15分钟，逾期释放；迟到支付自动进入模拟退款。
    </p>
    <p v-if="error" class="alert error" role="alert">{{ error }}</p>
    <template v-if="admin"
      ><p class="muted">最近200单与模拟渠道流水逐单比对；不代表真实商户账单核验。</p>
      <article v-for="r in rows" :key="r.id" class="payment-row">
        <b>订单 #{{ r.id }} · ¥{{ (r.amountCents / 100).toFixed(2) }} · {{ labels[r.status] }}</b>
        <p :class="{ danger: !r.matched }">
          {{ r.matched ? '账实一致' : '发现差异，请核对订单与渠道流水' }}
        </p>
      </article>
      <p v-if="!rows.length">暂无对账记录。</p></template
    ><template v-else
      ><article v-for="o in orders" :key="o.id" class="payment-row">
        <b>订单 #{{ o.id }} · {{ labels[o.status] }} · ¥{{ (o.amountCents / 100).toFixed(2) }}</b>
        <p>{{ o.doctorName }} · {{ o.campusName }} · {{ o.patientName }}</p>
        <p>{{ o.startAt.replace('T', ' ') }}—{{ o.endAt.slice(11, 16) }}（北京时间）</p>
        <p v-if="o.status === 'PENDING'">
          支付截止：{{ o.expiresAt.replace('T', ' ') }}（北京时间）
        </p>
        <p v-if="o.appointmentId">预约 #{{ o.appointmentId }}，可在我的预约查看或取消。</p>
        <div v-if="o.status === 'PENDING'" class="payment-actions">
          <button class="primary" :disabled="busy" @click="pay(o)">模拟支付</button
          ><button :disabled="busy" @click="cancel(o)">关闭订单</button>
        </div>
      </article>
      <p v-if="!orders.length">暂无支付订单，免费排班无需支付。</p></template
    >
  </section>
</template>
<style scoped>
.payments-panel {
  padding: 24px;
}
.payment-row {
  padding: 18px 0;
  border-top: 1px solid #e2e8f0;
  overflow-wrap: anywhere;
}
.payment-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
