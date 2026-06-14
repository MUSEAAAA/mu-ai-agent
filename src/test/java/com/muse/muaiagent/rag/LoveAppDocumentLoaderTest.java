package com.muse.muaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class LoveAppDocumentLoaderTest {

    @Resource
    private LoveAppDocumentLoader loader;
    @Resource
    private VectorStore loveAppVectorStore;
    @Resource
    private QueryRewriter queryRewriter;
    @Test
    void loadMarkdowns() {
        loader.loadMarkdowns();
    }

    @Test
    void loadMarkdownsWithCategory() {
        List<Document> docs = loader.loadMarkdowns();
        docs.forEach(doc ->
                System.out.println(doc.getMetadata().get("category") + " | " + doc.getText().substring(0, 20))
        );
    }

    @Test
    void testFilterByCategory() {
        // 1. 不带过滤，查全部
        List<Document> all = loveAppVectorStore.similaritySearch(
                SearchRequest.builder().query("怎么追人").topK(10).build()
        );
        System.out.println("无过滤: " + all.size() + " 条");

        // 2. 只查 "单身" 分类
        List<Document> single = loveAppVectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("怎么追人")
                        .topK(10)
                        .filterExpression(new FilterExpressionBuilder().eq("status", "单身").build())
                        .build()
        );
        System.out.println("单身过滤: " + single.size() + " 条");
        single.forEach(doc ->
                System.out.println("  " + doc.getMetadata().get("status") + " | " + doc.getText().substring(0, 30))
        );

        // 3. 断言：单身过滤的结果应该全部是 "单身"
        assertTrue(single.stream().allMatch(
                doc -> "单身".equals(doc.getMetadata().get("status"))
        ));

    }
    @Test
    void testFilterByCategory2() {
        List<Document> all = loveAppVectorStore.similaritySearch(
                SearchRequest.builder().query("怎么追人").topK(10).build()
        );
        System.out.println("无过滤: " + all.size() + " 条");
        all.forEach(doc ->
                System.out.println("metadata: " + doc.getMetadata() + " | 内容: " + doc.getText().substring(0, 20))
        );
    }
    @Test
    void testQueryRewrite() {
        String result = queryRewriter.doQueryRewrite("这个怎么办");
        System.out.println("改写前：这个怎么办");
        System.out.println("改写后：" + result);
    }

}