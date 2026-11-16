# HTTP API v0.1

基础路径 `/api`，JSON；时间 `yyyy-MM-dd'T'HH:mm:ss`（北京时间）；ID 为数字。会话身份使用 HttpOnly Cookie，前端不传 tenantId 或 userId。失败通常返回 ProblemDetail，安全过滤器错误可能返回 HTTP 标准错误对象。

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | /auth/csrf | 公开 | 返回 token、headerName；后续写请求附带该头并携带 Cookie |
| POST | /auth/login | 公开 + CSRF | tenantCode、username、password |
| GET | /auth/me | 已登录 | id、tenantId、name、role |
| POST | /auth/logout | 已登录 + CSRF | 清理会话，返回204；再次请求 csrf 获取新 token |
| GET | /catalog | 已登录 | campuses、departments、doctors |
| GET | /schedules | 已登录 | 当前到未来30天的排班 |
| GET | /slots?scheduleId=1 | 已登录 | 对应机构排班时段；其他机构返回空数组 |
| POST | /admin/schedules | ADMIN | campusId、doctorId、startAt、endAt、slotMinutes、capacity |
| POST | /patient/appointments | PATIENT | slotId、requestKey（1–64位字母数字下划线横线，推荐 UUID） |
| GET | /patient/appointments?page=1 | PATIENT | records、total、current、size；仅本人 |
| POST | /patient/appointments/{id}/cancel | PATIENT | 开始前取消本人 BOOKED 预约；已取消重试成功 |
| GET | /admin/appointments?page=1 | ADMIN | 本机构预约，固定每页20条 |
| POST | /admin/appointments/{id}/check-in | ADMIN | BOOKED → CHECKED_IN |
| POST | /admin/appointments/{id}/complete | ADMIN | CHECKED_IN → COMPLETED |

创建排班最多12小时，必须未来同一天、时段长度5–120分钟、容量1–100，区间秒数可被时段长度整除。同医生时间重叠返回409。未登录401，权限或CSRF错误403，不存在/无权访问404，冲突409，参数错误400。

号源 reserve 和预约写入为同一事务。网络超时后应复用原 requestKey 重试，不要每次生成新 key。列表返回已关联的 startAt、endAt、doctorName、campusName，避免客户端逐条请求。

## 智能导诊

`GET /patient/triage/status` 返回 `enabled` 和 `provider`。`POST /patient/triage` 接收 `symptoms`、`consent`，仅 PATIENT 角色可调用且必须带 CSRF token。请求长度5–1500字符，明确同意后才允许发送。结果字段 `urgency`、`departments`、`questions`、`message`，细节见 `ai-triage.md`。未配置503，格式/模型异常502，网络超时504，每账号每自然分钟超过8次返回429。

## v0.2.0接口

新增运营、平台、医护、档案和分页接口，详见v0.2.0.md；原有URL仍兼容。
