# 本次验证记录

验证日期：2026-11-11。本机 Windows、Java 21.0.8、Node 24.19.0、PostgreSQL 17.6。数据库测试使用独立测试实例，不访问原有数据库。

| 验证 | 结果 |
|---|---|
| `mvn -B verify spotless:check` | 19 项测试全部通过（8项数据库/接口集成、11项导诊客户端/业务/限流测试），打包和Java格式检查通过 |
| 12 个并发请求争抢容量为1的号源 | 仅1个成功，数据库占用为1 |
| 预约幂等、重复取消、事务回滚 | 通过 |
| 租户/患者隔离、CSRF、角色越权 | 通过 |
| 排班冲突、非法状态迁移 | 通过 |
| `npm test` | 2 项通过 |
| `npm run build` | TypeScript 检查与 Vite 生产构建通过 |
| `python scripts/smoke.py` | PostgreSQL + Redis 下真实 HTTP 流程通过，测试预约最终取消并释放 |
| `npm run test:e2e` | 5项通过；导诊模型响应使用测试桩，不产生外部调用 |
| Headless Chromium 浏览器联调 | 登录、预约、取消、管理端排班弹窗通过；无 pageerror |
| 390px 移动端检查 | 无横向溢出；已保存页面截图 |
| `npm audit --omit=dev --audit-level=high --registry=https://registry.npmjs.org` | 生产依赖0个已知漏洞；不代表所有依赖或后端安全审计 |
| Windows 本机启动 / 停止脚本 | 已实机运行 |

截图位于 `screenshots/`：新版蓝白登录、紧凑排班列表、患者移动端、管理员发布排班弹窗、导诊桌面和移动端。截图中的数据全部为虚构。

本机 Redis 为已有 Windows 3.2.100 二进制，仅用于本次兼容性联调；Compose 指定 Redis 7.4。未在本机安装或修改系统服务。

**未验证**：Docker 容器实际构建与启动、GitHub Actions 远程运行、真实 DeepSeek 模型调用及临床效果、真实医院接口、支付/短信、生产规模负载与高可用。当前12并发测试验证正确性，不是医院级吞吐性能报告。
