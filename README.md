# MockStudio · 素材制作网站

用户可以浏览模板、上传图片并提交制作任务；任务在 Java 后台按 **素材检查 → 模拟生成 → 结果整理** 三步异步执行。处理期间用户可以继续使用网站，最后在历史记录里查看结果或失败原因。

制作过程不调用任何 AI 服务，由一个**可控制耗时、可指定阶段失败**的模拟生成器完成。它会把用户上传的图片真实拼合成一张结果图。

---

## 1. 技术版本

| 层 | 选型 | 版本 |
|---|---|---|
| 前端 | Next.js（App Router）· React · TypeScript | Next 15.5.28 · React 19.3.1 · TS 5.9.3（lockfile 锁定） |
| 前端状态 | TanStack Query | 5.105.0 |
| 前端测试 | Vitest · Testing Library · jsdom | Vitest 3.2.7 |
| 后端 | Java · Spring Boot · MyBatis | Java 21 · Spring Boot 3.3.5 · mybatis-spring-boot-starter 3.0.3 |
| 数据库 | MySQL（运行时）/ H2 MySQL 模式（仅测试） | MySQL 8.0 |
| 迁移 | Flyway（随 Spring Boot 3.3 管理） | 10.x |
| 运行环境 | Node.js 22 · Temurin JDK 21 · Docker Compose v2 | |

样式使用手写 CSS（`frontend/app/globals.css`），没有引入 UI 库。

## 2. 项目结构

```
.
├── backend/                         Spring Boot 服务
│   ├── src/main/java/com/mockstudio/
│   │   ├── auth/        注册/登录/会话、鉴权拦截器、演示账号
│   │   ├── template/    分类与模板查询
│   │   ├── asset/       素材上传（文件头校验）与读取
│   │   ├── task/        任务受理、查询、派发（TaskDispatcher）、重启处理（TaskRecovery）
│   │   ├── pipeline/    多步骤流水线 TaskPipeline + 三个步骤 + 状态存储端口
│   │   ├── simulation/  模拟耗时/失败配置与开发接口
│   │   ├── storage/     本地文件存储（数据卷）
│   │   ├── config/      线程池、Web 配置、配置属性
│   │   └── common/      统一错误响应
│   ├── src/main/resources/
│   │   ├── db/migration/V1__init.sql            表结构
│   │   ├── db/migration/V2__seed_templates.sql  分类与模板种子数据
│   │   └── static/demo/covers/                  模板封面（程序生成）
│   └── src/test/        流水线单元测试 + HTTP 集成测试（H2）
├── frontend/                        Next.js 应用
│   ├── app/             页面：/ 模板列表、/templates/[id] 详情制作、/history 我的记录、/login
│   ├── components/      CreatePanel、UploadSlot、TaskBoard、TaskCard、Modal…
│   ├── lib/api/         请求封装（区分 network / unauthorized / http 错误）与类型
│   ├── lib/tasks/       任务合并规则 mergeTask、轮询 useTaskBoard、提交 useCreateTask
│   ├── lib/upload/      上传位状态机 useUploadSlots、前端校验、XHR 上传
│   ├── middleware.ts    未登录跳转
│   └── tests/           Vitest 测试
├── samples/                         可直接用来上传的示例图片（含反例）
├── scripts/generate_demo_images.py  生成封面与示例图片的脚本
├── scripts/sim.sh                   调整模拟耗时/失败阶段的辅助脚本
├── docker-compose.yml
├── .env.example
└── .github/workflows/ci.yml
```

## 3. 启动、构建与测试

### 方式一：Docker Compose（推荐）

```bash
cp .env.example .env          # 可选，不复制也会使用相同的默认值
docker compose up --build
```

- **国内网络**：如果构建卡在 `mvn dependency:go-offline` 或 `npm ci`，在 `.env` 中设置 `MAVEN_MIRROR=https://maven.aliyun.com/repository/public` 和 `NPM_REGISTRY=https://registry.npmmirror.com` 后重新构建（`.env.example` 中已有注释好的示例）。
- 网站：http://localhost:3000，用 `alice@demo.local` / `demo1234` 登录（也可以用 `bob@demo.local`，或自己注册）
- 后端：http://localhost:8080（也可以直接调接口，见第 6 节）
- MySQL 首次启动后，Flyway 会自动建表并写入模板数据；后端启动时会创建两个演示账号。
- 数据卷：`mysql-data` 保存数据库，`files-data` 保存上传素材和结果图。`docker compose down -v` 会清空这两个卷。

