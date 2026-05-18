package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.utilities.shell.ExecuteOptions;
import burp.api.montoya.utilities.shell.ExitCodeBehavior;
import burp.api.montoya.utilities.shell.ProcessExecutionException;
import burp.api.montoya.utilities.shell.StderrBehavior;
import burp.api.montoya.utilities.shell.TimeoutBehavior;
import com.anvilsecure.bytebanter.AIEngineUIs.ClaudeCodeEngineUI;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JOptionPane;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Engine that bridges ByteBanter to the Claude Code CLI ("claude -p"), letting
 * the user route prompts through their existing Anthropic subscription
 * (Claude Pro / Max) instead of paying per token via the API.
 *
 * <p>Subprocess execution is delegated to Montoya's
 * {@code api.utilities().shellUtils()} (added in Montoya 2025.6). The transcript
 * is passed as the {@code -p} argument value; stderr is merged into the result
 * stream so any CLI diagnostic surfaces in the error dialog. Authentication is
 * delegated to Claude Code itself (API key, OAuth, Bedrock, or Vertex).</p>
 */
public class ClaudeCodeEngine extends AIEngine {

    private static final long PROCESS_TIMEOUT_SEC = 120;

    public ClaudeCodeEngine(MontoyaApi api) {
        super(api, "Claude Code (CLI)");
        super.UI = new ClaudeCodeEngineUI(this);
        super.messages = new JSONArray();
    }

    @Override
    protected JSONObject packData(JSONObject data, JSONObject params) {
        // Claude Code -p does not accept temperature / top_p / max_tokens flags.
        // We just carry forward the model and the binary path.
        data.put("model", params.optString("model", ""));
        data.put("claude_path", params.optString("claude_path", "claude"));
        return data;
    }

    @Override
    protected String sendRequestToAI(JSONObject data, JSONObject params) {
        String binPath = params.optString("claude_path", "claude").trim();
        if (binPath.isEmpty()) {
            binPath = "claude";
        }
        String model = params.optString("model", "").trim();

        // Convert messages[] into:
        //   - systemPrompt (concatenated, passed via --append-system-prompt)
        //   - turnsText    (user/assistant turns, passed as the -p argument)
        JSONArray msgs = data.getJSONArray("messages");
        StringBuilder systemPromptB = new StringBuilder();
        StringBuilder turns = new StringBuilder();
        for (int i = 0; i < msgs.length(); i++) {
            JSONObject m = msgs.getJSONObject(i);
            String role = m.getString("role");
            String content = m.getString("content");
            if ("system".equals(role)) {
                if (systemPromptB.length() > 0) {
                    systemPromptB.append("\n\n");
                }
                systemPromptB.append(content);
            } else {
                turns.append("[").append(role.toUpperCase()).append("]\n");
                turns.append(content).append("\n\n");
            }
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(binPath);
        cmd.add("-p");
        // ShellUtils does not pipe stdin; pass the transcript as the prompt arg.
        cmd.add(turns.toString());
        cmd.add("--output-format");
        cmd.add("text");
        if (!model.isEmpty()) {
            cmd.add("--model");
            cmd.add(model);
        }
        if (systemPromptB.length() > 0) {
            cmd.add("--append-system-prompt");
            cmd.add(systemPromptB.toString());
        }

        ExecuteOptions opts = ExecuteOptions.executeOptions()
                .withTimeout(Duration.ofSeconds(PROCESS_TIMEOUT_SEC))
                .withTimeoutBehavior(TimeoutBehavior.FAIL_ON_TIMEOUT)
                .withStderrBehavior(StderrBehavior.MERGE)
                .withExitCodeBehavior(ExitCodeBehavior.FAIL_ON_NON_ZERO);

        try {
            String output = api.utilities()
                    .shellUtils()
                    .execute(opts, cmd.toArray(new String[0]));
            return output == null ? "" : output.trim();
        } catch (ProcessExecutionException e) {
            JOptionPane.showMessageDialog(null,
                    "ByteBanter: Claude Code execution failed.\n"
                            + "Check that '" + binPath + "' is installed and on PATH.\n\n"
                            + e.getMessage(),
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}
