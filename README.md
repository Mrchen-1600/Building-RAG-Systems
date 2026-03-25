# 🚀 Building-RAG-Systems: From Theory to Production

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](http://makeapullrequest.com)

> 本项目致力于提供一条平滑且深入的 RAG（检索增强生成）学习与实践路径。通过**深度文章解析**、**配套 Demo代码** 以及**系统级项目实战**，带你从零构建工业可用的大模型应用。

## 📂 仓库结构 (Repository Structure)

本项目按照“理论 -> 配套demo -> 系统级实践”的逻辑组织，主要包含以下三个核心模块：

```text
📦 RAG-Mastery
 ┣ 📂 articles         # 📖 核心知识库：RAG 理论解析、架构设计及痛点解决方案
 ┣ 📂 articles_demo    # 💻 Demo代码：与文章配套的开箱即用 Demo 代码
 ┗ 📂 project          # 🏗️ 系统级项目：融合所有知识点的全功能系统架构
```

### 1️⃣ `articles`：深入浅出的理论与思考

这里不只是空洞的理论，更是实打实的架构设计经验与问题解决思路。

+ 探索 RAG 的核心组件与工作流。
+ 深入剖析在 Chunking、Embedding、Retrieval 和 Generation 环节遇到的常见痛点（如语意鸿沟、上下文割裂、检索不精准等）及其解决方案。

### 2️⃣ `articles_demo`：配套的 Demo 代码

Show me the code! 每篇核心文章都配备了独立的、可运行的 Demo。代码保持极简，屏蔽无关框架干扰，方便你快速运行和理解。

### 3️⃣ `project`：从 Demo 到生产级系统

这是本仓库的终极目标。我们将前两个模块中沉淀的知识与技巧，融合并构建成一个完整的、具备 Agent 协同和复杂任务处理能力的系统级项目。

## 🗺️ 内容导航 (Roadmap & Navigation)


| **状态** | **文章 (Articles)**                                               | **核心要点**                                                                                       | **对应 Demo (Code Module)**                              |
| -------- |-----------------------------------------------------------------| -------------------------------------------------------------------------------------------------- |--------------------------------------------------------|
| 🟢       | [01 Native RAG 的标准处理流程](./articles/01 Native RAG 的标准处理流程.md)    | 离线数据建库与在线相似度检索链路                                                                   | [demo-01-naive-rag](./articles_demo/demo-01-naive-rag) |
| 🟡       | [02 Naive RAG 存在的致命问题与解决方案](./articles/02 Naive RAG 存在的致命问题与解决方案.md) | 四大痛点：语意鸿沟、上下文割裂、多级跳转推理与全文总结能力缺失、缺乏纠错闭环                       | [demo-02-critical-issues]()                            |
| 🟡       | [03 工业级 RAG 项目还需要解决哪些问题](./articles/03 工业级 RAG 项目还需要解决哪些问题.md)  | 数据一致性同步、细粒度权限隔离、系统可观测性、自动化评估方案等多方面问题                           | [demo-03-other-issues]()                               |
| 🟡       | [04 RAG的元数据设计](./articles/04 RAG的元数据设计.md)                      | 元数据的三大应用场景，以及利用元数据解决资料冲突的方案设计                                         | [demo-04-metadata-design]()                            |
| ⚪️     | [05 企业级 RAG 项目的架构参考](./articles/05 企业级 RAG 项目的架构参考.md)                       | 六层架构设计，从接入与网关层到最终的监控与测试层的全链路设计方案                                   | [demo-05-system-architecture]()                        |
| ⚪️     | [06 RAG 的全链路耗时与优化](./articles/06 RAG 的全链路耗时与优化.md)              | 全面分析 RAG 的全链路耗时，并针对耗时过高的环节进行优化设计                                        | [demo-06-latency-optimize]()                           |
| ⚪️     | [07 RAG 的评估维度以及标准评估方法](./articles/07 RAG 的评估维度以及标准评估方法.md)      | 基于 RAGAS 的四大评估维度（精确度、召回率、忠实度、相关性），及结合 JUnit 的自动化裁判测试驱动开发 | [demo-07-automated-evaluation]()                       |
| ⚪️     | [08 针对长短不一的文档的双轨制存储方案设计](./articles/08 针对长短不一的文档的双轨制存储方案设计.md)  | 结合全文检索与父子块检索，利用 Metadata 路由实现离线分轨入库与在线统一解析组装                     | [demo-08-dual-track-storage]()                         |

_(注：__🟢__ 已完成 __🟡__ 进行中 __⚪️__ 计划中)_

## 🛠️ 快速开始 (Getting Started)

### 环境要求

* JDK 17+ (推荐 JDK 21)
* Maven 3.8+

### 编译与配置

1. 克隆本项目到本地：

```Bash
git clone https://github.com/Mrchen-1600/Building-RAG-Systems.git
cd Building-RAG-Systems
```

2. 在根目录编译整个项目并安装依赖：

```Bash
mvn clean install -DskipTests
```

3. 环境变量配置：

请在相应模块的 src/main/resources/application.yml 中填入你的大模型 API Key。

### 运行 Demo

进入对应的 demo 目录，通过 Maven 插件运行主类:

```Bash
cd articles_demo/demo-01-naive-rag
mvn exec:java -Dexec.mainClass="com.yourname.rag.demo01.NaiveRagApplication"
```

## 🤝 参与贡献 (Contributing)

欢迎提交 Issue 和 Pull Request！无论是一个拼写错误的修复，还是一个全新 RAG 技巧的分享，都是对这个项目的巨大支持。

## 📄 开源协议 (License)

本项目采用 [MIT License](https://github.com/Mrchen-1600/Building-RAG-Systems/blob/main/LICENSE) 开源协议。
