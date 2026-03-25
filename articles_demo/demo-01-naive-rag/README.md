# Demo 01: Naive RAG 标准流程

这是一个完整的 RAG（Retrieval-Augmented Generation，检索增强生成）标准流程 Demo 项目，对应文章: [Naive RAG 的标准处理流程.md](../../articles/01 Naive RAG 的标准处理流程.md)

基于 Java 17 实现，展示了从文档解析、分块、向量化、存储、检索到重排序的完整流程。

## Demo 简介

本 Demo 按照 Naive RAG 的标准处理流程实现，包含以下核心功能：

1. **文档解析**：支持 Markdown、Word、HTML 三种格式
2. **分块策略**：实现固定大小分块、递归分块、语义分块三种策略
3. **文本向量化**：调用 Qwen Embedding API
4. **向量存储**：基于 Elasticsearch 的向量存储
5**结果重排序**：使用 Qwen Reranker API 进行精细排序
6**提示词增强**：构建增强提示词模板

## 技术栈

| 组件 | 技术                     |
|------|------------------------|
| 开发语言 | Java 17                |
| 构建工具 | Maven 3.9.2            |
| 框架 | Spring Boot 3.4.0      |
| 向量数据库 | Elasticsearch 8.17.10  |
| Embedding 模型 | Qwen text-embedding-v3 |
| Reranker 模型 | Qwen gte-rerank        |
| 文档解析 | Apache POI, JSoup      |
| RAG 框架 | LangChain4j 0.36.2     |

## 环境准备

### 1. Java 17

确保已安装 Java 17 并配置环境变量：

```bash
java -version
# 输出应显示 java version "17.x.x"
```

### 2. Elasticsearch 8.17.10

下载并启动 Elasticsearch：

```bash
# Windows
下载并解压 Elasticsearch 8.17.10
运行 bin\elasticsearch.bat

# Linux/Mac
brew install elasticsearch@8.17
elasticsearch
```

验证 Elasticsearch 运行状态：

```bash
curl https://localhost:9200
# 如果关闭 TLS 加密
curl http://localhost:9200
```

elasticsearch 配置：

elasticsearch 8.17.10 版本默认开启安全防护和 TLS 加密，每次访问需要输入账号（默认`elastic`）和密码（第一次启动时提供的超级管理员密码）。
为方便后续使用，可以关闭保护和 TLS 加密。

找到你的 Elasticsearch 安装目录。

打开 config/elasticsearch.yml 文件。

找到以下配置项，并将 true 改为 false：

```yaml
# 找到这一行，将 true 改为 false
xpack.security.http.ssl:
enabled: false
```

如果连登录密码也不想要：
```yaml
xpack.security.enabled: false
```


### 3. Qwen API 密钥

1. 访问 [阿里云 DashScope 控制台](https://bailian.console.aliyun.com/)
2. 开通服务并获取 API Key
3. 配置到项目的 `application.yml` 中

## 配置 + 运行步骤

### 步骤 1: 配置文件

编辑 `src/main/resources/application.yml`，配置以下参数：

```yaml
# Qwen API 密钥（必填）
qwen:
  api-key: your-actual-api-key-here
```

由于作者已关闭 elasticsearch 8.17.10 的 TLS 加密和安全防护，所以无需配置 es 的账号和密码。


### 步骤 2: 编译项目

```bash
mvn clean install
```

### 步骤 3: 运行应用

运行主类：

```bash
java -jar target/rag-standard-pipeline-1.0-SNAPSHOT.jar
```

### 步骤 4: 观察测试输出

应用启动后会自动执行 RAG Demo 测试，输出包括：

1. 文档解析结果
2. 三种分块策略的对比
3. 向量化和存储状态
4. 检索测试
5. 重排序效果
6. 提示词增强示例

## 项目目录结构

```
demo-01-naive-rag/
├── src/
│   ├── main/
│   │   ├── java/com/example/demo01naiverag/
│   │   │   ├── chunker/                        # 分块策略
│   │   │   │   ├── FixedChunker.java           # 固定大小分块
│   │   │   │   ├── RecursiveChunker.java       # 递归分块
│   │   │   │   └── SemanticChunker.java        # 语义分块
│   │   │   ├── parser/                         # 文档解析器
│   │   │   │   ├── MarkdownParser.java         # Markdown 解析
│   │   │   │   ├── WordParser.java             # Word 解析
│   │   │   │   └── HtmlParser.java             # HTML 解析
│   │   │   ├── retriever/                      # 检索器
│   │   │   │   ├── HybridRetriever.java        # 混合检索
│   │   │   ├── embedding/                      # embedding 模型
│   │   │   │   └── EmbeddingModelConfig.java   # 初始化 Embedding 模型
│   │   │   ├── chat/                           # chat 模型
│   │   │   │   └── ChatModelConfig.java        # 初始化 Chat 模型
│   │   │   ├── store/                          # 存储库
│   │   │   │   └── ESStoreService.java         # Elasticsearch 存储向量
│   │   │   ├── RagSpringBootApplication.java   # 主应用类
│   │   │   ├── RagRunner.java                  # Bean，启动后自动运行 Rag Demo 的测试
│   │   └── resources/
│   │       ├── files/                      # 示例文档
│   │       │   ├── sample.md               # Markdown 示例
│   │       │   ├── sample.docx             # Word 示例
│   │       │   └── sample.html             # HTML 示例
│   │       └── application-dev.yml         # 配置文件
├── pom.xml                                 # Maven 配置
└── README.md                               # 本文档
```

## Naive RAG 流程总结

```
┌─────────────────────────────────────────────────────────────────┐
│                        离线数据处理（建库）                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 文档解析                                                     │
│     ↓                                                           │
│  2. 文本分块（固定大小 / 递归 / 语义）                            │
│     ↓                                                           │
│  3. 文本向量化（Qwen Embedding）                                 │
│     ↓                                                           │
│  4. 向量存储（Elasticsearch）                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      在线检索生成（问答）                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  5. 用户提问                                                     │
│     ↓                                                           │
│  6. 检索                                                        │
│     ↓                                                           │
│  7. 重排序（Qwen Reranker）                                      │
│     ↓                                                           │
│  8. 提示词增强                                                   │
│     ↓                                                           │
│  9. LLM 生成最终回答                                             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 常见问题

### Q1: Elasticsearch 连接失败

确保 Elasticsearch 已启动并监听 9200 端口：

```bash
curl http://localhost:9200
```

### Q2: API 密钥错误

检查 `application-dev.yml` 中的 `qwen.api-key` 是否正确配置。


### Q3: 语义分块失败

语义分块需要调用 Embedding API，请检查网络连接和 API 配额。

## 参考资料

- [Naive RAG 的标准处理流程文档](../../articles/01 Naive RAG 的标准处理流程.md)
- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [Elasticsearch 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/8.17/index.html)
- [Qwen API 文档](https://help.aliyun.com/zh/dashscope/developer-reference/api-details)

## 许可证

本项目仅用于学习和演示目的。
