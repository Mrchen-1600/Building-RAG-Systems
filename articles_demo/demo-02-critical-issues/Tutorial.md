# 🛠️ 零基础运行指南：从零启动 RAG 项目

## 📍 1. 环境准备

在启动项目之前，你需要准备好以下基础设施：

1. **Java 开发环境:** 安装 **JDK 17** 或以上版本。

2. **构建工具:** Maven 3.6+。

3. **数据库:** **MySQL 8.0+**。

  - **极简配置：** 你只需要在 MySQL 中创建一个名为 rag_system 的空数据库即可。**无需手动建表或插数据！** Spring Boot 启动时会自动执行 schema.sql 建表，并利用 data.sql 自动填充供演示测试用的虚拟文档和配置数据。
    ```sql
    CREATE DATABASE rag_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    ```

4. **阿里云 DashScope API Key:**
- 本项目默认使用阿里云通义千问（Qwen）大模型。你需要前往阿里云百炼控制台申请一个免费的 API-KEY。

5. Elasticsearch (可选): 默认配置连接本地 9200 端口。如果不装也没关系！ 代码中已经做了智能容错：如果连不上 ES，系统会自动无缝降级使用 **内存向量库（InMemoryEmbeddingStore）**，丝毫不影响你的功能演示和测试。

## ⚙️ 2. 项目配置修改

使用你喜欢的 IDE（如 IntelliJ IDEA 或 VS Code）打开项目。

找到配置文件：src/main/resources/application.yml

你需要修改以下关键配置：

### 2.1 填入 API Key

找到 `dashscope` 节点，将你的 API Key 填入：
```yaml
dashscope:
  api-key: "sk-xxxxxx你的真实KEYxxxxxxxx"  # <--- 填在这里
  embedding-model: "text-embedding-v3"
  chat-model: "qwen-flash"
  # ...
```

### 2.2 修改 MySQL 连接信息

找到 `spring.datasource` 节点，如果你的本地 MySQL 账号密码不是 root/root，或者端口不是 3306，请修改：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rag_system?useUnicode=true...
    username: root          # <--- 修改为你的数据库账号
    password: root          # <--- 修改为你的数据库密码
```

## 🚀 3. 启动项目

在你的 IDE 中找到启动类 `RagSpringBootApplication.java`，右键点击 **Run 'RagSpringBootApplication.main()'**。

或者在项目根目录下打开终端，执行 Maven 命令：
```bash
mvn spring-boot:run
```

**观察启动日志：**

- 看到 `HikariPool-1 - Start completed`，说明 MySQL 连接成功。

- 看到 `无法连接到 Elasticsearch，已自动降级为【内存向量库】`，别慌，这是系统在保护你，说明已经启用了内存库。

- 最后看到` Started RagSpringBootApplication in xx seconds`，说明 Web 服务已成功挂载到 `8080` 端口。

## 👀 4. 验证核心功能

为了方便你理解这个系统，我们在项目中编写了一个测试类：`AdvancedRAGTest.java`。
项目启动成功后，它会自动在控制台（Console）进行打印运行日志。请盯着控制台仔细观察：

### 🎬 演示一：查询重写与语境补全

你会在日志中看到用户问了一句极不规范的方言：“那个打车发票咋贴啊，会退回不”。
接着，大模型会把它重写成：“差旅报销中打车发票的粘贴规范是什么？”，精准命中知识库。
**亮点验证：** 注意看“多轮对话补全”场景，当追问 “那打车的呢？” 时，系统能凭借短期记忆自动把主语补齐。

### 🎬 演示二：HyDE 高难度召回

系统面临硬核考题：“HashMap扩容原理”。
**亮点验证：** 注意看日志，系统会先“假装”回答一段技术文档（包含负载因子、链表等词），然后用这个假回答去钓出了真正标准答案。

### 🎬 演示三 & 四：上下文切分与 RAPTOR 全局总结

**亮点验证：** 注意查看 RAPTOR 演示环节，系统面对 “总结100页研报” 这种宏观问题时，没有去搜碎片，而是直接命中并返回了数据库中预先聚类好的 **Root 全局摘要**。

### 🎬 演示五：Agentic RAG 的 SQL 技能（渐进式披露与防 OOM）

用户提问：“帮我查询结构化数据库，统计当前的用户总量是多少？” 或 “执行安全清理脚本”。
**亮点验证：**

1. 日志会显示意图路由成功切轨到 `STRUCTURED_QUERY`。

2. 观察控制台打印的提示词，你会发现此时系统刚刚**动态加载**了庞大的 `skills.md` 规范。

3. 观察执行日志，留意 `【系统防护】为 SQL 追加限制防止 OOM: SELECT * ... LIMIT 100`。这证明我们在代码底层硬编码的防内存溢出机制已成功触发！

4. 大模型最后会将干瘪的数据库结果，转化为优雅的文字汇报给用户。

### 🎬 演示六：长短期记忆滚动压缩

**亮点验证：** 留意系统连问了 4 个技术问题后触发的动作。系统不仅没有遗忘，反而生成了一段**包含以往旧摘要和最新一轮对话的「滚动摘要 (Rolling Summary)」**，并且清空了长长的历史对话数组。这代表 Tokens 被大幅节省了！

## 🛠️ 5. 使用 Postman / cURL 进行 API 测试

如果你想脱离测试脚本，像真实产品一样与它聊天，可以使用 API 工具发送请求。

**发送对话请求：**
打开命令行终端，执行：
```bash
curl -X POST http://localhost:8080/api/rag/chat \
-H "Content-Type: application/json" \
-d '{
  "userId": "test_user_001",
  "sessionId": "session_888",
  "query": "公司的差旅报销标准里，每天餐补是多少钱？"
}'
```

**响应验证：**
你将获得一个 JSON 格式的返回，其中 `answer` 就是大模型经过重写、检索、重排序、幻觉评判后，最终生成的高质量回答；同时 `usedTools` 字段会为你揭秘这次回答背后到底启用了什么工具！

🎉 恭喜你！你现在已经成功运行了本项目！