package com.example.demo02criticalissues.test;

import com.example.demo02criticalissues.chunker.ParentChildChunker;
import com.example.demo02criticalissues.chunker.SentenceWindowChunker;
import com.example.demo02criticalissues.context.ContextWindow;
import com.example.demo02criticalissues.context.ContextWindowBuilder;
import com.example.demo02criticalissues.entity.RaptorTree;
import com.example.demo02criticalissues.repository.RaptorTreeRepository;
import com.example.demo02criticalissues.retriever.IRCoTRetriever;
import com.example.demo02criticalissues.router.LLMIntentRouter;
import com.example.demo02criticalissues.service.*;
import com.example.demo02criticalissues.skill.SkillManager;
import com.example.demo02criticalissues.tools.Text2SqlTool;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ClassName: AdvancedRAGTest
 * Package: com.example.demo02criticalissues.test
 *
 * @Author Mrchen
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvancedRAGTest implements CommandLineRunner {

    private final AdvancedRAGService ragService;
    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private final QueryRewriteService queryRewriteService;
    private final HyDEService hydeService;
    private final SelfRAGService selfRagService;
    private final IRCoTRetriever irCoTRetriever;
    private final RAPTORService raptorService;
    private final ParentChildChunker parentChildChunker;
    private final RaptorTreeRepository raptorTreeRepository;
    private final ShortTermMemoryService shortTermMemoryService;

    private final LLMIntentRouter llmIntentRouter;
    private final Text2SqlTool text2SqlTool;
    private final SkillManager skillManager;


    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n=======================================================");
        System.out.println("      🚀 开启高级 RAG 效果真实演示测试 🚀");
        System.out.println("=======================================================\n");

        // 0. 初始化对比测试专用数据
        initTestData();

        // 1. 对比一：词汇鸿沟与查询重写
        testQueryRewriteComparison();
        Thread.sleep(1000);

        // 2. 对比二：多轮对话代词丢失
        testContextLossComparison();
        Thread.sleep(1000);

        // 3. 对比三：高难度抽象概念召回 (HyDE)
        testHyDEComparison();
        Thread.sleep(1000);

        // 4. 对比四：检索切分导致的上下文割裂 (Chunking)
        testChunkingStrategyComparison();
        Thread.sleep(1000);

        // 5. 对比五：跨文档多步推理 (IRCoT)
        testIRCoTComparison();
        Thread.sleep(1000);

        // 6. 对比六：全局宏观总结 (RAPTOR)
        testRaptorComparison();
        Thread.sleep(1000);

        // 7. 对比七：死板流水线与幻觉拦截 (Self-RAG)
        testSelfRAGComparison();
        Thread.sleep(1000);

        // 8. 对比八：智能体工具调用与懒加载 (Agentic Skill)
        testAgenticSkillComparison();
        Thread.sleep(1000);

        // 9. 长短期记忆压缩
        testMemoryManagement();
        Thread.sleep(1000);

        System.out.println("\n=======================================================");
        System.out.println("              🎉 所有 8 项改进对比演示执行完毕 🎉");
        System.out.println("=======================================================\n");
    }

    private void initTestData() {
        System.out.println(">>> 正在初始化各测试场景专用的基础知识库数据...");

        // 场景 1 & 2：词汇鸿沟专用文本（UI设计 vs 蓝屏死机）
        TextSegment doc1_distract = TextSegment.from("【UI视觉设计规范 v2.0】关于品牌海报的配色方案：当背景版面全变蓝时，为了突出视觉焦点，上面写的所有英文字母都必须是白色的。该规范为强制标准，设计稿一旦定版提交，鼠标悬停效果和图层就固定动不了了，任何人不得随意更改。");
        embeddingStore.add(embeddingModel.embed(doc1_distract).content(), doc1_distract);
        TextSegment doc1_real = TextSegment.from("【IT运维故障排查手册】针对 Windows 操作系统内核崩溃（BSOD，俗称蓝屏死机）的紧急处理流程：当遇到该故障时，请立刻长按主机电源键 10 秒强制关机，并联系 IT 部门重装系统，切勿尝试敲击键盘或移动鼠标。");
        embeddingStore.add(embeddingModel.embed(doc1_real).content(), doc1_real);

        // ================= 场景 3：HyDE（客服话术 vs MQ死信底层原理） =================
        TextSegment doc2_distract = TextSegment.from("【客服话术规范】如果VIP客户投诉今天上午没有收到营销短信，请首先安抚客户情绪，并统一向客户解释说：这是因为后台短信通道的运营商出现了短暂的网络拥堵，目前网络通道已经完全恢复正常。");
        embeddingStore.add(embeddingModel.embed(doc2_distract).content(), doc2_distract);
        TextSegment doc2_real = TextSegment.from("【消息中间件预警底稿】RabbitMQ 集群中的 'marketing_queue' 队列存在死信堆积问题。当单条消息的 payload 体积超过 1MB 时，会被直接路由到 Dead Letter Exchange (DLX)，导致下游消费者永远无法拉取到该条消息。");
        embeddingStore.add(embeddingModel.embed(doc2_real).content(), doc2_real);

        // 场景 4：Chunking 切分割裂测试文本（被强行切碎的连贯通告）
        String fullDocument = "【文档：2024公司组织架构调整通告】2024年初，为了应对日益激烈的AI市场竞争，集团董事会决议进行大规模的组织架构优化。其中最引人注目的动作，是将原有的“AI创新事业部”分拆为大模型底层架构组、智能体研发组和AI应用落地组三个独立部门。由于这次分拆，原本负责RAG研发的核心骨干李工，因为具备深厚的分布式系统开发经验，被强制调去了大模型底层架构组参与基座模型的训练攻坚。这一调动虽然引起了短期的人事震荡，但长期来看有利于公司核心竞争力的提升。";
        Document doc = Document.from(fullDocument);
        ParentChildChunker.ParentChildChunkResult pcResult = parentChildChunker.chunk(doc, "doc_org_change_001");
        // 将被切得七零八落的子句存入向量库，作为 Naive 检索的陷阱
        for (TextSegment childSegment : pcResult.childSegments()) {
            embeddingStore.add(embeddingModel.embed(childSegment).content(), childSegment);
        }

        // 场景 5：IRCoT 多跳专用文本 (虚构的科幻背景，强迫 LLM 不能用脑子里的常识作弊)
        TextSegment doc3 = TextSegment.from("【机密档案：代号天狼星】2025年启动的'天狼星'觉醒计划的首席核心架构师是一位化名为'影刃'的神秘黑客，他负责了整个系统底层的逻辑重构。");
        embeddingStore.add(embeddingModel.embed(doc3).content(), doc3);
        TextSegment doc4 = TextSegment.from("【人事通告档案】经背景调查查实，一直活跃在暗网、化名为'影刃'的神秘黑客，其真实身份是原'量子科技财团'的首席安全官李明宇。");
        embeddingStore.add(embeddingModel.embed(doc4).content(), doc4);
        TextSegment doc5_ircot = TextSegment.from("【全球企业商用信息登记】'量子科技财团'是一家专注于前沿科技投资的神秘组织，其全球总部设立于风景秀丽的瑞士日内瓦湖畔。");
        embeddingStore.add(embeddingModel.embed(doc5_ircot).content(), doc5_ircot);

        // 场景 6：RAPTOR 宏观总结专用文本（顶层聚类根节点）
        if (raptorTreeRepository.count() == 0) {
            RaptorTree root = new RaptorTree();
            root.setDocId("doc_raptor_001");
            root.setNodeId("root_node");
            root.setLevel(3);
            root.setNodeType("root");
            root.setSummary("【全局摘要】《2024 AI 行业应用落地深度研报》核心指出：今年是大模型从技术探索走向商业落地的元年。报告从三个维度进行了剖析：首先，RAG 技术通过外挂知识库彻底解决了大模型的幻觉与企业数据孤岛问题；其次，Agent 智能体赋予了 AI 系统使用工具和自主规划的执行力；最后，算力成本的下降使得中小企业也能负担起专属模型的微调。综上，RAG 与 Agent 结合是当前企业降本增效的最优解。");
            raptorTreeRepository.save(root);
        }

        // 场景 7：Self-RAG 幻觉诱导专用文本
        TextSegment doc5 = TextSegment.from("【文档：后勤部年度报告】由于采用了新型航天材料学的副产物，去年我们公司食堂的锅铲变得更加坚固耐用了，这一改进使得食堂全年的净利润出人意料地上升了近十个百分点。");
        embeddingStore.add(embeddingModel.embed(doc5).content(), doc5);

        System.out.println(">>> 测试数据装载完毕，即将开启对比...\n");
    }

    private void testQueryRewriteComparison() {
        System.out.println("\n【对比 一】Query Mismatch 词汇鸿沟 (口语表达 vs 视觉设计规范)");
        String naiveQuery = "我刚用着电脑，突然屏幕全变蓝了，鼠标也动不了，该咋办？";
        System.out.println("-> 用户口语化提问: \"" + naiveQuery + "\"");

        System.out.println("\n❌ Naive RAG 回答 (踩中 UI 设计规范陷阱):");
        System.out.println("   [致命缺陷] 朴素 RAG 被“变蓝、白色的英文字母、动不了”等字眼吸住，找出了视觉设计规范...");
        String naiveResult = ragService.processNaiveRAG(naiveQuery);
        System.out.println("🤖 LLM 答: " + naiveResult.replace("\n", ""));

        System.out.println("\n✅ Advanced RAG 回答 (Query Rewrite):");
        QueryRewriteService.RewriteResult rewriteResult = queryRewriteService.terminologyRewrite(naiveQuery, "test_user");
        System.out.println("   [底层重写动作] LLM 凭借知识库外的常识，将其翻译为专业术语: \"" + rewriteResult.rewrittenQuery() + "\"");

        // 解剖式调用，使用重写后的 Query 去检索，命中 IT 运维手册
        List<TextSegment> docs = ragService.retrieveDocuments(rewriteResult.rewrittenQuery());
        ContextWindow context = new ContextWindowBuilder().input(naiveQuery).build();
        String advancedResult = ragService.generateAnswer(naiveQuery, docs, context);

        System.out.println("🤖 LLM 答: " + advancedResult.replace("\n", ""));
        System.out.println("-------------------------------------------------------");
    }

    private void testContextLossComparison() {
        System.out.println("\n【对比 二】多轮对话代词指代丢失");
        System.out.println("-> 场景：上一轮用户刚问过电脑蓝屏死机的解决办法...");
        String shortQuery = "那我一直按着那个按键重启的话，会把里面的资料弄丢吗？";
        System.out.println("-> 本轮用户继续追问: \"" + shortQuery + "\"");

        System.out.println("\n❌ Naive RAG 回答 (无记忆孤立检索导致大模型发懵):");
        String naiveResult = ragService.processNaiveRAG(shortQuery);
        System.out.println("🤖 LLM 答: " + naiveResult.replace("\n", ""));

        System.out.println("\n✅ Advanced RAG 回答 (ContextWindow 记忆补全):");
        ContextWindow memoryContext = new ContextWindow();
        memoryContext.updateShortTermMemory("电脑屏幕变蓝了咋办", "请立刻长按主机电源键 10 秒强制关机。");
        QueryRewriteService.RewriteResult ctxResult = queryRewriteService.contextCompletionRewrite(shortQuery, memoryContext, "test_user");
        System.out.println("   [底层补全动作] 结合记忆补全指代内容后: \"" + ctxResult.rewrittenQuery() + "\"");

        // 解剖式调用
        List<TextSegment> docs = ragService.retrieveDocuments(ctxResult.rewrittenQuery());
        String advancedResult = ragService.generateAnswer(shortQuery, docs, memoryContext);
        System.out.println("🤖 LLM 答: " + advancedResult.replace("\n", ""));
        System.out.println("-------------------------------------------------------");
    }

    private void testHyDEComparison() {
        System.out.println("\n【对比 三】高难度抽象概念召回 (业务表象病症 -> 底层技术病因 跨维映射)");
        String hardQuery = "为什么今天上午我们针对VIP客户发送的营销活动短信，他们都没有收到？后台看短信通道的状态明明是完全正常的啊。";
        System.out.println("纯业务症状提问 (全篇毫无技术词汇): \"" + hardQuery + "\"");

        System.out.println("\n❌ Naive RAG 回答 (命中客服话术，被带偏):");
        System.out.println("   [致命缺陷] 朴素 RAG 被'VIP、营销短信、通道正常'吸引，找出了客服手册...");
        String naiveResult = ragService.processNaiveRAG(hardQuery);
        System.out.println("🤖 LLM 答: " + naiveResult.replace("\n", ""));

        System.out.println("\n✅ Advanced RAG 回答 (触发 HyDE 机制):");
        HyDEService.HyDEResult hydeResult = hydeService.executeHyDE(hardQuery, 3);
        System.out.println("   [动作 1] LLM 扮演资深架构师，先盲猜技术病因，生成的假技术底稿:\n    \"" + hydeResult.hypotheticalDocument().substring(0, Math.min(100, hydeResult.hypotheticalDocument().length())) + "...\"");
        System.out.println("   [动作 2] 这份假底稿自带极强的技术向量(如消息队列、网关限流等)，钓出了真正的 RabbitMQ 运维预警文档: \n    \"" + (hydeResult.retrievedSegments().isEmpty() ? "空" : hydeResult.retrievedSegments().get(0).text().substring(0, Math.min(80, hydeResult.retrievedSegments().get(0).text().length())) + "...\""));

        // 解剖式调用
        ContextWindow context = new ContextWindowBuilder().input(hardQuery).build();
        String advancedResult = ragService.generateAnswer(hardQuery, hydeResult.retrievedSegments(), context);
        System.out.println("🤖 LLM 答: " + advancedResult.replace("\n", ""));
        System.out.println("-------------------------------------------------------");
    }

    private void testChunkingStrategyComparison() {
        System.out.println("\n【对比 四】Chunking 切分导致的上下文割裂");
        String query = "请详细说明原本负责 RAG 的李工被强制调岗的背景原因是什么？";
        System.out.println("提问: \"" + query + "\"");

        System.out.println("\n❌ 朴素切分 (导致大模型丢失了前面董事会决议的前因后果):");
        List<TextSegment> naiveDocs = ragService.retrieveDocuments(query);
        String naiveContext = naiveDocs.isEmpty() ? "无资料" : naiveDocs.get(0).text();
        System.out.println("   [检索命中的单薄短句]：\"" + naiveContext + "\"");
        String prompt1 = "请基于以下参考资料回答，不要脑补。\n资料：" + naiveContext + "\n问题：" + query;
        System.out.println("🤖 LLM 答: " + chatModel.generate(prompt1).replace("\n", ""));

        System.out.println("\n✅ 父子块机制 (Parent-Child) 自动回溯还原真相:");
        List<String> parents = parentChildChunker.getParentsFromSegments(naiveDocs);
        String goodContext = parents.isEmpty() ? naiveContext : parents.get(0);
        System.out.println("   [系统底层通过子块关联查出的完整通告段落]：\n    \"" + goodContext.substring(0, Math.min(90, goodContext.length())) + "...\"");
        String prompt2 = "请基于以下参考资料回答。\n资料：" + goodContext + "\n问题：" + query;
        System.out.println("🤖 LLM 答: " + chatModel.generate(prompt2).replace("\n", ""));
        System.out.println("-------------------------------------------------------");
    }

    private void testIRCoTComparison() {
        System.out.println("\n【对比 五】跨文档多跳推理 (IRCoT 交错检索)");
        // 这是一个虚构的提问，LLM 脑子里没有任何先验知识，只能依赖检索系统去扒资料
        String multiHopQuery = "2025年'天狼星'觉醒计划的首席核心架构师，他以前老东家的全球总部设在哪里？";
        System.out.println("提问 (只有线索 A，要经过 B 才能找到答案 C): \"" + multiHopQuery + "\"");

        System.out.println("\n❌ Naive RAG 回答 (只能傻搜一次，资料必定拼不齐):");
        String naiveResult = ragService.processNaiveRAG(multiHopQuery);
        System.out.println("🤖 LLM 答: " + naiveResult.replace("\n", ""));

        System.out.println("\n✅ Advanced RAG 回答 (触发 IRCoT 迭代检索智能体进行抽丝剥茧):");
        IRCoTRetriever.IRCoTResult ircotResult = irCoTRetriever.retrieve(multiHopQuery);
        for (IRCoTRetriever.RetrievalStep step : ircotResult.steps()) {
            System.out.println("   [Agent 深度挖掘 - 第 " + step.iteration() + " 层]");
            System.out.println("     - Thought (思考推演): " + step.reasoning());
            System.out.println("     - Action (动态生成搜索词): " + step.query());
        }
        System.out.println("🤖 LLM 闭环推演最终答案: " + ircotResult.answer().replace("\n", ""));
        System.out.println("-------------------------------------------------------");
    }

    private void testRaptorComparison() {
        System.out.println("\n【对比 六】全局宏观总结痛点 (RAPTOR树状摘要)");
        String summaryQuery = "全面总结一下这份100页的《2024 AI 行业应用落地深度研报》讲了哪些核心维度？";
        System.out.println("宏观提问: \"" + summaryQuery + "\"");

        System.out.println("\n❌ Naive RAG 回答 (只能摸到零碎细节，只见树木不见森林):");
        String naiveResult = ragService.processNaiveRAG(summaryQuery);
        System.out.println("🤖 LLM 答: " + naiveResult.replace("\n", ""));

        System.out.println("\n✅ Advanced RAG 回答 (直击 RAPTOR 预生成的 Root 层顶层大纲):");
        // 解剖式调用，直接利用 RAPTOR 获取顶层摘要
        RAPTORService.RAPTORResult raptorResult = raptorService.retrieve("doc_raptor_001", summaryQuery);
        ContextWindow context = new ContextWindowBuilder().input(summaryQuery).build();
        context.setRetrievedContext("【宏观摘要库】\n" + (raptorResult.summaries().isEmpty() ? "" : raptorResult.summaries().get(0)));
        System.out.println("🤖 LLM 答: " + chatModel.generate(context.buildPrompt()).replace("\n", ""));
        System.out.println("-------------------------------------------------------");
    }

    private void testSelfRAGComparison() {
        System.out.println("\n【对比 七】僵化流水线与大模型幻觉拦截 (Self-RAG)");
        String fakeQuery = "公司去年猎鹰火箭发射任务赚了多少利润？";
        System.out.println("无中生有的提问: \"" + fakeQuery + "\"");

        System.out.println("\n✅ Self-RAG 大模型裁判系统紧急熔断展示:");
        // 故意喂给它“后勤食堂利润上升”的不相干资料，并模拟生成器犯错产生“十亿利润”的幻觉
        SelfRAGService.SelfRAGResult response = selfRagService.execute(
                fakeQuery,
                q -> ragService.retrieveDocuments("食堂锅铲利润"),
                (q, docs) -> "根据参考资料，公司去年通过猎鹰火箭发射任务，赚了大约十个亿的利润。"
        );

        System.out.println("   [警报！生成器产生了幻觉，评判团介入]：");
        for (SelfRAGService.EvaluationResult eval : response.evaluations()) {
            System.out.println("    -> 裁判 [" + eval.type().getDisplayName() + "] 亮牌: " + (eval.passed() ? "✅ PASS" : "❌ FAIL") + " | 理由: " + eval.reasoning().replace("\n", ""));
        }
        System.out.println("\n🤖 系统已被 SelfRAG 拦截阻断，最终输出降级回应: " + response.answer().replace("\n", ""));
        System.out.println("-------------------------------------------------------");
    }

    private void testAgenticSkillComparison() {
        System.out.println("\n【对比 八】Agentic Skill 渐进式加载与动态工具调用 (Text2SQL)");
        String sqlQuery = "帮我查一下底层的关系型数据库，目前系统里一共有多少个注册用户的画像记录？";
        System.out.println("-> 跨界提问 (要求跨界操作底层的结构化关系型数据库): \"" + sqlQuery + "\"");

        System.out.println("\n❌ Naive RAG 真实回答 (拿着结构化问题去纯文本向量库里查询):");
        String naiveResult = ragService.processNaiveRAG(sqlQuery);
        System.out.println("🤖 LLM 答: " + naiveResult.replace("\n", ""));

        System.out.println("\n✅ Advanced RAG 真实回答 (触发意图路由与 Skill 懒加载):");
        LLMIntentRouter.RouteResult routeResult = llmIntentRouter.route(sqlQuery);
        System.out.println("   [动作 1 - 意图网关] LLM 敏锐地识别出该问题不能搜向量库，将其拦截并路由至专有技能: [" + routeResult.intentType() + "]");

        if (routeResult.intentType() == LLMIntentRouter.IntentType.STRUCTURED_QUERY) {
            skillManager.clearCache("text2sql");
            System.out.println("   [动作 2 - 渐进式披露] 系统从冷启动状态，开始从磁盘动态加载极其庞大的 `skills.md` (含全库表结构) 组装至内存...");
            System.out.println("                        (日常对话时不加载，此举极大节省了常规 Token 开销并防止了 Attention 分散)");

            Text2SqlTool.Text2SqlResult sqlResult = text2SqlTool.execute(routeResult.rewrittenQuery());

            // 【Null 安全检查
            if (sqlResult.success()) {
                System.out.println("   [动作 3 - 智能体执行] LLM 成功将自然语言转化为 SQL 并通过 JDBC 物理执行:");
                System.out.println("      -> 提取的底层动作: " + sqlResult.sql());
                System.out.println("      -> 数据库物理返回: " + (sqlResult.results() != null ? sqlResult.results().toString() : "空"));
            } else {
                System.out.println("   [动作 3 - 智能体执行失败] " + sqlResult.message());
            }

            ContextWindow context = new ContextWindowBuilder().input(sqlQuery).build();
            context.setRetrievedContext("【底层动作执行结果】\n" + sqlResult.formatResultText() + "\n请用自然、专业的语言向用户汇报结果。");
            String finalAnswer = chatModel.generate(context.buildPrompt());
            System.out.println("\n🤖 LLM 最终转换汇报: " + finalAnswer.replace("\n", ""));
        } else {
            System.out.println("⚠️ 路由未命中预期");
        }
        System.out.println("-------------------------------------------------------");
    }

    private void testMemoryManagement() throws InterruptedException {
        System.out.println("\n【内部演示】系统记忆滚动压缩 (Memory Management)");
        String userId = "mem_user_99";

        System.out.println("-> 连续注入高频对话（直接调用底层记忆接口，避开不相关的知识检索与拦截）...");
        ContextWindow context = new ContextWindowBuilder().userId(userId).build();

        shortTermMemoryService.updateShortTermMemory(context, "Spring Boot 怎么配 Redis?", "在 application.yml 中配置 spring.redis.host 即可。");
        System.out.println("   [第1轮结束] 当前记忆队列驻留条数: " + context.getMemory().getShortTerm().getHistory().size());
        Thread.sleep(1000);

        shortTermMemoryService.updateShortTermMemory(context, "怎么做缓存预热呢？", "可以通过实现 ApplicationRunner 接口，在项目启动时将数据主动刷入 Redis。");
        System.out.println("   [第2轮结束] 当前记忆队列驻留条数: " + context.getMemory().getShortTerm().getHistory().size());
        Thread.sleep(1000);

        shortTermMemoryService.updateShortTermMemory(context, "如果 Redis 集群全宕机了怎么办？", "建议立刻开启熔断限流，同时允许短暂降级穿透读取本地 Caffeine 缓存。");
        System.out.println("   [第3轮结束] 当前记忆队列驻留条数: " + context.getMemory().getShortTerm().getHistory().size());
        Thread.sleep(1000);

        System.out.println("-> 即将注入第四轮，突破设定的最大保留阈值 3，迫使大模型启动后台无损压缩...");
        shortTermMemoryService.updateShortTermMemory(context, "原来如此，那 RAG 系统里的向量库也能用 Redis 替代吗？", "可以的，Redis 提供了强大的 RediSearch 模块，完美支持高维向量检索。");

        System.out.println("\n✅ 检查内存自动清理情况与数据库落地情况：");
        System.out.println("   由于触发内存阈值并完成压缩转换，当前庞大的长对话数组已被清空。剩余队列条数： " + context.getMemory().getShortTerm().getHistory().size());
        System.out.println("✅ 大模型真实融合生成的【滚动无损摘要】(极大地节省了后续聊天的 Token 开销) 如下：");
        System.out.println("   " + context.getMemory().getShortTerm().getSummary());
        System.out.println("-------------------------------------------------------");
    }
}