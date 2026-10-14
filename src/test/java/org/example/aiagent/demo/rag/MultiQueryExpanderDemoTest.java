package org.example.aiagent.demo.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class MultiQueryExpanderDemoTest {

    @Resource
    private  MultiQueryExpanderDemo multiQueryExpanderDemo;
    @Test
    void expand() {
        List<Query> queries = multiQueryExpanderDemo.expand("谁是程序员鱼皮啊？");
        Assertions.assertNotNull(queries);
    }
}