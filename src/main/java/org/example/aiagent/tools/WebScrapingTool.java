package org.example.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.tools.util.ToolRetryUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Slf4j
public class WebScrapingTool {

    /**
     * 页面下载超时（毫秒）。
     * 原实现 Jsoup.connect(url).get() 用默认超时，遇到慢站点会长时间挂住整个智能体循环。
     */
    private static final int TIMEOUT_MS = 15_000;

    /**
     * 返回内容上限，避免把巨型页面塞进上下文导致 token 爆炸。
     */
    private static final int MAX_CONTENT_LENGTH = 20_000;

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(
            @ToolParam(description = "URL of the web page to scrape") String url) {

        if (StrUtil.isBlank(url)) {
            return "[INVALID_ARGUMENT] URL 不能为空";
        }
        // 只允许 http/https，防止模型传入 file:// 之类的本地协议读取服务器文件
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "[INVALID_ARGUMENT] 仅支持 http/https 协议的 URL";
        }

        try {
            // 重试放在外层：Jsoup 的 IOException 多为瞬时网络问题，重试往往能成功
            return ToolRetryUtil.execute(() -> doScrape(url), "WebScrapingTool.scrapeWebPage");
        } catch (Exception e) {
            log.error("抓取网页失败, url={}", url, e);
            return ToolRetryUtil.exhaustedMessage("WebScrapingTool.scrapeWebPage", e);
        }
    }

    private String doScrape(String url) throws Exception {
        Document document = Jsoup.connect(url)
                .timeout(TIMEOUT_MS)
                // 伪装 UA：不少站点对无 UA 的请求直接返回 403，模型会误判为「页面不存在」
                .userAgent("Mozilla/5.0 (compatible; AiAgentBot/1.0)")
                .get();

        String html = document.html();

        if (html.length() > MAX_CONTENT_LENGTH) {
            log.info("页面内容过长({}字符)，截断至 {} 字符, url={}", html.length(), MAX_CONTENT_LENGTH, url);
            return html.substring(0, MAX_CONTENT_LENGTH) + "\n...[内容过长已截断]";
        }
        return html;
    }
}
