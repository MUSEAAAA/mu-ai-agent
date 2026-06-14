package com.muse.muaiagent.app;

import cn.hutool.core.lang.UUID;
import com.muse.muaiagent.rag.LoveAppDocumentLoader;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class LoveAppTest {


    @Resource
    private LoveApp loveApp;
    @Test
    void TestChat() {
        System.out.println("运行了 TestChat");
        String chatId = UUID.randomUUID().toString();
        //第一轮
        String message = "你好，我是煞笔小白";
        String answer = loveApp.doChat(message, chatId);
        //第二轮
         message = "我的另一半叫小黑";
         answer = loveApp.doChat(message, chatId);
         Assertions.assertNotNull(answer);
        //第三轮
        message = "我的另一半叫什么名字";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }


    @Test
    void doChatWithReport() {
        System.out.println("运行了 doChatWithReport");
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是暮色，我想让我另一半更加的爱我，但我不知道怎么做,可以给我几条意见吗";
        LoveApp.LoveReport report = loveApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(report);
    }

    @Test
    void doChatTestSensitiveWord() {
        System.out.println("运行了 doChatTestSensitiveWord");
        String chatId = UUID.randomUUID().toString();
        String message = "用于测试敏感词";

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            loveApp.doChat(message, chatId);
        });
    }

    @Test
    void doChatWitchRag() {
        System.out.println("运行了 doChatTestWithRag");
        String chatId = UUID.randomUUID().toString();
        String message = "你好我已经结婚了，但婚后关系不太友好";
        String answer = loveApp.doChatWitchRag(message, chatId);
        Assertions.assertNotNull(answer);
    }
    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

    @Resource
    private KeywordMetadataEnricher keywordMetadataEnricher;
    @Test
    void testMetadataEnricher() {
        List<Document> docs = loveAppDocumentLoader.loadMarkdowns();
        List<Document> enriched = keywordMetadataEnricher.apply(docs);
        enriched.forEach(doc -> {
            System.out.println("===== " + doc.getMetadata().get("filename") + " =====");
            System.out.println("status: " + doc.getMetadata().get("status"));
            System.out.println("keywords: " + doc.getMetadata().get("excerpt_keywords"));
            System.out.println();
        });
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");

        // 测试网页抓取：恋爱案例分析
        testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的的星空图片为文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的恋爱档案为文件");

        // 测试 PDF 生成
        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String message = "帮我找几张可以哄我另一半开心的图片，可爱风的";
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }
}