package com.muse.muaiagent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

public class LangChainAiInvoke {

        public static void main(String[] args) {
            ChatLanguageModel qwenModel = QwenChatModel.builder()
                    .apiKey(TestApiKey.API_KEY)
                    .modelName("qwen-max")
                    .build();
            String answer = qwenModel.chat("我是暮色，测试项目");
            System.out.println(answer);
        }
    }


