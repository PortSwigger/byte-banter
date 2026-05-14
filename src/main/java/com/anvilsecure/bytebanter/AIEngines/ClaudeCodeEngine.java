package com.anvilsecure.bytebanter.AIEngines;

import burp.api.montoya.MontoyaApi;
import com.anvilsecure.bytebanter.AIEngineUIs.ClaudeCodeEngineUI;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.JOptionPane;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Engine that bridges ByteBanter to the Claude Code CLI ("claude -p"), letting
 * the user route prompts through their existing Anthropic subscription
 * (Claude Pro / Max) instead of paying per token via the API.
 *
 * <p>Notes:</p>
 * <ul>
 *   <li>Requires the Claude Code CLI to be installed on the host running Burp.</li>
 *   <li>Each prompt spawns a subprocess; expect ~1-3s of overhead per call.</li>
 *   <li>Authentication is delegated to Claude Code (whatever method the user
 *       has configured: API key, OAuth, Bedrock, Vertex).</li>
 *   <li>This engine bypasses the Montoya networking API. It is fine for
 *       personal use but is unlikely to satisfy PortSwigger's BApp Store
 *       policy for third-party LLM extensions.</li>
 * </ul>
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
        //   - systemPrompt (concatenated, passed via --system-prompt)
        //   - turnsText (user/assistant turns, piped via stdin as a transcript)
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

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            Process p = pb.start();

            try (OutputStream stdin = p.getOutputStream()) {
                stdin.write(turns.toString().getBytes(StandardCharsets.UTF_8));
            }

            byte[] stdoutBytes = p.getInputStream().readAllBytes();
            byte[] stderrBytes = p.getErrorStream().readAllBytes();

            if (!p.waitFor(PROCESS_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                JOptionPane.showMessageDialog(null,
                        "ByteBanter: Claude Code timed out after " + PROCESS_TIMEOUT_SEC + "s.",
                        "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }

            if (p.exitValue() != 0) {
                String err = new String(stderrBytes, StandardCharsets.UTF_8).trim();
                JOptionPane.showMessageDialog(null,
                        "ByteBanter: Claude Code exited with code " + p.exitValue()
                                + (err.isEmpty() ? "" : ":\n" + err),
                        "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }

            return new String(stdoutBytes, StandardCharsets.UTF_8).trim();
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(null,
                    "ByteBanter: failed to launch '" + binPath + "'. Is the Claude Code CLI installed and on PATH?\n"
                            + e.getMessage(),
                    "ByteBanter Error", JOptionPane.ERROR_MESSAGE);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
