package org.example.aiagent.rag.github;

public record GithubContentResponse(
        String name,
        String path,
        String sha,
        Long size,
        String url,
        String html_url,
        String git_url,
        String download_url,
        String type,
        String encoding,
        String content
) {
}
