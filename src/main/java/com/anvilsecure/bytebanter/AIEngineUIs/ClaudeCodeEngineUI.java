package com.anvilsecure.bytebanter.AIEngineUIs;

import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class ClaudeCodeEngineUI extends AIEngineUI {

    private static final String DEFAULT_BIN = "claude";
    private static final String[] DEFAULT_MODELS = {
            "",                              // empty = let Claude Code pick its default
            "claude-sonnet-4-6",
            "claude-opus-4-7",
            "claude-haiku-4-5-20251001",
            "claude-3-5-sonnet-latest",
            "claude-3-5-haiku-latest"
    };

    private JTextField binPathField;
    private JComboBox<String> modelCombo;

    public ClaudeCodeEngineUI(AIEngine engine) {
        super(engine);
    }

    @Override
    public JPanel getAIConfPanel() {
        // Build a custom config panel: this engine doesn't use URL/headers nor
        // the "Sensitive Data" persistence checkbox. It still needs the
        // request-limits widgets, which we get from the parent helper.
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Engine Configuration:"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;

        binPathField = new JTextField(DEFAULT_BIN, 40);
        JPanel binPanel = new JPanel(new BorderLayout());
        binPanel.setBorder(new TitledBorder("Claude Code binary (path or command):"));
        binPanel.add(binPathField, BorderLayout.CENTER);
        panel.add(binPanel, gbc);

        modelCombo = new JComboBox<>(DEFAULT_MODELS);
        modelCombo.setEditable(true);
        JPanel modelPanel = new JPanel(new BorderLayout());
        modelPanel.setBorder(new TitledBorder("Model (leave empty for Claude Code default):"));
        modelPanel.add(modelCombo, BorderLayout.CENTER);
        gbc.gridy = 1;
        panel.add(modelPanel, gbc);

        gbc.gridy = 2;
        panel.add(buildRequestLimitsPanel(), gbc);

        // Friendly note on auth and BApp Store status.
        JTextArea note = new JTextArea(
                "Authentication is delegated to the Claude Code CLI itself "
                        + "(claude.ai OAuth, API key, Bedrock, or Vertex). "
                        + "This engine spawns a local subprocess and bypasses Montoya "
                        + "networking, so it is intended for personal/research use rather "
                        + "than the BApp Store build.");
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setFocusable(false);
        note.setBorder(null);
        note.setForeground(new Color(0x666666));
        note.setFont(note.getFont().deriveFont(Font.ITALIC));
        gbc.gridy = 3;
        panel.add(note, gbc);

        return panel;
    }

    @Override
    public JPanel getParamPanel() {
        // Claude Code's -p mode does not expose temperature / top_p / max_tokens
        // tuning. Show a static info label instead of empty sliders.
        JPanel panel = new JPanel(new BorderLayout());
        JLabel info = new JLabel(
                "Generation parameters are managed by the Claude Code CLI; nothing to tune here.");
        info.setHorizontalAlignment(SwingConstants.LEFT);
        info.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(info, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public JSONObject getParams() {
        JSONObject params = super.getParams();
        params.put("model", String.valueOf(modelCombo.getSelectedItem()));
        params.put("claude_path", binPathField.getText());
        return params;
    }

    @Override
    public void loadParams(JSONObject params) {
        super.loadParams(params);
        modelCombo.setSelectedItem(params.optString("model", ""));
        binPathField.setText(params.optString("claude_path", DEFAULT_BIN));
    }
}
