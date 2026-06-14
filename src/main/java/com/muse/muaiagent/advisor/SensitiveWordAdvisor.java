package com.muse.muaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.*;
import reactor.core.publisher.Flux;

@Slf4j
public class SensitiveWordAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private AdvisedRequest before(AdvisedRequest advisedRequest) {
        String Text = advisedRequest.userText();
        if (Text.contains("敏感词")) {
            log.info("有敏感词汇");
            throw new IllegalArgumentException("输入内容包含敏感词，请修改后再试。");
        }else{
            return advisedRequest;
        }

    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        return chain.nextAroundCall(this.before(advisedRequest));

    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        return chain.nextAroundStream(this.before(advisedRequest));
    }

    @Override
    public String getName() {
        return "敏感词拦截器";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