### 方式二：本地分别启动

需要 JDK 21、Maven 3.9+、Node 22，以及一个本地的 MySQL 8：

```bash
# 1) 数据库
mysql -uroot -p -e "CREATE DATABASE mockstudio DEFAULT CHARSET utf8mb4; \
  CREATE USER 'mockstudio'@'%' IDENTIFIED BY 'mockstudio'; GRANT ALL ON mockstudio.* TO 'mockstudio'@'%';"

# 2) 后端（默认连接 localhost:3306/mockstudio，可用 DB_URL/DB_USERNAME/DB_PASSWORD 覆盖）
cd backend
DEV_API_ENABLED=true mvn spring-boot:run

# 3) 前端（/api 与 /demo 由 Next 代理到 API_INTERNAL_URL，默认 http://localhost:8080）
cd frontend
npm install
npm run dev
```

### 测试与检查

```bash
cd backend  && mvn verify                  # 编译 + 单元测试 + 集成测试（H2，不需要 Docker）+ 打包
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

CI（`.github/workflows/ci.yml`）在每次 push 和 pull request 时运行同样的检查，然后用 `docker compose up` 启动整套服务（MySQL + Java + Next.js），执行端到端冒烟测试 `.github/scripts/e2e.sh`。测试经由 Next.js 代理完成：登录 → 上传 → 提交 → 观察到 RUNNING → 成功并按顺序完成三步 → 注入 GENERATE 失败（FINALIZE 不执行）→ 上传校验 → 用户隔离 → 重启后端后历史仍在。失败时，CI 会把关键输出写成注解，便于查看。

## 4. 配置项

所有值都是非敏感的演示默认值；生产环境请通过环境变量覆盖。

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 本地 mockstudio 库 | MySQL 连接 |
| `STORAGE_ROOT` | `./data/files`（容器内为 `/data/files`） | 素材与结果文件的根目录 |
| `EXECUTOR_CORE_POOL_SIZE` / `EXECUTOR_MAX_POOL_SIZE` | 2 / 2 | 后台并行执行的任务数 |
| `EXECUTOR_QUEUE_CAPACITY` | 100 | 排队上限；满了以后提交接口返回 503 |
| `SIM_DELAY_ASSET_CHECK_MS` / `SIM_DELAY_GENERATE_MS` / `SIM_DELAY_FINALIZE_MS` | 1500 / 4000 / 1500 | 各阶段的模拟耗时 |
| `SIM_FAIL_STAGE` | 空 | 启动时默认的失败阶段 |
| `DEV_API_ENABLED` | 本地默认 `false`，compose 中为 `true` | 是否开放 `/api/dev/simulation` |
| `DEMO_USERS_ENABLED` / `DEMO_USER_PASSWORD` | `true` / `demo1234` | 演示账号 |
| `COOKIE_SECURE` | `false` | HTTPS 部署时设为 `true` |
| `API_INTERNAL_URL`（前端） | `http://localhost:8080` | Next 代理和服务端组件访问 Java 的地址。**rewrites 在构建时确定**，Docker 中通过 build arg 传入 |

**模拟数据与素材的来源**：模板封面和 `samples/` 下的示例图片都由 `scripts/generate_demo_images.py`（基于 Pillow）绘制，已提交到仓库，不依赖外部资源。`samples/` 里有两个反例：`invalid-type.gif` 用来测类型校验；`too-large-for-sticker.png` 约 2.4MB，用来测「宠物贴纸」2MB 的大小限制。

## 5. 架构与后台执行

