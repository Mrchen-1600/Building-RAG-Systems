# 🚀 高级RAG系统 - Demo

本项目是一个基于 Java 17 + Spring Boot 3 + LangChain4j 构建的工业级检索增强生成（RAG）系统。旨在彻底解决传统朴素 RAG（Naive RAG）在真实业务场景中面临的词汇鸿沟、上下文割裂、多步推理失败、严重幻觉以及缺乏动态执行力等致命痛点。

系统融合了当前业界最前沿的大模型落地实践，包括：Self-RAG（自我反思）、RAPTOR（树状宏观摘要）、IRCoT（交错检索思维链）、长短期记忆管理以及 Agentic Skill 渐进式披露。

## 📂 核心目录结构

```text
src/main/java/com/example/demo02criticalissues/
├── chat/               # 大模型配置 (DashScope Qwen)
├── chunker/            # 高级切分策略 (父子块切分 ParentChildChunker、句子滑窗 SentenceWindowChunker)
├── context/            # 上下文窗口管理 (ContextWindow 结构化提示词设计)
├── controller/         # REST API 对外接口
├── entity/             # 数据库实体类 (对话归档、用户画像、RAPTOR 树节点等)
├── parser/             # 文档解析器 (Word, HTML, Markdown)
├── repository/         # Spring Data JPA 仓储接口
├── retriever/          # 检索器与重排序 (HybridRerank 混合重排、IRCoT 迭代检索器)
├── router/             # 意图网关 (LLM 逻辑路由、Semantic 语义路由)
├── service/            # 核心业务逻辑层
│   ├── AdvancedRAGService.java      # RAG 核心主控流水线 (主调度器)
│   ├── HyDEService.java             # 假设性文档生成增强检索
│   ├── QueryRewriteService.java     # 查询重写 (术语规范化、上下文补全)
│   ├── RAPTORService.java           # RAPTOR 树状摘要构建与检索
│   ├── SelfRAGService.java          # 自我反思与评判引擎 (防止幻觉)
│   ├── ShortTermMemoryService.java  # 短期记忆滚动压缩管理
│   └── UserProfileService.java      # 长期记忆 (用户画像) 提取与更新
├── skill/              # Agentic Skill 管理引擎 (渐进式披露加载)
├── tools/              # 智能体工具 (Text2SQLTool 数据库与系统脚本执行)
├── utils/              # 工具类 (JSON 安全提取等)
└── test/               # 核心功能演示入口 (AdvancedRAGTest)
```

## 🧠 核心技术实现方案
### 1. 结构化上下文窗口设计 (Context Window)

抛弃了传统 RAG 简单的“资料+问题”拼接，设计了类似 XML 标签的结构化 Prompt 容器。

- **机制：** 划分为 \<system prompt>, \<core rules>, \<tools or Skills>, \<memory>, \<retrieved context>, \<input> 六大模块。

- **优势：** 严格界定大模型的行为边界，防止提示词注入；让模型时刻保持一致的人设和记忆连贯性。

### 2. 双轨记忆管理 (Long/Short Term Memory)

- **短期记忆 (Rolling Summary)：** 防止对话轮数过多导致 Token 爆仓。当历史对话达到阈值（如3轮）时，自动触发大模型将新对话“融合”到旧的全局摘要中，生成新的滚动摘要，随后清空历史，实现无损降维。

- **长期记忆 (User Profile)：** 通过异步分析对话摘要中的兴趣点与高频词，更新存储在数据库中的用户画像（如专业水平、提问偏好），后续对话自动注入以实现高度个性化。

### 3. 多维查询重写 (Query Rewrite)

填平用户口语化提问与专业文档之间的“词汇鸿沟”。

- **术语规范化：** 将“那个发票咋贴”翻译为“差旅交通费凭证粘贴规范”。

- **上下文补全：** 利用短期记忆，将包含代词的追问（如“那打车的呢？”）补全为携带独立完整语义的检索长句，大幅提升向量检索命中率。

### 4. 假设性文档嵌入增强 (HyDE)

- **机制：** 遇到缺乏上下文线索的极其抽象的技术提问（如“HashMap扩容原理”）时，先让 LLM 凭训练记忆“盲答”生成一段假答案。用这段富含专业特征词汇的假答案去向量库中做匹配。

