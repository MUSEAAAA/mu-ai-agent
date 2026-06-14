package com.muse.muaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class PgVectorVectorsStoreConfigTest {


    @Resource
    private VectorStore pgVectorVectorStore;
    @Test
    void pgVectorVectorStore() {
        List<Document> documents = List.of(
                new Document("暮色好帅，我凑暮色真的好帅，真的好帅", Map.of("meta1", "meta1")),
                new Document("暮色人很好啊非常好啊，因为很帅"),
                new Document("小白是狗，暮色说的.", Map.of("meta2", "meta2")));
        // 添加文档
        pgVectorVectorStore.add(documents);
        // 相似度查询
        List<Document> results = pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("很帅").topK(3).build());
        Assertions.assertNotNull(results);

    }
}

