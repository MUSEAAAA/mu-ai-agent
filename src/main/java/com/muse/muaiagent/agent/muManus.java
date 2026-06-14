package com.muse.muaiagent.agent;

import com.muse.muaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class muManus  extends  ToolCallAgent{

    public muManus(ToolCallback[] allTool, ChatModel dashscopeChatModel) {
        super(allTool);
        this.setName("muManus");
        //英文版:
        //String SYSTEM_PROMPT = """
        //        You are muManus, an all-capable AI assistant, aimed at solving any task presented by the user.
        //        You have various tools at your disposal that you can call upon to efficiently complete complex requests.
        //        """;
        String SYSTEM_PROMPT = """
                你是 muManus，一个全能型 AI 助手，旨在解决用户提出的任何任务。
                你可以调用各种工具，高效完成复杂请求。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        //英文版:
        //String NEXT_STEP_PROMPT = """
        //        Based on user needs, proactively select the most appropriate tool or combination of tools.
        //        For complex tasks, you can break down the problem and use different tools step by step to solve it.
        //        After using each tool, clearly explain the execution results and suggest the next steps.
        //        If you want to stop the interaction at any point, use the `terminate` tool/function call.
        //        """;
        String NEXT_STEP_PROMPT = """
                根据用户需求，主动选择最合适的工具或工具组合。
                对于复杂任务，可以拆解问题，分步使用不同工具来解决。
                每次使用工具后，清晰说明执行结果并建议下一步。
                如果想在任何时候停止交互，使用 `terminate` 工具/函数调用来结束。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(10);
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);


    }

}


