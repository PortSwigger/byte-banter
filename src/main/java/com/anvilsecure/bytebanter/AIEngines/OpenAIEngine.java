package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import com.anvilsecure.bytebanter.AIEngineUIs.OpenAIEngineUI;
import org.json.*;

public class OpenAIEngine extends AIEngine {

    public OpenAIEngine(MontoyaApi api) {
        super(api, "OpenAI API (chat/completions)");
        super.UI = new OpenAIEngineUI(this);
        super.messages = new JSONArray();
    }
}