```
浏览器 ──► Next.js :3000 ──rewrites /api/*, /demo/*──► Spring Boot :8080 ──► MySQL
                │ RSC 直接 fetch 模板（公开数据）           │
                                                          ├─ ThreadPoolTaskExecutor（有界队列）
                                                          │     └─ TaskPipeline: ASSET_CHECK → GENERATE → FINALIZE
                                                          └─ 本地目录 / 数据卷（uploads/ work/ results/）
```

### 受理（R-04）

`POST /api/tasks` 依次做这些事：
1. 校验模板存在，图片数量正确、不重复，素材属于当前用户，类型和大小都符合模板要求；
2. 在一个事务里写入 `task(PENDING)` 和 `task_asset`，同时把当时的模拟配置快照写进 `task.sim_config`；
3. **事务提交之后**（`@TransactionalEventListener(AFTER_COMMIT)`）再投递到线程池，避免后台线程读到还没提交的记录；
4. 立即返回 `202` 和任务信息，不等制作完成。

关闭浏览器页面不会影响后台执行。线程池满时，提交接口直接返回 `503 SERVICE_BUSY`。

### 流水线（R-05）

`TaskPipeline` 按 `TaskStage` 声明的顺序依次执行三个 `TaskStep`；启动时会校验每个阶段恰好对应一个步骤。

- 每一步的边界都会写入数据库：先把 `task.stage` 更新为当前阶段，并插入一条 `task_step_log(RUNNING)`；完成时把这条日志更新为 `DONE`，失败时更新为 `FAILED` 并写入原因。这些更新各自独立提交，所以查询接口能看到真实的阶段变化，而不是前端按时间切换文案。
- **状态迁移都是带前置条件的 UPDATE**，例如 `... WHERE id=? AND status='RUNNING'`。终态（`SUCCEEDED` / `FAILED`）不会被改回处理中，同一个任务也不会被重复领取执行。
- 查询接口只读，不会推进任务。

三个步骤分别做什么：

| 阶段 | 做什么 |
|---|---|
| `ASSET_CHECK` 素材检查 | 从磁盘重新读取素材，检查文件存在、数量、大小与记录一致、文件头类型、能否用 ImageIO 解码 |
| `GENERATE` 模拟生成 | 用 Java2D 把素材按数量排版到 900×1200 的画布上，加边框和水印图形，写到 `work/{id}.jpg` |
| `FINALIZE` 结果整理 | 把产物原子移动到 `results/{id}.jpg`，记录可预览地址 `/api/tasks/{id}/result` |

### 任务状态与阶段

| 字段 | 取值 |
|---|---|
| `status` | `PENDING` 等待处理 · `RUNNING` 处理中 · `SUCCEEDED` 成功 · `FAILED` 失败 |
| `stage` | `ASSET_CHECK` / `GENERATE` / `FINALIZE`：处理中时表示当前阶段，失败时表示失败阶段 |
| `steps[]` | 每个阶段的 `status`（`RUNNING` / `DONE` / `FAILED`）、开始和结束时间、失败信息 |
| `errorCode` / `errorMessage` | 稳定的错误码和可读原因，例如 `SIMULATED_FAILURE`、`ASSET_MISSING` |

### 失败与模拟控制（R-06）

- 任一步骤抛出 `StepFailure`（预期内的业务失败）或其它异常（记为 `INTERNAL_ERROR`），流水线都会立即停止，后续步骤不会执行，失败阶段和原因写回数据库。
- 模拟器在每个阶段执行前，先按配置等待指定时间；如果配置的失败阶段就是当前阶段，就抛出 `SIMULATED_FAILURE`。失败是确定性的，可以重复触发，没有随机失败。
- 配置会在**任务创建时快照**。修改配置只影响之后新建的任务，正在跑的任务不受影响。

### 服务重启边界

本项目**不做断点续跑**。进程重启时，内存中的执行队列会丢失；启动时（Web 服务对外开放之前），`TaskRecovery` 会把遗留的 `PENDING` / `RUNNING` 任务标为 `FAILED`，原因是 `SERVICE_RESTARTED`，避免前端一直轮询一个永远不会结束的任务。

## 6. 主要接口

除了 `auth/login`、`auth/register`、`categories`、`templates`、`dev/*` 以外，其余接口都需要登录 Cookie `ms_session`。错误统一返回 `{ "code": "...", "message": "...", "details": {...} }`。

