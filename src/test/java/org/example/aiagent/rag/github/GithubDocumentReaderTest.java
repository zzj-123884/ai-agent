package org.example.aiagent.rag.github;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GithubDocumentReaderTest {
    @Test
    void testReadGithubRepository() {
        String token = System.getenv("GITHUB_TOKEN");
        RestClient restClient = RestClient.builder().baseUrl("https://api.github.com").defaultHeader(
                        "Accept",
                        "application/vnd.github+json"
                )
                .defaultHeader(
                        "Authorization",
                        "Bearer " + token
                )
                .build();
        GithubRepositoryClient githubClient = new GithubRepositoryClient(restClient);
        GithubDocumentReader reader = new GithubDocumentReader( githubClient, "spring-projects", "spring-ai", "main" );
        List<Document> documents = reader.read();
        assertFalse( documents.isEmpty(), "GitHub 仓库读取结果不应该为空" ); // 6. 打印读取结果 System.out.println( "读取 Document 数量: " + documents.size() );
        // 7. 检查第一个 Document
        Document document = documents.get(0); assertNotNull(document);
        // Document 正文
        System.out.println( "==============================" );
        System.out.println( "Document Content:" ); System.out.println( document.getText() );
        // Document Metadata
        System.out.println( "Document Metadata:" );
        System.out.println( document.getMetadata() );
        // 8. 验证 metadata
        assertNotNull( document.getMetadata().get("source") );
        assertNotNull( document.getMetadata().get("repository") );
        assertNotNull( document.getMetadata().get("branch") );
        assertNotNull( document.getMetadata().get("language") );
    }

}