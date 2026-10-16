package org.example.aiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MedicalDocumentLoaderTest {

    @Resource
    private MedicalDocumentLoader MedicalDocumentLoader;

    @Test
    void loadMarkdowns() {
        MedicalDocumentLoader.loadMarkdowns();
    }
}