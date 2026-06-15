package com.muse.muaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class KnowledgeEconomyDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public KnowledgeEconomyDocumentLoader() {
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            log.info("找到 {} 个 markdown 文件", resources.length);
            for (Resource resource : resources) {
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("status", "基础")
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                List<Document> docs = reader.get();
                allDocuments.addAll(docs);
                log.info("已加载文件, 包含 {} 个章节", docs.size());
            }
        } catch (IOException e) {
            log.error("Markdown document loading failed", e);
        }
        log.info("共加载 {} 个文档", allDocuments.size());
        return allDocuments;
    }
}
