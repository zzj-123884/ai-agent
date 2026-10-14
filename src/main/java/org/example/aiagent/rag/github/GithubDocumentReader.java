package org.example.aiagent.rag.github;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;

import java.util.List;
import java.util.Locale;

public class GithubDocumentReader implements DocumentReader {

    private final GithubRepositoryClient githubClient;

    private final String owner;
    private final String repo;
    private final String branch;

    public GithubDocumentReader(
            GithubRepositoryClient githubClient,
            String owner,
            String repo,
            String branch) {

        this.githubClient = githubClient;
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
    }

    @Override
    public List<Document> get() {

        GithubTreeResponse tree =
                githubClient.getTree(
                        owner,
                        repo,
                        branch
                );

        return tree.tree()
                .stream()

                // 只处理文件
                .filter(item ->
                        "blob".equals(item.type()))

                // 只处理我们关心的文件
                .filter(this::supportedFile)

                .map(this::toDocument)

                .toList();
    }

    private boolean supportedFile(GithubTreeItem item) {

        String path = item.path();

        return path.endsWith(".md");
//                || path.endsWith(".java")
//                || path.endsWith(".xml")
//                || path.endsWith(".yml")
//                || path.endsWith(".yaml")
//                || path.endsWith(".json");
    }

    private Document toDocument(GithubTreeItem item) {

        String content =
                githubClient.getFileContent(
                        owner,
                        repo,
                        item.path(),
                        branch
                );

        return Document.builder()
                .text(content)
                .metadata("source", item.path())
                .metadata("repository",
                        owner + "/" + repo)
                .metadata("branch", branch)
                .metadata("language",
                        getLanguage(item.path()))
                .build();
    }

    private String getFileExtension(
            String path) {

        int index =
                path.lastIndexOf('.');

        if (index == -1
                || index == path.length() - 1) {
            return "unknown";
        }

        return path
                .substring(index + 1)
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 根据文件后缀判断编程语言
     */
    private String getLanguage(
            String path) {

        String extension =
                getFileExtension(path);

        return switch (extension) {

            case "java" ->
                    "java";

            case "kt", "kts" ->
                    "kotlin";

            case "js", "jsx" ->
                    "javascript";

            case "ts", "tsx" ->
                    "typescript";

            case "py" ->
                    "python";

            case "go" ->
                    "go";

            case "rs" ->
                    "rust";

            case "c", "h" ->
                    "c";

            case "cpp", "hpp", "cc" ->
                    "cpp";

            case "xml" ->
                    "xml";

            case "yml", "yaml" ->
                    "yaml";

            case "json" ->
                    "json";

            case "md", "markdown" ->
                    "markdown";

            case "properties" ->
                    "properties";

            case "sql" ->
                    "sql";

            default ->
                    "unknown";
        };
    }
}