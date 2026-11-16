import { api } from '../utils/request'
import type { TriageResult } from '../types'
export const triageApi = {
  status: () => api<{ enabled: boolean; provider: string }>('/patient/triage/status'),
  recommend: (input: { symptoms: string; consent: boolean }) =>
    api<TriageResult>('/patient/triage', 'POST', input),
}