| 方法与路径 | 请求 | 响应 |
|---|---|---|
| `POST /api/auth/register` | `{email, password, displayName?}` | `201 {id, email, displayName, createdAt}` 并设置 Cookie |
| `POST /api/auth/login` | `{email, password}` | `200` 用户信息，并设置 Cookie |
| `POST /api/auth/logout` | – | `204`，清除 Cookie 和服务端会话 |
| `GET /api/auth/me` | – | 当前用户 |
| `GET /api/categories` | – | `[{id, name}]` |
| `GET /api/templates?category=` | – | `[{id, title, categoryId, categoryName, coverUrl, description, requiredImageCount, acceptedMimeTypes[], maxFileSizeBytes}]` |
| `GET /api/templates/{id}` | – | 单个模板；不存在时 `404 TEMPLATE_NOT_FOUND` |
| `POST /api/assets?templateId=` | multipart，字段名 `file` | `201 {id, url, mimeType, sizeBytes, originalFilename}`；错误码 `UNSUPPORTED_MIME` / `FILE_TOO_LARGE` / `EMPTY_FILE` |
| `GET /api/assets/{id}/content` | – | 图片本体（只能读取自己的素材） |
| `POST /api/tasks` | `{templateId, assetIds: number[]}` | `202 Task`（`status=PENDING`）；错误码 `IMAGE_COUNT_MISMATCH` / `DUPLICATE_ASSET` / `ASSET_NOT_FOUND` / `SERVICE_BUSY` 等 |
| `GET /api/tasks?templateId=&before=&limit=` | – | `{items: Task[], nextCursor}`，个人历史，按 id 倒序 |
| `GET /api/tasks?ids=1,2,3` | – | `{items: Task[]}`，批量查询（给前端轮询用），别人的 id 会被忽略 |
| `GET /api/tasks/{id}` | – | `Task`；别人的任务和不存在的任务一样返回 `404` |
| `GET /api/tasks/{id}/result` | – | 结果图（`image/jpeg`） |
| `GET/PUT /api/dev/simulation`，`POST /api/dev/simulation/reset` | `{delaysMs?: {STAGE: ms}, failStage?: "GENERATE" \| "NONE"}` | 当前模拟配置 |

`Task` 的结构：

```json
{
  "id": 12, "templateId": "demo-postcard", "templateTitle": "旅行明信片",
  "status": "FAILED", "stage": "GENERATE",
  "errorCode": "SIMULATED_FAILURE", "errorMessage": "模拟失败：已配置在「模拟生成」阶段失败",
  "resultUrl": null,
  "inputs": [{ "slotIndex": 0, "assetId": 31, "url": "/api/assets/31/content" }],
  "steps": [
    { "stage": "ASSET_CHECK", "status": "DONE",   "message": null, "startedAt": "…", "endedAt": "…" },
    { "stage": "GENERATE",    "status": "FAILED", "message": "模拟失败：…", "startedAt": "…", "endedAt": "…" }
  ],
  "createdAt": "…", "startedAt": "…", "finishedAt": "…", "updatedAt": "…"
}
```

## 7. 前端异步状态管理

- **上传位**（`useUploadSlots`）：每个上传位是一个独立的状态机：`empty → uploading(progress) → ready | error`。
  - 选图时先在前端校验类型和大小，再用 XHR 上传，以便拿到上传进度；
  - 替换或移除时，会取消正在进行的上传并释放本地预览地址；
  - 用 `uploadId` 丢弃过期的上传回调，避免旧回调覆盖新选的图片。
