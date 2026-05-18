package com.anvilsecure.bytebanter.AIEngineUIs;

import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class AnthropicAIEngineUI extends AIEngineUI {

    private static final String DEFAULT_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_HEADERS_HINT = "x-api-key: YOUR_ANTHROPIC_API_KEY";
    private static final String[] DEFAULT_MODELS = {
            "claude-sonnet-4-6",
            "claude-opus-4-7",
            "claude-haiku-4-5-20251001",
            "claude-3-5-sonnet-latest",
            "claude-3-5-haiku-latest"
    };

    private JComboBox<String> modelCombo;

    // Sampling field gates: newer Claude models reject requests that specify both
    // `temperature` and `top_p` simultaneously, so each parameter is opt-in.
    // Defaults match Anthropic's current guidance: send temperature only.
    private JCheckBox sendTemperatureCheck;
    private JCheckBox sendTopPCheck;

    public AnthropicAIEngineUI(AIEngine engine) {
        super(engine);
    }

    @Override
    public JPanel getAIConfPanel() {
        JPanel configPanel = super.getAIConfPanel();
        urlField.setText(DEFAULT_URL);
        // Anthropic requires x-api-key. Ask the user to add it in the parent Headers field.
        if (headersField.getText().isEmpty()) {
            headersField.setText(DEFAULT_HEADERS_HINT);
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;

        modelCombo = new JComboBox<>(DEFAULT_MODELS);
        modelCombo.setEditable(true);
        JPanel modelPanel = new JPanel(new BorderLayout());
        modelPanel.setBorder(new TitledBorder("Model:"));
        modelPanel.add(modelCombo, BorderLayout.CENTER);
        gbc.gridy = 4;
        configPanel.add(modelPanel, gbc);

        return configPanel;
    }

    @Override
    public JPanel getParamPanel() {
        JPanel paramPanel = new JPanel(new GridLayout(0, 1));

        // Max Tokens (required by Anthropic)
        maxTokensSpinner = new JSpinner(new SpinnerNumberModel(1024, 1, 64000, 1));
        JPanel maxTokensPanel = new JPanel(new BorderLayout());
        maxTokensPanel.add(new JLabel("Max Tokens:"), BorderLayout.WEST);
        maxTokensPanel.add(maxTokensSpinner, BorderLayout.CENTER);
        paramPanel.add(maxTokensPanel);

        // Temperature and Top P are mutually exclusive on newer Claude models
        // (the API rejects requests that specify both). The UI enforces that:
        // ticking one auto-unticks the other. Both off is allowed → the model
        // applies its own defaults.
        temperatureSlider = new JSlider(0, 100, 70);
        JLabel tempLabel = new JLabel("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0));
        temperatureSlider.addChangeListener(
                e -> tempLabel.setText("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0)));
        sendTemperatureCheck = new JCheckBox("Send temperature", true);

        topPSlider = new JSlider(0, 20, 20);
        JLabel topPLabel = new JLabel("Top P: " + String.format("%.2f", topPSlider.getValue() / 20.0));
        topPSlider.addChangeListener(
                e -> topPLabel.setText("Top P: " + String.format("%.2f", topPSlider.getValue() / 20.0)));
        sendTopPCheck = new JCheckBox("Send top_p", false);
        topPSlider.setEnabled(false);
        topPLabel.setEnabled(false);

        sendTemperatureCheck.addActionListener(e -> {
            boolean on = sendTemperatureCheck.isSelected();
            if (on && sendTopPCheck.isSelected()) {
                sendTopPCheck.setSelected(false);
                topPSlider.setEnabled(false);
                topPLabel.setEnabled(false);
            }
            temperatureSlider.setEnabled(on);
            tempLabel.setEnabled(on);
        });
        sendTopPCheck.addActionListener(e -> {
            boolean on = sendTopPCheck.isSelected();
            if (on && sendTemperatureCheck.isSelected()) {
                sendTemperatureCheck.setSelected(false);
                temperatureSlider.setEnabled(false);
                tempLabel.setEnabled(false);
            }
            topPSlider.setEnabled(on);
            topPLabel.setEnabled(on);
        });

        JPanel tempPanel = new JPanel(new BorderLayout());
        tempPanel.add(sendTemperatureCheck, BorderLayout.WEST);
        tempPanel.add(tempLabel, BorderLayout.NORTH);
        tempPanel.add(temperatureSlider, BorderLayout.CENTER);
        paramPanel.add(tempPanel);

        JPanel topPPanel = new JPanel(new BorderLayout());
        topPPanel.add(sendTopPCheck, BorderLayout.WEST);
        topPPanel.add(topPLabel, BorderLayout.NORTH);
        topPPanel.add(topPSlider, BorderLayout.CENTER);
        paramPanel.add(topPPanel);

        return paramPanel;
    }

    @Override
    public JSONObject getParams() {
        JSONObject params = super.getParams();
        params.put("model", String.valueOf(modelCombo.getSelectedItem()));
        if (sendTemperatureCheck != null) {
            params.put("send_temperature", sendTemperatureCheck.isSelected());
        }
        if (sendTopPCheck != null) {
            params.put("send_top_p", sendTopPCheck.isSelected());
        }
        return params;
    }

    @Override
    public void loadParams(JSONObject params) {
        super.loadParams(params);
        modelCombo.setSelectedItem(params.optString("model", DEFAULT_MODELS[0]));
        boolean tempOn = params.optBoolean("send_temperature", true);
        boolean topPOn = params.optBoolean("send_top_p", false);
        // Defend against a stale settings blob with both flags set: keep
        // temperature, drop top_p (matches the new-install default).
        if (tempOn && topPOn) {
            topPOn = false;
        }
        if (sendTemperatureCheck != null) {
            sendTemperatureCheck.setSelected(tempOn);
            if (temperatureSlider != null) {
                temperatureSlider.setEnabled(tempOn);
            }
        }
        if (sendTopPCheck != null) {
            sendTopPCheck.setSelected(topPOn);
            if (topPSlider != null) {
                topPSlider.setEnabled(topPOn);
            }
        }
    }
}
