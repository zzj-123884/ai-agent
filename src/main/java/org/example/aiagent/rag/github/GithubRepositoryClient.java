package org.example.aiagent.rag.github;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class GithubRepositoryClient {

    private final RestClient restClient;

    public GithubRepositoryClient(RestClient restClient) {
        this.restClient = restClient;
    }
    public GithubTreeResponse getTree(
            String owner,
            String repo,
            String branch) {

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "/repos/{owner}/{repo}/git/trees/{branch}"
                        )
                        .queryParam(
                                "recursive",
                                "1"
                        )
                        .build(
                                owner,
                                repo,
                                branch
                        ))
                .header(
                        "Accept",
                        "application/vnd.github+json"
                )
                .retrieve()
                .body(GithubTreeResponse.class);
    }

    public String getFileContent(
            String owner,
            String repo,
            String path,
            String branch
    ) {

        GithubContentResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/contents/{path}")
                        .queryParam("ref", branch)
                        .build(owner, repo, path))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2026-03-10")
                .retrieve()
                .body(GithubContentResponse.class);

        if (response == null) {
            throw new IllegalStateException(
                    "GitHub response is null"
            );
        }

        if (!"file".equals(response.type())) {
            throw new IllegalArgumentException(
                    "Path is not a file: " + path
            );
        }

        if (!"base64".equalsIgnoreCase(response.encoding())) {
            throw new IllegalStateException(
                    "Unsupported encoding: "
                            + response.encoding()
            );
        }

        if (response.content() == null) {
            throw new IllegalStateException(
                    "GitHub file content is empty: " + path
            );
        }

        // GitHub 返回的 Base64 内容通常带换行
        String base64 = response.content()
                .replace("\n", "")
                .replace("\r", "");

        byte[] decoded =
                Base64.getDecoder().decode(base64);

        return new String(
                decoded,
                StandardCharsets.UTF_8
        );
    }
}