- **优势：** 通过“答案匹配答案”的降维打击，将原本极低的召回置信度拉升至精准召回。

### 5. 双层意图路由网关 (Intent Routing)

- **语义路由 (Semantic Router)：** 基于本地轻量级向量计算，将用户 Query 与预设的“闲聊/文档查询/SQL查询”主题向量计算余弦相似度，0延迟、无 Token 消耗，作为第一道高频流量分发网关。

- **逻辑路由 (LLM Router)：** 基于大模型强大的推理能力，处理复杂的隐含意图，作为兜底防线。

### 6. 破除上下文割裂的切分策略 (Chunking Strategies)

- **父子块检索 (Parent-Child)：** 建两次索引，切分小句入向量库，大段落入关系库。小块精准命中后，顺藤摸瓜返回完整大段落，给大模型提供充分的前因后果。

- **句子窗口检索 (Sentence Window)：** 按单句切分，命中某句后，自动向前后滑动扩展 K 句话，拼装成连贯上下文。

### 7. RAPTOR 树状摘要与宏观问题检索

解决传统 RAG 无法回答“请总结这100页研报核心观点”的痛点。

- **构建：** 底层 Chunk -> K-Means 聚类 -> LLM 生成局部摘要 -> 再次聚类... 最终生成 Root 顶层全局摘要。

- **检索：** 基于用户问题意图动态决定探查树的层级。宏观问题直接抓取 Root 摘要，微观问题探底至叶子节点。

### 8. IRCoT 交错检索思维链

处理跨越多个文档才能拼凑出答案的多跳（Multi-hop）推理。

- 系统转变为一个会“自我提问”的 Agent。执行 `思考 -> 搜索短句 -> 观察 -> 再思考生成新搜索词` 的循环，直到收集齐所有拼图。

### 9. Self-RAG 自我反思与 Fail-Fast 熔断机制

抛弃传统的“盲目检索+强行生成”单向流水线，引入大模型裁判委员会。

- **三大裁判：** 检索相关性裁判、幻觉裁判、答案有用性裁判。

- **Fail-Fast (快速熔断)：** 在裁判判定失败时（尤其是严重脱离文档的幻觉），系统绝不死循环浪费 Token，而是快速阻断并回退到安全的兜底策略（如婉拒回答或请求人工介入）。

### 10. Agentic Skill 渐进式披露与系统防线

- **渐进式披露：** 系统中预置了复杂的工具说明（如 skills.md）。平时这些内容不载入内存；只有当路由判定需要执行复杂任务（如 Text2SQL）时，才动态加载包含详细表结构、参考规范 (refs) 和脚本列表 (scripts) 的长 Prompt，极大节省常规对话的 Token 消耗。

- **防 OOM 与注入拦截：** Text2SQL 工具内置了指令级注入拦截，并在下发底层执行前强制追加 LIMIT 防护，彻底杜绝大模型生成的恶劣 SQL 导致千万级数据撑爆 JVM 的严重线上事故。

## 🔌 REST API 文档
基础路径: /api/rag

### 1. 对话接口 (POST /chat)
请求体:
```json
{
"userId": "user_001",
"sessionId": "session_1001",
"query": "咱们公司最新的差旅发票应该怎么贴？"
}
```

响应体:
```json
{
"answer": "根据差旅报销规范，交通费发票必须平铺粘贴在A4纸左上角...",
"sessionId": "session_1001",
"reasoning": "匹配到主题: 向量库检索",
"retrievalStrategy": "VECTOR_SEARCH",
"usedTools": ["IntentRouting", "VectorSearch", "QueryRewrite", "HybridRerank", "SelfRAG"]
}
```

### 2. 意图分析独立测试 (POST /intent)

请求体:

```json
{
  "userId": "user_001",
  "query": "帮我统计一下数据库里的总用户数"
}
```

### 3. 系统健康检查 (GET /health)

响应体:

```json
{
  "status": "OK",
  "message": "RAG服务正常运行"
}
```

## 参考资料

《02_Naive RAG存在的致命问题与解决方案.md》
- 痛点1：用户提问质量差与词汇鸿沟
- 痛点2：文本切分带来的上下文割裂
- 痛点3：无法处理跨文档和全局总结的复杂推理
- 痛点4：流程僵化，缺乏自我纠错能力

