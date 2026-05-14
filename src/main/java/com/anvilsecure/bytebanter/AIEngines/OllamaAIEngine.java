package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import com.anvilsecure.bytebanter.AIEngineUIs.OllamaAIEngineUI;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Random;

public class OllamaAIEngine extends AIEngine {
    private static final String THINK_REGEX = "<think>(\n.*)*</think>";

    public OllamaAIEngine(MontoyaApi api) {
        super(api, "Ollama");
        UI = new OllamaAIEngineUI(this);
        messages = new JSONArray();
    }

    @Override
    protected JSONObject packData(JSONObject data, JSONObject params) {
        JSONObject options = new JSONObject();
        options.put("temperature", params.getInt("temperature") / 100);
        options.put("top_p", params.getInt("top_p") / 20);
        data.put("seed", new Random().nextInt() % 10000);
        data.put("options", options);
        data.put("stream", false);
        data.put("model", params.getString("model"));
        return data;
    }

    @Override
    protected String sendRequestToAI(JSONObject data, JSONObject params) {
        JSONObject response = sendPostRequest(params.get("URL") + "api/chat", data.toString(),
                params.getString("headers"));
        return response.getJSONObject("message").getString("content").replaceAll(THINK_REGEX, "");
    }

    @Override
    public String askAi() {
        String response = super.askAi();
        return response == null ? null : response.replaceAll(THINK_REGEX, "");
    }

    // used for other interaction with the AI (i.e.: prompt optimization)
    @Override
    public String askAi(String prompt, String user_input) {
        String response = super.askAi(prompt, user_input);
        return response == null ? null : response.replaceAll(THINK_REGEX, "");
    }
}
