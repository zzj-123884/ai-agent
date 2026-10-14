package org.example.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.constant.FileConstant;
import org.example.aiagent.tools.util.ToolRetryUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

@Slf4j
public class ResourceDownloadTool {
    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url, @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        if (StrUtil.isBlank(url) || StrUtil.isBlank(fileName)) {
            return "[INVALID_ARGUMENT] url 与 fileName 均不能为空";
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "[INVALID_ARGUMENT] 仅支持 http/https 协议的 URL";
        }
        // 路径穿越防护：模型可能传入 "../../etc/passwd" 这类文件名试图写到目录外
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return "[INVALID_ARGUMENT] 文件名不能包含路径分隔符或 ..";
        }

        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        String filePath = fileDir + "/" + fileName;
        try {
            return ToolRetryUtil.execute(() -> {
                FileUtil.mkdir(fileDir);
                HttpUtil.downloadFile(url, new File(filePath));
                return "资源已下载至: " + filePath;
            }, "ResourceDownloadTool.downloadResource");
        } catch (Exception e) {
            log.error("下载资源失败, url={}", url, e);
            return ToolRetryUtil.exhaustedMessage("ResourceDownloadTool.downloadResource", e);
        }
    }
}

