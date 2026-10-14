package org.example.aiagent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.example.aiagent.tools.util.ToolRetryUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class WebSearchTool {
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    /**
     * 单次搜索最多返回的结果条数。
     * 原实现固定写 subList(0, 5)，若上游只返回 3 条会抛
     * IndexOutOfBoundsException —— 这是原代码一个真实的崩溃隐患。
     */
    private static final int MAX_RESULTS = 5;

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {

        // 参数校验：模型偶尔会传空串，直接返回明确提示比发一个无意义请求更省成本
        if (StrUtil.isBlank(query)) {
            return "[INVALID_ARGUMENT] 搜索关键词不能为空";
        }
        if (StrUtil.isBlank(apiKey)) {
            log.error("search-api.api-key 未配置，WebSearchTool 不可用");
            return "[CONFIG_ERROR] 搜索服务未配置 API Key，请联系管理员";
        }

        try {
            return ToolRetryUtil.execute(() -> doSearch(query), "WebSearchTool.searchWeb");
        } catch (Exception e) {
            log.error("搜索失败, query={}", query, e);
            return ToolRetryUtil.exhaustedMessage("WebSearchTool.searchWeb", e);
        }
    }

    private String doSearch(String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");

        String response = HttpUtil.get(SEARCH_API_URL, paramMap);

        if (StrUtil.isBlank(response)) {
            // 返回空串：抛异常以触发重试，而不是把空结果喂给模型
            throw new IllegalStateException("搜索接口返回空响应");
        }

        JSONObject jsonObject = JSONUtil.parseObj(response);
        JSONArray organicResults = jsonObject.getJSONArray("organic_results");

        if (organicResults == null || organicResults.isEmpty()) {
            return "未搜索到相关结果，请尝试更换关键词";
        }

        // 关键修复：取 min(size, MAX_RESULTS)，而不是无条件 subList(0, 5)
        List<Object> objects = organicResults.subList(0, Math.min(organicResults.size(), MAX_RESULTS));

        String result = objects.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));

        log.debug("搜索完成, query={}, 返回 {} 条", query, objects.size());
        return result;
    }
}
