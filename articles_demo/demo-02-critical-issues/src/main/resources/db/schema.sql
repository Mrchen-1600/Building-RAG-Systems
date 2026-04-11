-- RAG系统数据库表结构设计
-- 创建数据库
CREATE DATABASE IF NOT EXISTS rag_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE rag_system;

-- ============================================
-- 表1: structured_knowledge (结构化知识表)
-- 存储结构化的知识数据，用于Text2SQL查询
-- ============================================
CREATE TABLE IF NOT EXISTS structured_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    knowledge_type VARCHAR(50) NOT NULL COMMENT '知识类型：技术文档、产品信息、政策制度等',
    category VARCHAR(100) NOT NULL COMMENT '分类',
    title VARCHAR(255) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '内容',
    tags VARCHAR(500) COMMENT '标签，逗号分隔',
    source VARCHAR(255) COMMENT '来源',
    priority INT DEFAULT 0 COMMENT '优先级，数值越大优先级越高',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_knowledge_type (knowledge_type),
    INDEX idx_category (category),
    INDEX idx_tags (tags(255)),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结构化知识表';

-- ============================================
-- 表2: user_profile (用户画像表 - 长期记忆)
-- 存储用户画像信息，带版本控制
-- ============================================
CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号，每次更新+1',
    user_role VARCHAR(100) COMMENT '用户角色：开发者、产品经理、管理者等',
    preferences TEXT COMMENT '用户偏好信息，JSON格式存储',
    interests TEXT COMMENT '用户兴趣点，JSON格式存储',
    expertise_level VARCHAR(50) COMMENT '专业水平：初级、中级、高级、专家',
    frequently_asked_topics TEXT COMMENT '常问话题，JSON格式存储',
    learning_style VARCHAR(50) COMMENT '学习风格：实践型、理论型、混合型',
    interaction_pattern VARCHAR(50) COMMENT '交互模式：简洁型、详细型、混合型',
    additional_info TEXT COMMENT '其他附加信息',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否为当前最新版本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_version (user_id, version),
    INDEX idx_user_id (user_id),
    INDEX idx_is_active (user_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表-长期记忆';

-- ============================================
-- 表3: conversation_archive (对话归档表)
-- 记录每次对话的用户提问和大模型回复
-- ============================================
CREATE TABLE IF NOT EXISTS conversation_archive (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    round_num INT NOT NULL COMMENT '对话轮次',
    user_query TEXT NOT NULL COMMENT '用户原始提问',
    rewritten_query TEXT COMMENT '改写后的查询',
    retrieved_context TEXT COMMENT '检索到的上下文',
    ai_response TEXT COMMENT 'AI回复',
    intent_type VARCHAR(50) COMMENT '意图类型',
    retrieval_strategy VARCHAR(50) COMMENT '检索策略',
    used_tools TEXT COMMENT '使用的工具，JSON格式',
    response_time_ms INT COMMENT '响应时间（毫秒）',
    satisfaction_score INT COMMENT '用户满意度评分（1-5）',
    feedback TEXT COMMENT '用户反馈',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_session (user_id, session_id),
    INDEX idx_round_num (session_id, round_num),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话归档表';

-- ============================================
-- 表4: query_rewrite_log (查询改写记录表)
-- 记录用户真实输入和改写后的内容
-- ============================================
CREATE TABLE IF NOT EXISTS query_rewrite_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    original_query TEXT NOT NULL COMMENT '用户原始输入',
    rewritten_query TEXT NOT NULL COMMENT '改写后的内容',
    rewrite_type VARCHAR(50) COMMENT '改写类型：terminology专业术语化、context_completion上下文补全、hybrid混合',
    rewrite_reason TEXT COMMENT '改写原因说明',
    similarity_score DOUBLE COMMENT '原始查询和改写查询的相似度',
    context_used TEXT COMMENT '使用的上下文信息',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_rewrite_type (rewrite_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='查询改写记录表';

-- ============================================
-- 表5: document_metadata (文档元数据表)
-- 记录文档和句子窗口的元数据信息
-- ============================================
CREATE TABLE IF NOT EXISTS document_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    doc_id VARCHAR(64) NOT NULL COMMENT '文档ID',
    doc_name VARCHAR(255) NOT NULL COMMENT '文档名称',
    doc_path VARCHAR(500) COMMENT '文档路径',
    doc_type VARCHAR(50) COMMENT '文档类型：markdown, html, docx, pdf等',
    total_sentences INT COMMENT '文档总句子数',
    total_chunks INT COMMENT '文档总块数',
    content_hash VARCHAR(64) COMMENT '内容哈希，用于去重',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_doc_id (doc_id),
    INDEX idx_doc_type (doc_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档元数据表';

-- 句子元数据表
CREATE TABLE IF NOT EXISTS sentence_metadata (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    doc_id VARCHAR(64) NOT NULL COMMENT '文档ID',
    sentence_index INT NOT NULL COMMENT '句子序号',
    sentence_text TEXT NOT NULL COMMENT '句子内容',
    sentence_vector_id VARCHAR(64) COMMENT '向量库中的ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_doc_sentence (doc_id, sentence_index),
    INDEX idx_doc_id (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='句子元数据表';

-- ============================================
-- 表6: parent_child_chunk (父子块映射表)
-- 存储父子块映射关系
-- ============================================
CREATE TABLE IF NOT EXISTS parent_child_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    doc_id VARCHAR(64) NOT NULL COMMENT '文档ID',
    parent_id VARCHAR(64) NOT NULL COMMENT '父块ID',
    parent_text TEXT NOT NULL COMMENT '父块完整内容',
    child_id VARCHAR(64) NOT NULL COMMENT '子块ID',
    child_text TEXT NOT NULL COMMENT '子块内容',
    child_vector_id VARCHAR(64) COMMENT '向量库中的ID',
    chunk_index INT NOT NULL COMMENT '子块在父块中的序号',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_doc_id (doc_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_child_vector_id (child_vector_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='父子块映射表';

-- ============================================
-- 表7: raptor_tree (RAPTOR树状摘要表)
-- 存储RAPTOR层次化摘要
-- ============================================
CREATE TABLE IF NOT EXISTS raptor_tree (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    doc_id VARCHAR(64) NOT NULL COMMENT '文档ID',
    node_id VARCHAR(64) NOT NULL COMMENT '节点ID',
    level INT NOT NULL COMMENT '层级，0为叶子节点',
    parent_node_id VARCHAR(64) COMMENT '父节点ID',
    node_type VARCHAR(50) NOT NULL COMMENT '节点类型：leaf叶子、cluster聚类、summary摘要、root根节点',
    content TEXT COMMENT '节点内容',
    summary TEXT COMMENT '摘要内容（非叶子节点）',
    cluster_id VARCHAR(64) COMMENT '聚类ID（同一层级的同一聚类有相同ID）',
    child_node_ids TEXT COMMENT '子节点ID列表，JSON格式',
    embedding_vector_id VARCHAR(64) COMMENT '向量库中的ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_node_id (node_id),
    INDEX idx_doc_id (doc_id),
    INDEX idx_level (level),
    INDEX idx_cluster (level, cluster_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAPTOR树状摘要表';

-- ============================================
-- 表8: self_rag_evaluation (Self-RAG评估表)
-- 记录Self-RAG的评估结果
-- ============================================
CREATE TABLE IF NOT EXISTS self_rag_evaluation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT NOT NULL COMMENT '对话归档表ID',
    evaluation_type VARCHAR(50) NOT NULL COMMENT '评估类型：retrieval文档相关性、hallucination幻觉、answer答案有用性',
    evaluation_result VARCHAR(50) NOT NULL COMMENT '评估结果：pass通过、fail失败',
    score DOUBLE COMMENT '评分',
    reason TEXT COMMENT '失败原因',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_evaluation_type (evaluation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Self-RAG评估表';