- **提交**（`useCreateTask`）：所有上传位都上传成功后，按钮才可点；提交中按钮禁用，并用 ref 防止同一帧内连续点击重复提交。后端受理（202）后，把任务插入列表缓存，卡片立即以「排队中」出现，按钮恢复可用。
- **跟踪**（`useTaskBoard`）：
  - 历史列表从 Java 读取，刷新页面后自然恢复，未结束的任务会继续被跟踪；
  - 只对**未结束**的任务用一个批量请求 `GET /api/tasks?ids=` 轮询（默认 1.5 秒一次）。某个任务结束后就从轮询集合中移出；没有未结束任务时，查询被禁用，不再发请求；标签页隐藏时暂停轮询；
  - 合并规则 `mergeTask`：只按 id 合并；终态不会被处理中覆盖；`updatedAt` 更早的乱序响应会被丢弃；
  - **查询失败 ≠ 任务失败**：网络或 5xx 错误只显示「状态同步失败」横幅，并提供「立即重试」按钮，同时按指数退避自动重试（最长 15 秒）。任务卡片保持最后一次已知的状态。只有服务端返回 `status=FAILED`，卡片才会显示失败；
  - **清理**：组件卸载时，TanStack Query 会通过 AbortSignal 取消正在进行的请求。缓存 key 都以 `userId` 开头。退出或切换账号时，先取消在途请求再清空缓存；轮询响应写回前会再核对一次当前用户，旧用户的晚到响应会被丢弃。

## 8. 演示方法

先登录（`alice@demo.local` / `demo1234`）。

| 演示点 | 操作 |
|---|---|
| **成功** | 进入「萌宠肖像」，上传 `samples/pet.png`，点击「立即制作」。卡片依次显示 排队中 → 处理中·素材检查 → 模拟生成 → 结果整理 → 结果图，点击结果图可放大预览。 |
| **指定阶段失败** | 运行 `./scripts/sim.sh fail GENERATE`（或 `curl -X PUT localhost:8080/api/dev/simulation -H 'Content-Type: application/json' -d '{"failStage":"GENERATE"}'`）后再提交，卡片显示「制作失败 · 失败阶段：模拟生成」和原因，结果整理阶段不会执行。把 `GENERATE` 换成 `ASSET_CHECK` 或 `FINALIZE` 可以演示其它阶段。`./scripts/sim.sh ok` 恢复。 |
| **调整耗时** | `./scripts/sim.sh slow 15000`（模拟生成 15 秒），或 `./scripts/sim.sh delays 3000 8000 3000`；也可以在 `.env` 里改 `SIM_DELAY_*_MS` 后重启。 |
| **多任务** | 调慢以后连续提交几次（也可以打开多个模板），每张卡片各自显示阶段；再配合 `fail` 让其中一部分失败，可以看到各任务的状态、阶段、错误和结果互不干扰。线程池默认 2 个并行，其余的显示「排队中」。 |
| **刷新恢复** | 任务处理中时刷新页面，或关掉标签页再打开，或到「我的记录」页：记录从 Java 读回，未结束的任务会继续被跟踪直到结束。 |
| **查询异常 ≠ 任务失败** | 任务处理中时，在浏览器 DevTools → Network 选择 Offline（或执行 `docker compose stop backend`）：页面出现「状态同步失败」，卡片仍显示处理中；恢复网络后点「立即重试」或等自动重试即可继续。注意：停掉后端会中断它正在执行的任务，重启后这些任务会被标记为「服务重启导致中断」，这是第 5 节说明的边界行为。 |
| **上传校验** | 上传 `samples/invalid-type.gif` 会被前端拦截；在「宠物贴纸」（仅 PNG、≤2MB）上传 `.jpg` 或 `too-large-for-sticker.png` 会提示原因；用 curl 绕过前端直接上传，同样会被后端按文件头和大小拒绝。 |
| **用户隔离** | Alice 提交几个任务后，从右上角「账户设置 → 退出登录 / 切换账号」换成 Bob 登录：Bob 的历史是空的；直接访问 `GET /api/tasks/{alice 的任务 id}` 返回 404。 |

## 9. 用户身份与个人记录

- **身份**：邮箱 + 密码（BCrypt）。登录后生成 256 位随机 token，放在 `HttpOnly; SameSite=Lax` 的 Cookie 里；数据库 `user_session` 只保存它的 SHA-256 值，有效期 7 天。退出登录会删除服务端会话。
- **鉴权**：`AuthInterceptor` 拦截 `/api/**`，解析出当前用户后注入到 Controller 参数 `AuthUser`。没有引入完整的 Spring Security，这是有意的简化。
- **个人记录**：`asset`、`task` 都带有 `user_id`，所有查询都按当前用户过滤。访问别人的资源统一返回 404，不暴露资源是否存在；提交任务时也会校验素材属于当前用户。
- **前端**：`middleware.ts` 拦截没有 Cookie 的访问并跳到登录页；接口返回 401 时会清空缓存并跳回登录页；切换账号时会清空全部用户数据缓存。

