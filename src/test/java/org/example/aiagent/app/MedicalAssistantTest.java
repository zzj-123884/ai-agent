package org.example.aiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MedicalAssistantTest {
    @Resource
    private MedicalAssistant medicalAssistant;
    @Test
    void testChat() {
        String chatId= UUID.randomUUID().toString();
        String message="你好,我是程序员";
        String answer=medicalAssistant.doChat(message,chatId);
        message="我想让另一半(小美)和我做爱";
        answer=medicalAssistant.doChat(message,chatId);
        Assertions.assertNotNull(answer);
        message="我的另一半叫什么来着?";
        answer=medicalAssistant.doChat(message,chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatwithReporter() {
        String chatId= UUID.randomUUID().toString();
        String message="你好,我是小帅,我想让另一半(小美)更爱我,但我不知道怎么做";
        MedicalAssistant.HealthReport healthReport=medicalAssistant.doChatwithReporter(message,chatId);
        Assertions.assertNotNull(healthReport);
    }

    @Test
    void doChatWithTools() {

//        testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");

//        testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");

        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");

//        testMessage("执行 Python3 脚本来生成数据分析报告");

//        testMessage("把这次咨询摘要保存为文件");

//        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = medicalAssistant.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();

//        String message = "我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点";
//        String answer =  medicalAssistant.doChatWithMcp(message, chatId);
        String message = "帮我搜索一些哄另一半开心的图片";
        String answer =  medicalAssistant.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }

//    @Test
//    void doChatWithRag() {
//        String chatId= UUID.randomUUID().toString();
//        String message="你好,我是小帅,我想让另一半(小美)更爱我,但我不知道怎么做";
//        String answer=medicalAssistant.doChatWithRag(message,chatId);
//        Assertions.assertNotNull(answer);
//    }
}