# 医约项目开发与维护约定

## 每次开发入口

1. 先检查 Git 状态，再阅读 docs/roadmap.md、docs/project-structure.md、docs/architecture.md 和 CHANGELOG.md。
2. 明确本次版本与任务编号，只实施已授权范围；新需求先登记，再实现。
3. 阅读相关实现、类型、调用方和测试，优先复用现有组件与接口。
4. 完成后更新任务状态、验收证据、变更记录和未验证项；未来规划不能标为完成。

## 工程结构

- 前端 yiyue-web、后端 yiyue-server 独立构建，通过 HTTP API 通信。后端父POM管理common/ai/application三模块，数据库与启动类位于application。AI仅通过科室端口访问业务，禁止反向依赖application或Mapper。
- 后端 controller 负责 HTTP 参数校验和响应；service 定义业务接口；service/impl 负责业务、事务与组装；mapper 负责持久化。
- 实体放 model/entity 并使用 DO 后缀；请求放 model/req，响应放 model/resp。多条件查询实际出现时再放 model/query，不创建无用途空类。
- 前端 views 放页面、apis 放业务接口、hooks 放页面状态、types 放契约、utils 放基础工具、styles 放样式、components 放真正复用的组件。
- 修改公共接口先检查所有调用方。保持现有 JSON 字段、URL、租户隔离和事务语义；破坏性变更需明确授权。
- 不复制人事法务参考项目的业务代码、凭证、配置与依赖体系，只参考目录和工程约定。

## 数据与安全

- 单表查询和更新优先 MyBatis-Plus Lambda；禁止注解 SQL；原子容量更新、行锁等确需 SQL 的内容集中 XML，禁止拼接外部输入。
- 禁止新增数据库 CHECK 约束。状态、范围、业务关系在应用层验证；保留主键、唯一索引和外键。
- 真实凭证、患者信息、咨询正文不得进入源码、测试、日志和提交。DeepSeek Key 仅在后端环境配置，模型响应是外部不可信数据。
- 不移除或减弱租户过滤、数据权限、CSRF、幂等、防超约、行锁、事务与限流。

## 验证与交付

- 后端 mvn verify spotless:check 使用独立 PostgreSQL 测试库；移动/删除 Java 类后执行 clean，避免残留字节码掩盖问题。
- 前端运行 npm test、npm run build；涉及页面或交互时运行 npm run test:e2e，并检查桌面与移动端。
- 浏览器 AI 测试使用测试桩，不能算作 DeepSeek 实网或医学有效性验证；Docker 与远程 CI 需分别确认。
- Git 提交使用中文说明，例如 refactor: 调整医约工程分层。只有用户明确要求时才提交、推送、打标签或发布。
- 不删除或覆盖用户无关改动；不使用破坏性 Git 命令。最终说明变更、关键文件、验证结果与限制。
