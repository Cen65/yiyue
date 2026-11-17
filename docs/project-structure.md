# 当前项目结构

更新日期2026-11-16，后端版本0.4.0。参考人事法务项目的分层与命名，未复制其业务或凭证。

```text
yiyue-server/                    Maven父工程
  yiyue-common/                  SessionUser、BusinessException
  yiyue-ai/                      导诊Controller/Service/Impl/模型、DeepSeek、限流
  yiyue-application/             启动类与医疗业务
    src/main/java/com/yiyue/
      controller/                HTTP边界
      service/impl/              业务接口与实现
      mapper/                    MyBatis-Plus
      model/entity/              DO实体
      model/req|resp|query/      契约与查询
      auth|config|common/        安全、配置、HTTP错误
    src/main/resources/db/migration/
yiyue-web/src/
  views/                         工作区、运营、平台、就诊人、导诊、排班变更
  apis/                          业务HTTP接口
  hooks/                         会话与交互状态
  types/                         TypeScript契约
  utils/                         request/CSRF、format
  styles/                        样式
```

调用方向Controller → Service → Impl → Mapper或外部端口。application依赖ai和common，ai只依赖common。AiDepartmentCatalog的数据库适配位于application，禁止AI反向引用医疗业务Mapper。

事务、机构行锁、医生行锁、档案授权锁、原子容量更新和CSRF保留。前端复用hook和fetch，不为拆目录新增Router、Pinia或Axios。角色导航与数据范围由前后端共同约束，后端是最终权限边界。

IDEA导入父POM，运行application启动类；构建与检查从父工程执行。移动Java包后clean。维护入口为根AGENTS、roadmap和CHANGELOG，v0.2.0的配置、迁移和边界见v0.2.0.md。

排班变更使用 ScheduleChangeController → ScheduleChangeService/Impl → Mapper；ChangeItemProcessor 单独控制逐单事务，ChangeTaskRunner 只负责有界轮询。前端 ChangesPanel 复用既有 catalogApi、CSRF 请求和页面样式，管理员与患者共用变更条目契约。

v0.4.0新增WaitlistController → WaitlistService/Impl、候补/outbox/收件箱Mapper与DO、独立Req/Resp，WaitlistRunner轮询；前端waitlistApi及WaitlistPanel复用已有请求、角色导航与样式。浏览器排班夹具抽取scheduleFixture，供改约和候补共同使用。
