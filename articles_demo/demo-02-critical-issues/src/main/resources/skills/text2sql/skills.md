# Text2SQL Skill

## 简介

Text2SQL Skill是一个将自然语言问题转换为SQL查询的工具。它能够理解用户的自然语言提问，并根据数据库结构生成相应的SQL语句，然后执行查询并返回结果。

## 使用场景

当用户的提问涉及到以下情况时，适合使用Text2SQL Skill：
- 查询结构化数据（如统计数字、具体记录）
- 查询数据库中的特定信息
- 需要精确的数据查询结果

## 数据库结构

当前可查询的数据库为 `rag_system`，包含以下表：

### 1. structured_knowledge (结构化知识表)
- `id` (BIGINT) - 主键ID
- `knowledge_type` (VARCHAR) - 知识类型
- `category` (VARCHAR) - 分类
- `title` (VARCHAR) - 标题
- `content` (TEXT) - 内容
- `tags` (VARCHAR) - 标签
- `source` (VARCHAR) - 来源
- `priority` (INT) - 优先级
- `is_active` (BOOLEAN) - 是否启用

### 2. user_profile (用户画像表)
- `id` (BIGINT) - 主键ID
- `user_id` (VARCHAR) - 用户ID
- `version` (INT) - 版本号
- `user_role` (VARCHAR) - 用户角色
- `expertise_level` (VARCHAR) - 专业水平
- `learning_style` (VARCHAR) - 学习风格
- `is_active` (BOOLEAN) - 是否激活

### 3. conversation_archive (对话归档表)
- `id` (BIGINT) - 主键ID
- `user_id` (VARCHAR) - 用户ID
- `session_id` (VARCHAR) - 会话ID
- `round_num` (INT) - 轮次号
- `user_query` (TEXT) - 用户提问
- `ai_response` (TEXT) - AI回复
- `intent_type` (VARCHAR) - 意图类型
- `created_at` (TIMESTAMP) - 创建时间

## 使用方法

通过自然语言描述你想要查询的内容，例如：
- "查询所有的RAG技术文档"
- "统计有多少篇技术文档"
- "查找用户角色为开发者的用户画像"
- "查询最近10条对话记录"

## 输出格式

执行SQL查询后，返回的结果将按照以下格式呈现：
- 表格形式展示查询结果
- 如果查询结果为空，会明确提示
- 对于复杂的查询结果，会提供简要说明

## 注意事项

1. SQL查询会自动进行安全检查，防止SQL注入
2. 查询结果会根据用户画像进行适当的简化或详细化
3. 如果自然语言无法准确转换为SQL，会要求用户重新表述
4. 查询结果只返回前100条记录，避免返回过多数据
