package org.example.aiagent.rag.github;

public record GithubTreeItem(
        String path,
        String mode,
        String type,
        String sha,
        Long size,
        String url
) {
}
