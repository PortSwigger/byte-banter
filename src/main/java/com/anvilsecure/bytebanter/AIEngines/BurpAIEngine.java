package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ai.chat.Message;
import burp.api.montoya.ai.chat.PromptException;
import burp.api.montoya.ai.chat.PromptOptions;
import burp.api.montoya.ai.chat.PromptResponse;
import burp.api.montoya.core.Annotations;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import com.anvilsecure.bytebanter.AIEngineUIs.BurpAIEngineUI;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JOptionPane;

import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BurpAIEngine extends AIEngine {

    public BurpAIEngine(MontoyaApi api) {
        super(api, "BurpAI");
        super.UI = new BurpAIEngineUI(this);
        super.messages = new JSONArray();
    }

    @Override
    public String askAi() {
        if (!isAIEnabled()) {
            return null;
        }
        JSONObject params = UI.getParams();
        JSONObject data = packData(new JSONObject(), params);

        // Throttling control values come from the UI params, not from the API body data.
        isInfiniteRequests = params.optBoolean("isInfiniteRequests", false);
        int newLimit = params.optInt("requestsLimit", 1000000);
        if (oldInfiniteFlag != isInfiniteRequests || this.requestsLimit != newLimit) {
            counter = 0;
        }
        oldInfiniteFlag = isInfiniteRequests;
        this.requestsLimit = newLimit;

        // check if the number of maximum requests has been reached
        if (isInfiniteRequests || counter < this.requestsLimit) {
            counter++;

            // reset messages on "stateful" change or prompt change
            boolean settingsChanged = (isStateful != params.getBoolean("stateful"))
                    || !lastUsedPrompt.equals(params.getString("prompt"));

            isStateful = params.getBoolean("stateful");
            lastUsedPrompt = params.getString("prompt");

            // Reset if settings changed OR we are in stateless mode (fresh request every time)
            if (settingsChanged || !isStateful) {
                messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content", lastUsedPrompt));
            }

            // If stateless, always add the default trigger message
            if (!isStateful) {
                messages.put(new JSONObject().put("role", "user").put("content", DEFAULT_MESSAGE));
            } else {
                // In stateful mode, if we just reset (have [System]), add the trigger.
                // Otherwise, 'messages' already contains history.
                if (messages.length() == 1) {
                    messages.put(new JSONObject().put("role", "user").put("content", DEFAULT_MESSAGE));
                }
            }
            data.remove("messages");
            data.put("messages", messages);
            String responseMessage = sendRequestToAI(data, params);
            messages.put(new JSONObject().put("role", "assistant").put("content", responseMessage));
            return responseMessage;
        } else {
            counter = 0;
            return null;
        }
    }

    // used for other interaction with the AI (i.e.: prompt optimization)
    @Override
    public String askAi(String prompt, String user_input) {
        if (!isAIEnabled()) {
            return prompt;
        }
        JSONObject params = UI.getParams();
        JSONObject data = packData(new JSONObject(), params);
        ;
        JSONArray m = new JSONArray();
        m.put(new JSONObject().put("role", "system").put("content", prompt));
        m.put(new JSONObject().put("role", "user").put("content", user_input));
        data.remove("messages");
        data.put("messages", m);

        return sendRequestToAI(data, params);
    }

    @Override
    protected JSONObject packData(JSONObject data, JSONObject params) {
        data.put("temperature", params.getDouble("temperature") / 100);
        return data;
    }

    @Override
    protected String sendRequestToAI(JSONObject data, JSONObject params) {
        try {
            // set the temperature with PromptOptions
            PromptOptions options = PromptOptions.promptOptions().withTemperature(data.getDouble("temperature"));

            // Convert from JSONArray of {role, content} JSONObjects to BurpAI Message[].
            JSONArray messages = data.getJSONArray("messages");
            Message[] context = new Message[messages.length()];
            for (int i = 0; i < messages.length(); i++) {
                JSONObject m = messages.getJSONObject(i);
                String role = m.getString("role");
                String content = m.getString("content");
                if ("system".equals(role)) {
                    context[i] = Message.systemMessage(content);
                } else if ("assistant".equals(role)) {
                    context[i] = Message.assistantMessage(content);
                } else {
                    context[i] = Message.userMessage(content);
                }
            }

            // Execute Prompt
            PromptResponse response = api.ai().prompt().execute(options, context);
            return response.content();
        } catch (PromptException e) {
            JOptionPane.showMessageDialog(null, "An error occurred while processing the prompt: " + e.getMessage() +
                    "\nUnable to retrieve AI response. Please try again later. " +
                    "\n(Mostly this error could be related to BurpAI disabled or AI credit exhausted)",
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
            // return null, to stop payload generation.
            throw new PromptException(e.getMessage());
        }
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        JSONObject params = UI.getParams();
        if (isStateful) {
            Matcher matcher = Pattern.compile(params.getString("regex")).matcher(httpResponseReceived.bodyToString());
            if (matcher.find()) {
                String rxp = params.getBoolean("b64") ? Arrays.toString(Base64.getDecoder().decode(matcher.group(1)))
                        : matcher.group(1);
                messages.put(new JSONObject().put("role", "user").put("content", rxp));
            }
        }
        Annotations a = runVerificationIfApplicable(httpResponseReceived, params);
        return a != null
                ? ResponseReceivedAction.continueWith(httpResponseReceived, a)
                : ResponseReceivedAction.continueWith(httpResponseReceived);
    }

}
