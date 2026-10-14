package org.example.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.nio.charset.StandardCharsets;

@Slf4j
public class FileOperationTool {
    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    /**
     * 读取内容上限，防止把超大文件塞进模型上下文导致 token 超限。
     */
    private static final int MAX_READ_LENGTH = 20_000;

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of the file to read") String fileName) {
        // 路径穿越防护：模型完全可能构造 "../../application.yaml" 读取服务端敏感配置
        String violation = validateFileName(fileName);
        if (violation != null) {
            return violation;
        }

        File file = new File(FILE_DIR, fileName);
        if (!file.exists()) {
            return "[FILE_NOT_FOUND] 文件不存在: " + fileName;
        }
        if (!file.isFile()) {
            return "[INVALID_ARGUMENT] 目标不是普通文件: " + fileName;
        }

        try {
            String content = FileUtil.readString(file, StandardCharsets.UTF_8);
            if (content.length() > MAX_READ_LENGTH) {
                log.info("文件过长({}字符)，截断至 {} 字符, file={}",
                        content.length(), MAX_READ_LENGTH, fileName);
                return content.substring(0, MAX_READ_LENGTH) + "\n...[内容过长已截断]";
            }
            return content;
        } catch (Exception e) {
            log.error("读取文件失败, file={}", fileName, e);
            return "[IO_ERROR] 读取文件失败: " + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(
            @ToolParam(description = "Name of the file to write") String fileName,
            @ToolParam(description = "Content to write to the file") String content) {
        String violation = validateFileName(fileName);
        if (violation != null) {
            return violation;
        }
        if (content == null) {
            return "[INVALID_ARGUMENT] 写入内容不能为空";
        }

        try {
            FileUtil.mkdir(FILE_DIR);
            File target = new File(FILE_DIR, fileName);
            FileUtil.writeString(content, target, StandardCharsets.UTF_8);
            return "文件已写入: " + target.getAbsolutePath();
        } catch (Exception e) {
            log.error("写入文件失败, file={}", fileName, e);
            return "[IO_ERROR] 写入文件失败: " + e.getMessage();
        }
    }

    /**
     * 校验文件名，不合法时返回给模型的提示文本，合法时返回 null。
     * <p>
     * 这是一道真实的安全边界：FILE_DIR 是服务端本地目录，
     * 若不校验，模型可以通过 {@code ../../.env} 读写容器内的任意文件。
     */
    private String validateFileName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "[INVALID_ARGUMENT] 文件名不能为空";
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return "[INVALID_ARGUMENT] 文件名不能包含路径分隔符或 ..";
        }
        return null;
    }
}
