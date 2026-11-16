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

## v0.1.1 结构调整验收（2026-11-12）

- `mvn spotless:apply clean verify spotless:check` 通过，清除旧包字节码后19项测试通过；清理导入后再次运行19项测试与格式检查通过。
- `npm test` 2项通过，`npm run build` 的类型检查与生产构建通过；`npm run test:e2e` 5项通过。
- 新结构启动成功，真实 PostgreSQL/Redis HTTP 冒烟通过；脚本已修复跨天挑选已开始号源的问题。
- Controller 不直接引用 Mapper；实体保留原表名，JSON 响应字段、接口路径、事务与权限检查保持兼容。
- 参考项目只读；没有复制其业务、认证框架或生产配置，没有新增生产依赖或数据库迁移。
- 此版本尚未提交、推送或打标签。Docker实机、真实模型和医院规模性能仍不计入本次验证。

## v0.2.0 多模块与基础运营验收（2026-11-16）

- 父POM执行spotless:apply clean verify spotless:check通过，29项后端测试：AI模块11项、application模块18项。
- 新测试覆盖平台开户与停用会话、机构越权、重名、停用保护、医生多院区/科室关联、岗位操作范围、授权家属、同账号多就诊人防重、分页与多字节密码边界。
- 前端2项单元测试、类型检查及生产构建通过，9项Playwright回归通过；平台页面采用明确测试桩，平台真实API由数据库/MockMvc验证。
- PostgreSQL/Redis下HTTP冒烟通过；虚构演示机构已有排班过期时补充了未来三天排班，没有修改原有业务记录。
- Docker/CI配置已适配多模块，Docker实际运行、远程CI、新版本发布、真实模型与医学评估均未计入完成项。
