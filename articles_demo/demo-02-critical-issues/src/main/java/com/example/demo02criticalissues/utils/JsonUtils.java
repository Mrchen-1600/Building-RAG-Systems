package com.example.demo02criticalissues.utils;

/**
 * ClassName: JsonUtils
 * Package: com.example.demo02criticalissues.utils
 *
 * @Author Mrchen
 */
public class JsonUtils {

    /**
     * 清理大模型返回的 JSON 字符串
     * 移除 ```json 和 ``` 等可能导致解析失败的标记
     *
     * @param rawResponse 大模型原始返回内容
     * @return 纯净的 JSON 字符串
     */
    public static String cleanJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "{}";
        }
        // 移除开头的 ```json 和结尾的 ```
        return rawResponse.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
    }

    /**
     * 安全提取 JSON 字符串（从大模型可能包含废话的文本中精准截取 JSON 块）
     *
     * @param response 大模型原始返回内容
     * @return 截取出的纯 JSON 字符串
     */
    public static String extractJson(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end >= 0 && end >= start) {
            return response.substring(start, end + 1);
        }
        return cleanJson(response);
    }
}