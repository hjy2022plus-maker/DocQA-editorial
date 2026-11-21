# DocQA 单机运行

默认后端入口读取 `docqa.yml`。根目录 Compose 只编排 MySQL、Elasticsearch、后端和前端。Java 17 直接解析文本层，不需要 Kafka、Redis、MinIO、OCR、LiteParse 或 WebSocket。历史源码与资料未删除；Maven 按白名单编译轻量实现，旧企业版资源不打入后端包。

## 运行配置

复制 `.env.example`，填写 MySQL/ES 密码、随机生成的 `JWT_SECRET_KEY`、生成和 Embedding 接口地址及密钥。启用 Rerank 时设置 `SEARCH_RERANK_URL`（当前使用 DashScope 请求格式）。本地 OpenAI 兼容模型使用对应的 `/v1` 基地址；如 Embedding 服务不接受 `dimensions` 参数，将 `EMBEDDING_SEND_DIMENSION` 设为 `false`。需要管理员时，首次启动临时配置 `ADMIN_BOOTSTRAP_ENABLED`、用户名和密码。

默认创建 `docqa_*` MySQL 表和独立的 `docqa_chunks_v1` 索引。旧版用户、文档、会话以及索引不会自动迁移；需要重新注册和上传。更换 Embedding 模型或维度时应该使用新索引并重新建立语料，避免混用不可比较的向量。原始文件保存在 `APP_STORAGE_ROOT`；容器使用持久卷。

## 文档与问答

- 普通 multipart 整文件上传，同一用户按 MD5 去重；仅支持可提取文本的 PDF、DOC、DOCX、Markdown、TXT。PDF 保留页码；Tika 处理 Word，解析由 `@Async` 线程池执行。
- MySQL 保存文档、分块、会话和引用映射；ES 保存分块文本与 Embedding。两路召回在 ES 中先按 MySQL 可见文档 ID 过滤，再由 BM25、KNN、RRF、Rerank 选取证据。相邻分块只扩展模型上下文，引用仍指向命中的分块。
- `POST /api/v1/lightweight/qa/stream` 使用 JWT 与 SSE。无足够证据时拒答；模型、检索出错时发出错误，不冒充无答案。流结束后以 MySQL 中完成的会话为准，不做重连与快照。
- 登录后可读公开文档；私有文档仅上传者可读。后台只提供基本的用户与角色查看。

## 评测口径

`docs/eval` 下的既有报告是历史基线，不代表本次轻量链路已实测。按事实、精确名词、跨段综合、无答案四类题目整理新语料；记录召回准确性、引用定位与拒答。保持语料、模型与参数固定进行检索对比，并通过 `SEARCH_RERANK_ENABLED`、`SEARCH_PARENT_CONTEXT_ENABLED` 对比模块开关。SSE 的 `done` 给出首字时延和端到端时延（毫秒）。不做并发压测或异常恢复实验。

## 论文结构

1. 文档知识库构建与语义索引
2. 混合检索与重排序
3. 上下文扩展、引用映射与交互实现
4. 评测集构建与对比、消融实验
5. 关键技术难点与解决方案
6. 创新点与应用价值
