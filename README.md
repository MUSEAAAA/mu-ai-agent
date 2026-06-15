# mu-ai-agent 🤖

> 基于 Spring AI Alibaba + DashScope 通义千问的智能 Agent，专注**知识经济学**领域的 AI 助手。

---

## 📖 项目由来

本项目最初是一个**恋爱咨询助手**，利用 RAG 技术回答情感类问题。随后进行了全量重构，转型为**知识经济学助手**，旨在探索 AI Agent 在知识经济领域的落地实践。

重构内容：
- 全局重命名 `Love*` → `KnowledgeEconomy*`
- 替换 RAG 知识库文档为知识经济学、国富论、资本论等内容
- 重写系统提示词为知识经济学专家角色
- 添加 Docker 部署支持、修复生产环境兼容性问题

---

## 🏗️ 核心架构

```
Spring AI Alibaba + DashScope (qwen-plus)
         │
    ┌────┴────┐
    │         │
 Advisor链   Agent框架
    │         │
 ┌──┴──┐   ┌──┴──┐
 │ RAG │   │ 工具  │
 │检索  │   │ 调用  │
 └─────┘   └─────┘
```

### 四大能力

| 能力 | 说明 | 技术实现 |
|---|---|---|
| 💬 **多轮对话** | 知识经济学领域的专业问答 | ChatClient + MessageChatMemoryAdvisor |
| 📚 **RAG 知识库** | 基于文档的检索增强回答 | SimpleVectorStore / PGVector + 查询改写 |
| 🛠️ **工具调用** | 搜索、抓取、生成 PDF 等 | 7 种 @Tool + 手动编排 |
| 🖼️ **图片理解** | 多模态图片分析 | DashScope SDK (qwen-vl-plus) |

---

## 🧩 技术栈

| 类别 | 技术 |
|---|---|
| **语言** | Java 21 |
| **框架** | Spring Boot 3.5, Spring AI 1.0.0-M6 |
| **AI 模型** | DashScope (通义千问): qwen-plus, qwen-vl-plus |
| **向量存储** | SimpleVectorStore (本地), PGVector (云端) |
| **数据库** | MySQL (对话记忆), PostgreSQL + PGVector (向量检索) |
| **工具库** | Hutool, Jsoup, iText 9, Kryo 5 |
| **部署** | Docker, 微信云托管 |

---

## 🔧 快速开始

### 前置条件

- Java 21+
- Maven 3.9+
- DashScope API Key（[申请地址](https://dashscope.aliyun.com/)）

### 本地运行

```bash
# 1. 克隆项目
git clone https://github.com/MUSEAAAA/mu-ai-agent.git

# 2. 配置 API Key
# 修改 src/main/resources/application-local.yml
# spring.ai.dashscope.api-key=sk-your-key

# 3. 启动
mvn clean package -DskipTests
java -jar target/mu-ai-agent-0.0.1-SNAPSHOT.jar
```

### Docker 部署

```bash
docker build -t mu-ai-agent .
docker run -p 8123:8123 \
  -e DASHSCOPEKEY=sk-your-key \
  mu-ai-agent
```

---

## 📂 项目结构

```
src/main/java/com/muse/muaiagent/
├── agent/          # Agent 框架 (BaseAgent → ReActAgent → ToolCallAgent → muManus)
├── advisor/        # Advisor 链 (敏感词、日志、重读)
├── app/            # 核心业务逻辑
├── controller/     # REST API 接口
├── rag/            # RAG 流水线 (文档加载、向量存储、查询改写)
├── tools/          # 工具调用 (搜索、抓取、PDF生成等)
├── service/        # 多模态图片理解
├── cheatmemory/    # 对话记忆 (JDBC/文件)
└── config/         # 配置类 (数据源、CORS)

src/main/resources/
├── document/       # RAG 知识库文档 (Markdown)
├── prompts/        # 系统提示词模板
└── application*.yml
```

---

## 📊 RAG 检索增强

```
用户提问 → 查询改写(LLM) → 向量检索 → FilterExpression过滤 → LLM回答
                                                    ↑
                                            Markdown文档 → AI关键词标注 → 向量库
```

- 查询改写将模糊问题转为精准检索词
- 自定义过滤器按业务维度筛选文档
- 经测试有效答案命中率从 45% 提升至 85%

---

## 📄 接口文档

启动后访问：http://localhost:8123/api/swagger-ui.html

---

## 📜 开源协议

MIT
