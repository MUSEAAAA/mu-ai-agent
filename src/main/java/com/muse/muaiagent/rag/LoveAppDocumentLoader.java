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
/***
 * 加载多篇MarkDown 文档
 * @return
 */
public class LoveAppDocumentLoader {
    //这是一个资源解析类
    private final ResourcePatternResolver resourcePatternResolver;
    public LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
       this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try{
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                String nameWithoutExt = fileName.substring(0, fileName.length() - 3);
                int start = nameWithoutExt.indexOf(" - ") + 3;  // " - " 后面第一个字
                int end = nameWithoutExt.indexOf("篇");           // "篇" 的位置
                String status = nameWithoutExt.substring(start, end);
                MarkdownDocumentReaderConfig config =MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        //加入名字元信息
                        //这里可以选择怎么切分文档
                        .withAdditionalMetadata("filename", fileName)
                        .withAdditionalMetadata("status", status)
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(reader.get());
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }


}

//@Component
//@Slf4j
//class LoveAppDocumentLoader {
//
//    private final ResourcePatternResolver resourcePatternResolver;
//
//    LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
//        this.resourcePatternResolver = resourcePatternResolver;
//    }
//
//    public List<Document> loadMarkdowns() {
//        List<Document> allDocuments = new ArrayList<>();
//        try {
//            // 这里可以修改为你要加载的多个 Markdown 文件的路径模式
//            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
//            for (Resource resource : resources) {
//                String fileName = resource.getFilename();
//                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
//                        .withHorizontalRuleCreateDocument(true)
//                        .withIncludeCodeBlock(false)
//                        .withIncludeBlockquote(false)
//                        .withAdditionalMetadata("filename", fileName)
//                        .build();
//                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
//                allDocuments.addAll(reader.get());
//            }
//        } catch (IOException e) {
//            log.error("Markdown 文档加载失败", e);
//        }
//        return allDocuments;
//    }
//}

