package org.example.aiagent.agent.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public abstract class ReActAgent extends BaseAgent {


    public abstract boolean think();


    public abstract String act();


    @Override
    public String step() {
        boolean shouldAct = think();
        if (!shouldAct) {
            // think() 返回 false 意味着模型已经给出结论，正文已通过 ANSWER 事件发出。
            setState(AgentState.FINISHED);
            return "思考完成 - 无需行动";
        }
        if (isCancelled()) {
            setState(AgentState.FINISHED);
            return "任务已停止";
        }
        return act();
    }
}
