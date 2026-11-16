# DeepSeek 智能导诊

## 能力与边界

患者描述症状后，匹配**当前登录机构已有的科室**，展示就医紧急性提示与固定的补充问题，点击推荐科室后进入已有排班筛选。此功能不做疾病诊断、处方、用药、治疗或排除急症。管理端不提供患者咨询入口。

已完成适配与本地测试，**尚未提供真实 API Key，因此没有完成 DeepSeek 实网调用及临床有效性验证**。测试中模型返回是本地测试桩，不是实际医学判断。上线前需医护人员参与评估，包括儿童、老年人、否定症状、复合症状、紧急情况、非医疗输入与提示注入等场景。

## 配置

在根目录 `.env` 中加入或修改：

```dotenv
# 密钥仅填写在本地文件；不要提交 Git 或放入 VITE_ 变量。
AI_ENABLED=true
DEEPSEEK_API_KEY=
DEEPSEEK_MODEL=deepseek-flash
```

填写 `DEEPSEEK_API_KEY` 后，运行 `scripts/Stop-Local.ps1` 与 `scripts/Start-Local.ps1` 重启服务。`AI_ENABLED=false` 或缺少密钥时，页面显示尚未启用，仍可正常使用预约功能。修改环境变量无需重新编译；Java 代码变更后需要重新构建。

默认调用官方 `https://api.deepseek.com/chat/completions`，使用 JSON Output、非流式、关闭 thinking，最多800输出 token。模型名可配置；默认名参考接入时的官方文档。服务地址固定且禁止重定向，防止把 Key 误发往其他主机。连接超时4秒、响应读取超时25秒，无自动重试以避免重复费用。部分请求可能已经计费但未收到结果，需由用户选择是否重试。

参考：[DeepSeek 接入文档](https://api-docs.deepseek.com/en/)、[JSON Output](https://api-docs.deepseek.com/guides/json_mode/)。使用 Spring 自带 RestClient，无新增生产依赖。

## 请求和结果处理

- GET `/api/patient/triage/status`：返回 `enabled`、`provider`，不暴露 Key、模型凭证或服务端配置。
- POST `/api/patient/triage`：`symptoms` 5–1500字符，`consent=true`；必须有患者会话与 CSRF token。
- 返回 `urgency`、`departments`（id/name）、`questions`、`message`。
- urgency 为 EMERGENCY / PROMPT / UNDETERMINED / ROUTINE，ROUTINE 不表示排除急症。
- EMERGENCY 不展示科室预约入口；UNDETERMINED 不展示科室推荐，建议补充信息或人工导诊。
- 当前机构没有合适科室时，返回空推荐，不能为了有结果而强选其他科室。
- 后端重新验证科室ID、数组长度和枚举；拒绝字符串ID、虚构科室和非法问题类型。
- 页面消息由后端固定文案生成，不展示模型自由文本、诊断或思考过程。补充问题只能来自经过定义的五种问题类型。
- 模型空白、截断、格式错误、网络失败时返回502/504，不伪造推荐；未配置503、参数错误400、无同意400、超过配额429。

## 数据与费用控制

仅在患者勾选同意并主动提交后发送症状文本与当前机构的科室ID/名称。不主动发送账号、租户ID、姓名、电话、证件或预约记录；提醒用户不要在文本中填写身份信息。本系统不将咨询正文、模型原始响应或 API Key 写入业务数据库、应用日志及浏览器持久化存储。第三方服务的数据处理政策需由使用机构独立确认。

Redis 按机构与账号原子计数，每自然分钟最多8次，键120秒过期，不使用症状文本作为键或缓存内容。Redis 故障时不绕过限流；不同患者与机构的额度相互独立。当前配额不能替代生产环境的机构总预算和网关限流。

## 验证

后端在 `triage` 测试目录验证 JSON请求、空白/截断、错误响应、超时、科室白名单、紧急/不确定状态、固定问题、服务关闭和限流。接口集成测试验证患者同意、CSRF及未启用状态。

前端安装 Playwright Chromium 后，先启动本地系统，再运行 `npm run test:e2e`。浏览器测试使用真实本地登录和科室接口，**仅将 AI 接口替换为明确的测试返回**，验证同意、未配置、推荐跳转、紧急结果、超时及移动布局。不调用外部 DeepSeek，不产生模型费用。

## 就医提示的参考来源

紧急就医提示参考 [NHS 呼吸困难说明](https://www.nhs.uk/symptoms/shortness-of-breath/)；页面不使用其英国专用急救号码，而提示联系所在地急救服务。该资料只支持一般提醒，不构成本系统的临床分诊规范。医疗 AI 的错误风险参考 [WHO 对健康领域大语言模型的提醒](https://www.who.int/news/item/16-05-2023-who-calls-for-safe-and-ethical-ai-for-health)。

## v0.2.0模块位置

导诊完整代码位于yiyue-server/yiyue-ai；数据库白名单适配位于application，过滤停用科室。AI不引入MyBatis或数据库。
