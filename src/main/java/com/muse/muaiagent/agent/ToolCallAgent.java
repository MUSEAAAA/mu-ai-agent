package com.muse.muaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.muse.muaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper=true)
public class ToolCallAgent extends ReActAgent {

    //可用工具
    private final ToolCallback[] availableTools;
    //保存工具调用信息的响应(要调用那些工具)
    private ChatResponse toolChatResponse;
    // AI 的思考过程文本（用于前端展示）
    private String lastThought;
    //工具调用管理者
    private ToolCallingManager toolCallingManager;
    // 禁用内置的工具调用机制，自己维护上下文
    private final ChatOptions chatOptions;

    public ToolCallAgent (ToolCallback[] availableTools){
        super();
        this.availableTools = availableTools;
        this.toolCallingManager =ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();

    };

    @Override
    public boolean think() {
        try {
            //1. 校验提示词，拼接用户提示词
            if(getNextStepPrompt()!=null&&!getNextStepPrompt().isEmpty()){
                UserMessage userMessage = new UserMessage(getNextStepPrompt());
                getMessageList().add(userMessage);
            }
            List<Message> messageList = getMessageList();
            Prompt prompt =new Prompt(messageList,chatOptions);

            ChatResponse chatResponse= getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();

            this.toolChatResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            String result = assistantMessage.getText();
            this.lastThought = result;
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            log.info(getName() + "的思考: " + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");

            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s,参数: %s",
                            toolCall.name(),
                            toolCall.arguments())
                    ).collect(Collectors.joining("\n"));
            log.info(getName() + "<UNK>: " + toolCallInfo);
            if (toolCallInfo.isEmpty()){
                setState(AgentState.FINISHED);
                getMessageList().add(assistantMessage);
                return false;
            }else {
                return true;
            }
        } catch (Exception e) {
           log.error(getName() + "思考中遇到了问题", e);
           getMessageList().add(new AssistantMessage("处理时遇到了错误"+e.getMessage()));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolChatResponse.hasToolCalls()){
            return "没有工具可调用";
        }
        //2构建prompt提示词，用于使用工具
        Prompt prompt = new Prompt(getMessageList(),chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolChatResponse);
        setMessageList(toolExecutionResult.conversationHistory());
        Message lastMsg = CollUtil.getLast(toolExecutionResult.conversationHistory());
        if (!(lastMsg instanceof ToolResponseMessage toolResponseMessage)) {
            return "工具调用返回异常";
        }
        String results = toolResponseMessage.getResponses().stream()
                .map(response ->"工具"+ response.name() + " 完成了它的任务！结果: " + response.responseData())
                .collect(Collectors.joining("\n"));
        //判断是否调用了终止工具
        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                        .anyMatch(response->"doTerminate".equals(response.name()));
        if (terminateToolCalled){
            setState(AgentState.FINISHED);
        }
        log.info(results);

        return results;
    }

    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                return lastThought != null ? lastThought : "思考完成";
            }
            // 执行工具（更新 messageList，供下轮 think 使用）
            act();
            // 提取本次调用了哪些工具
            String toolNames = toolChatResponse.getResult().getOutput().getToolCalls().stream()
                    .map(AssistantMessage.ToolCall::name)
                    .collect(Collectors.joining(", "));
            return (lastThought != null ? lastThought : "") + "\n→ 已调用工具: " + toolNames;
        } catch (Exception e) {
            log.error(getName() + "步骤执行失败", e);
            return "步骤执行失败: " + e.getMessage();
        }
    }

}