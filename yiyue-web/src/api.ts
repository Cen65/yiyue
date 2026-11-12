let csrf: { token: string; headerName: string } | undefined
export async function refreshCsrf() {
  // 从服务端取得当前会话的 CSRF 令牌；不在浏览器持久化认证信息。
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('无法连接医约服务，请检查后端和 Redis')
  csrf = await response.json()
}
export async function api<T = void>(url: string, method = 'GET', body?: unknown): Promise<T> {
  if (method !== 'GET' && !csrf) await refreshCsrf()
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (method !== 'GET' && csrf) headers[csrf.headerName] = csrf.token
  const response = await fetch(`/api${url}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(
      error.detail ||
        (response.status === 401
          ? '登录已失效，请重新登录'
          : response.status === 403
            ? '没有操作权限，或会话校验已过期，请刷新页面'
            : '请求失败，请稍后重试'),
    )
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