## 10. 完成情况、已知限制与取舍

**已完成**：R-01 ~ R-12 的功能与测试代码、R-13 的说明文档、R-14 的 Compose 配置。

**未实现 / 已知限制**：
- 不做服务重启后的自动恢复执行（见第 5 节）。
- 不做任务受理幂等：前端已经能防止重复点击，但网络重试仍可能产生重复任务。可选方案是让客户端带上 `Idempotency-Key`，后端用 `(user_id, key)` 唯一索引来去重。
- 不做断点续跑和自动重试：失败后需要用户重新提交（题目不要求）。
- 历史列表只加载最近 50 条。后端已支持 `before` 游标分页，前端还没有做「加载更多」。
- 轮询而非推送：任务多时请求量随轮询频率线性增长；规模变大后可以换成 SSE。
- 素材没有清理策略，删除账号等功能不在范围内。
- 单实例设计：线程池在进程内，多实例部署需要换成数据库抢占（`UPDATE … WHERE status='PENDING'` 已经天然支持）或消息队列。

**验证情况**：GitHub Actions 全部通过，包括：
- 前端 lint、类型检查、Vitest、生产构建；
- 后端 16 个测试和打包；
- docker compose 构建与端到端冒烟测试。

本地开发模式（`npm run dev` + `mvn spring-boot:run`）的浏览器交互没有自动化测试，需要手动演示（见第 8 节）。

**小说明**：因为模板详情页有 `loading.tsx` 流式加载骨架，访问不存在的模板时页面会渲染「模板不存在或已下线」，但 HTTP 状态码是 200 而不是 404，这是 Next.js 流式渲染的行为。接口层 `GET /api/templates/{id}` 正常返回 404。


## 11. 部署与回滚

- **部署**：在目标机器上 `git checkout <tag>`，然后 `docker compose up -d --build`。Flyway 会在后端启动时自动执行新的迁移。
- **回滚**：`git checkout <上一个 tag>`，然后 `docker compose up -d --build`。迁移只增不改；如果新版本带有破坏性迁移，需要先备份数据库（`docker compose exec mysql mysqldump …`），回滚时再恢复。数据卷 `files-data` 中的文件和版本无关，回滚时不需要处理。

## 12. AI 使用说明

本项目使用了 AI 编程助手（Claude）。工作流如下：

1. **拆解任务**：先让 AI 逐条解析题目 R-01 ~ R-14，产出实现方案（数据模型、接口、流水线、前端状态设计），人工确认后再开始编码。
2. **提供上下文**：把题目截图、技术栈和版本约束、明确的取舍（轮询而非 SSE、H2 测试、不做断点续跑）作为上下文；按「骨架 → 后端 → 后端测试 → 前端 → 前端测试 → 交付」的顺序分阶段生成。
3. **生成与修改**：每个阶段由 AI 生成代码，人工审阅关键逻辑：状态迁移的条件 UPDATE、AFTER_COMMIT 派发、`mergeTask` 的终态保护、轮询的停止与清理。
4. **验证与修正**：用测试固定核心行为（步骤顺序、失败中断、终态不回退、用户隔离、多任务互不干扰、查询异常 ≠ 任务失败），以 CI 结果为准。
   - 例子：开发接口最初把「未传 failStage」当作「清除失败配置」，导致 `sim.sh slow` 会悄悄取消已经配置好的失败阶段。发现后改成「不传 = 保持不变，传 `NONE` = 清除」。


## 13. 素材与第三方代码来源

- 封面与示例图片：由 `scripts/generate_demo_images.py` 自行绘制，没有使用第三方图片。
- 没有使用项目脚手架模板，配置文件为手写。依赖均为上面列出的开源库，遵循各自的许可证。
