package rag;

import com.muse.muaiagent.rag.KnowledgeEconomyDocumentLoader;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class KnowledgeEconomyDocumentLoaderTest {

    @Resource
    private KnowledgeEconomyDocumentLoader knowledgeEconomyDocumentLoader;

    @Test
    void loadMarkdowns() {
        knowledgeEconomyDocumentLoader.loadMarkdowns();
    }
}
