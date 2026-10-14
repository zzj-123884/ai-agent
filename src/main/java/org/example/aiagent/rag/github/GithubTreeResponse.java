package org.example.aiagent.rag.github;

import java.util.List;

public record GithubTreeResponse(
        String sha,
        Boolean truncated,
        List<GithubTreeItem> tree
) {
}
