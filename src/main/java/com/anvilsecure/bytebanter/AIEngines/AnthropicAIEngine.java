package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.anvilsecure.bytebanter.AIEngineUIs.AnthropicAIEngineUI;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JOptionPane;

public class AnthropicAIEngine extends AIEngine {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_ANTHROPIC_HOST = "api.anthropic.com";

    public AnthropicAIEngine(MontoyaApi api) {
        super(api, "Anthropic");
        super.UI = new AnthropicAIEngineUI(this);
        super.messages = new JSONArray();
    }

    @Override
    protected JSONObject packData(JSONObject data, JSONObject params) {
        data.put("model", params.getString("model"));
        data.put("max_tokens", params.getInt("max_tokens"));
        data.put("temperature", params.getDouble("temperature") / 100);
        data.put("top_p", params.getDouble("top_p") / 20);
        return data;
    }

    @Override
    protected String sendRequestToAI(JSONObject data, JSONObject params) {
        String url = params.getString("URL");
        String userHeaders = params.optString("headers", "");

        // Enforce x-api-key only when targeting the official Anthropic endpoint.
        // Custom URLs (proxies, mocks, gateways) may not require it.
        if (url.toLowerCase().contains(DEFAULT_ANTHROPIC_HOST)
                && !userHeaders.toLowerCase().contains("x-api-key")) {
            JOptionPane.showMessageDialog(null,
                    "ByteBanter: Anthropic requires an x-api-key header. Add it in the Headers field as:\n"
                            + "x-api-key: YOUR_ANTHROPIC_API_KEY",
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        // Anthropic requires the system prompt as a top-level field, not in messages[].
        JSONArray msgs = data.getJSONArray("messages");
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.getJSONObject(i);
            if ("system".equals(m.getString("role"))) {
                data.put("system", m.getString("content"));
            } else {
                filtered.put(m);
            }
        }
        data.put("messages", filtered);

        HttpRequest request = HttpRequest.httpRequestFromUrl(url);
        request = request.withMethod("POST");
        request = request.withAddedHeader("Content-Type", "application/json");
        request = request.withAddedHeader("anthropic-version", ANTHROPIC_VERSION);
        // Multi-header support: one header per line, empty lines ignored.
        for (String line : userHeaders.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                request = request.withAddedHeader(HttpHeader.httpHeader(trimmed));
            }
        }
        request = request.withBody(data.toString());

        // BApp Store requirement: enforce upstream TLS verification on third-party LLM calls.
        RequestOptions options = RequestOptions.requestOptions().withUpstreamTLSVerification();
        HttpRequestResponse response = api.http().sendRequest(request, options);

        JSONObject body = new JSONObject(response.response().bodyToString());
        if (!body.has("content")) {
            JOptionPane.showMessageDialog(null,
                    "ByteBanter: Unexpected response from Anthropic API:\n" + body.toString(),
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        JSONArray content = body.getJSONArray("content");
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) {
                return block.getString("text");
            }
        }
        return "";
    }
}
