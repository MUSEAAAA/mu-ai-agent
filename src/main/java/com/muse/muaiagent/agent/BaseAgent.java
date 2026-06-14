package com.muse.muaiagent.agent;

import com.muse.muaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 *
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {
    //Agent 名字
    private String name;
    //Agent 描述
    private String description;

    //提示词
    private String systemPrompt;
    private String nextStepPrompt;

    //记忆
    private List<Message> messageList = new ArrayList<>();

    //执行控制
    private Integer currentStep = 0;
    private Integer maxSteps =10;

    //状态使用枚举
    private AgentState state = AgentState.IDLE;

    //LLM
    private ChatClient chatClient;

    public String run (String userPrompt) {
        if (state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent " + name + " because state is " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent " + name + " because user prompt is empty");
        }
        //更改状态
        state = AgentState.RUNNING;
        //记录上下文
        messageList.add(new UserMessage(userPrompt));
        // 用于保存结果到列表中
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("step number: " + stepNumber + "MaxSteps: " + maxSteps);
                String stepResult = step();
                String result = "Step" + stepNumber + ": " + stepResult;
                log.info("step result: " + result);
                results.add(result);

            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps: (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误: " + e.getMessage();
        } finally {
            this.cleanUp();
        }
    }

    /**
     * 定义单个步骤
     * @return
     */
    public abstract String step();

    protected void cleanUp() {}

    public SseEmitter runStream (String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            //使用线程异步处理，避免阻塞输出

            try {
                if (state != AgentState.IDLE) {
                    sseEmitter.send("错误，当前状态无法运行代理"+this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StrUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误，空提示词无法运行代理");
                    sseEmitter.complete();
                }
            } catch (IOException e) {
                sseEmitter.completeWithError(e);
            }
            //更改状态
            state = AgentState.RUNNING;
            //记录上下文
            messageList.add(new UserMessage(userPrompt));
            // 用于保存结果到列表中
            List<String> results = new ArrayList<>();
            try {
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("step number: " + stepNumber + "MaxSteps: " + maxSteps);
                    String stepResult = step();
                    String result = "Step" + stepNumber + ": " + stepResult;
                    log.info("step result: " + result);
                    results.add(result);
                    //输出当前每一步的结果给SSE
                    sseEmitter.send("Step" + stepNumber + ": " + stepResult );

                }
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps: (" + maxSteps + ")");
                    sseEmitter.send("Terminated: Reached max steps(到达最大步骤): (" + maxSteps + ")");

                }
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("Error executing agent", e);
                try {
                    sseEmitter.send("执行错误"+e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanUp();
            }
        });


        //设置超时回调
        sseEmitter.onTimeout(()-> {
            this.state = AgentState.ERROR;
            this.cleanUp();
            log.warn("SSE connection timeout");
        });

        sseEmitter.onCompletion(()->{
            if (state == AgentState.RUNNING) {
                state = AgentState.FINISHED;
            }
            this.cleanUp();
            log.info("SSE connection terminated");
        });

        return sseEmitter;


    }

}

