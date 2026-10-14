package org.example.aiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppDocumentLoaderTest {

    @Resource
    private LoveAppDocumentLoader LoveAppDocumentLoader;

    @Test
    void loadMarkdowns() {
        LoveAppDocumentLoader.loadMarkdowns();
    }
}