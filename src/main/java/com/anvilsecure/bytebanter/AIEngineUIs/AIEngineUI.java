package com.anvilsecure.bytebanter.AIEngineUIs;

import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.text.NumberFormat;

public abstract class AIEngineUI {
    private JCheckBox statefulCheck;
    private JTextField regexField;
    private JCheckBox b64Check;
    // Context extraction mode: "Regex" (regexField + b64Check) or "Script (Groovy)" (extractScriptField).
    private JComboBox<String> extractModeCombo;
    private JTextArea extractScriptField;
    private JTextArea promptField;

    // ASCII smuggling (hide text in the invisible Unicode Tags block).
    protected JCheckBox smuggleEnabledCheck;
    protected JComboBox<String> smuggleModeCombo;
    protected JTextArea smuggleHiddenText;

    // Last sample response used in the script editor, remembered across pop-up opens this session.
    private String lastSampleResponse = "";
    protected AIEngine model;
    protected JTextField urlField;
    protected JTextArea headersField;

    // Request Limit Config
    protected JCheckBox infiniteRequestCheck;
    protected JFormattedTextField requestLimitField;

    // Persist sensitive data (API key, custom headers) opt-in.
    protected JCheckBox persistSensitiveCheck;

    // Success Verification
    protected JCheckBox verifyEnabledCheck;
    protected JTextArea verifyCriterionField;
    protected JSpinner verifyHistoryDepthSpinner;     // shown in stateful mode
    protected JSpinner verifyTruncateCharsSpinner;    // shown in stateless mode

    private static final String DEFAULT_EXTRACT_SCRIPT =
            "// Available: body, status, headers, request, response\n"
                    + "// Return the string to feed back to the AI, or null to skip this response.\n"
                    + "def m = (body =~ /\"answer\":\"([^\"]*)\"/)\n"
                    + "m ? m[0][1] : null";

    private static final String VERIFY_DEFAULT_CRITERION =
            "The HTTP response body contains tokens that look like leaked secrets "
                    + "(passwords, API keys, session IDs), database error fragments "
                    + "(SQLSTATE, ORA-, MySQL), or stack traces revealing internal paths. "
                    + "Status code 500 alone is NOT enough.";

    protected JSpinner maxTokensSpinner;
    protected JSlider temperatureSlider;
    protected JSlider topPSlider;
    protected JSlider frequencyPenaltySlider;
    protected JSlider presencePenaltySlider;

    public AIEngineUI(AIEngine model) {
        this.model = model;
    }

    public JPanel getPromptPanel(String default_prompt) {
        JPanel promptPanel = new JPanel(new GridBagLayout());
        promptPanel.setBorder(new TitledBorder("Prompt:"));
        promptField = new JTextArea(default_prompt, 50, 50);
        promptField.setMinimumSize(new Dimension(50, 50));
        JScrollPane promptScrollPane = new JScrollPane(promptField, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        promptScrollPane.getVerticalScrollBar().setUnitIncrement(18);
        promptField.setLineWrap(true);
        promptScrollPane.setMinimumSize(new Dimension(50, 50));
        promptPanel.add(promptScrollPane, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        return promptPanel;
    }

    /**
     * Builds the Request Limits panel and instantiates the supporting fields
     * (infiniteRequestCheck and requestLimitField). Reused by engines that
     * don't need the parent's full URL/headers configuration but still need
     * the throttling logic.
     */
    protected JPanel buildRequestLimitsPanel() {
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        limitPanel.setBorder(new TitledBorder("Request Limits:"));

        infiniteRequestCheck = new JCheckBox("Infinite Requests");
        limitPanel.add(infiniteRequestCheck);

        NumberFormat format = NumberFormat.getIntegerInstance();
        format.setGroupingUsed(false);
        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(1);
        formatter.setMaximum(1000000);
        formatter.setAllowsInvalid(false);
        formatter.setCommitsOnValidEdit(true);

        requestLimitField = new JFormattedTextField(formatter);
        requestLimitField.setValue(10);
        requestLimitField.setColumns(8);
        limitPanel.add(new JLabel("Limit:"));
        limitPanel.add(requestLimitField);

        infiniteRequestCheck.addActionListener(e ->
                requestLimitField.setEnabled(!infiniteRequestCheck.isSelected()));

        return limitPanel;
    }

    public JPanel getStatePanel() {
        JPanel statePanel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;

        statefulCheck = new JCheckBox("Enable", false);
        statePanel.add(statefulCheck, gbc);

        // Extraction mode selector: Regex (original behaviour) vs Groovy script.
        extractModeCombo = new JComboBox<>(new String[] { "Regex", "Script (Groovy)" });
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeRow.add(new JLabel("Extraction mode:"));
        modeRow.add(extractModeCombo);
        gbc.gridy = 1;
        statePanel.add(modeRow, gbc);

        // Small card: regex field + base64 checkbox.
        JPanel regexCard = new JPanel(new GridLayout(2, 1));
        regexField = new JTextField("^\\{.*\\\"answer\\\":\\\"(.*)\\\",\\\"defender\\\".*\\}$");
        b64Check = new JCheckBox("Base64 decoding", false);
        regexCard.add(regexField);
        regexCard.add(b64Check);

        // Tall card: highlighted Groovy preview + editor button.
        JPanel scriptCard = new JPanel(new BorderLayout());
        JLabel scriptHelp = new JLabel(
                "Variables: body, status, headers, request, response — "
                        + "return the extracted String, or null to skip.");
        ScriptEditorDialog.GroovyEditor scriptEd = ScriptEditorDialog.newGroovyEditor(6, 40);
        extractScriptField = scriptEd.area;
        extractScriptField.setText(DEFAULT_EXTRACT_SCRIPT);
        JComponent scriptScroll = scriptEd.scroll;
        scriptScroll.setPreferredSize(new Dimension(400, 140));
        JButton editScriptButton = new JButton("Edit / Test script...");
        editScriptButton.addActionListener(e -> openScriptEditor());
        JPanel scriptButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        scriptButtonRow.add(editScriptButton);
        scriptCard.add(scriptHelp, BorderLayout.NORTH);
        scriptCard.add(scriptScroll, BorderLayout.CENTER);
        scriptCard.add(scriptButtonRow, BorderLayout.SOUTH);

        // Manual swap (not CardLayout) so the panel shrinks to the regex card's small
        // height instead of always reserving the tall script editor's footprint.
        final JPanel cardHolder = new JPanel(new BorderLayout());
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        statePanel.add(cardHolder, gbc);

        Runnable showSelectedCard = () -> {
            cardHolder.removeAll();
            cardHolder.add(extractModeCombo.getSelectedIndex() == 1 ? scriptCard : regexCard, BorderLayout.CENTER);
            cardHolder.revalidate();
            cardHolder.repaint();
        };
        // Fires on both user clicks and programmatic setSelectedIndex (e.g. loadParams).
        extractModeCombo.addActionListener(e -> showSelectedCard.run());
        showSelectedCard.run();

        return statePanel;
    }

    /** Opens the syntax-highlighted pop-up editor and copies the result back on OK. */
    private void openScriptEditor() {
        showScriptEditor(null);
    }

    /**
     * Entry point for the "Send to extraction script" context-menu action: switches
     * the panel into stateful Script mode and opens the editor with the given response
     * pre-loaded as the sample to test/generate against.
     */
    public void openScriptEditorWithResponse(String response) {
        if (statefulCheck != null) {
            statefulCheck.setSelected(true);
        }
        if (extractModeCombo != null) {
            extractModeCombo.setSelectedIndex(1); // Script (Groovy)
        }
        showScriptEditor(response);
    }

    private void showScriptEditor(String initialResponse) {
        Window owner = SwingUtilities.getWindowAncestor(extractScriptField);
        String goal = (promptField != null) ? promptField.getText() : "";
        // Seed with the passed-in response (e.g. from the context menu), otherwise reuse the
        // last sample so pasted/loaded/sent responses survive across editor opens this session.
        String seed = (initialResponse != null && !initialResponse.isEmpty())
                ? initialResponse : lastSampleResponse;
        ScriptEditorDialog dialog =
                new ScriptEditorDialog(owner, extractScriptField.getText(), model, goal, seed);
        dialog.setVisible(true);
        lastSampleResponse = dialog.getSampleResponse();
        if (dialog.isConfirmed()) {
            extractScriptField.setText(dialog.getScript());
        }
    }

    /**
     * ASCII smuggling panel. When enabled, the generated payload is transformed
     * before it reaches Intruder (see {@code AIEngine.applyPayloadTransforms}):
     * either the whole payload is rendered invisible, or a user-supplied hidden
     * text is appended/prepended to the visible payload.
     */
    public JPanel getSmugglePanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;

        smuggleEnabledCheck = new JCheckBox("Hide text in invisible Unicode Tags (U+E0000 block)", false);
        panel.add(smuggleEnabledCheck, gbc);

        smuggleModeCombo = new JComboBox<>(new String[] {
                "Encode entire payload",
                "Append hidden text to payload",
                "Prepend hidden text to payload" });
        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modeRow.add(new JLabel("Mode:"));
        modeRow.add(smuggleModeCombo);
        gbc.gridy = 1;
        panel.add(modeRow, gbc);

        smuggleHiddenText = new JTextArea("", 3, 40);
        smuggleHiddenText.setLineWrap(true);
        smuggleHiddenText.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(smuggleHiddenText);
        sp.setPreferredSize(new Dimension(400, 70));
        sp.setBorder(new TitledBorder("Hidden text (used by append/prepend modes):"));
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panel.add(sp, gbc);

        smuggleEnabledCheck.addActionListener(e -> syncSmuggleEnabled());
        smuggleModeCombo.addActionListener(e -> syncSmuggleEnabled());
        syncSmuggleEnabled();

        return panel;
    }

    /** Enables the mode/hidden-text widgets only when smuggling is on and the mode needs hidden text. */
    private void syncSmuggleEnabled() {
        boolean on = smuggleEnabledCheck.isSelected();
        smuggleModeCombo.setEnabled(on);
        // "Encode entire payload" (index 0) does not use the hidden-text field.
        smuggleHiddenText.setEnabled(on && smuggleModeCombo.getSelectedIndex() != 0);
    }

    public JPanel getVerificationPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Success Verification:"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;

        verifyEnabledCheck = new JCheckBox(
                "Verify each Intruder response with the LLM (one extra LLM call per result)", false);
        panel.add(verifyEnabledCheck, gbc);

        verifyCriterionField = new JTextArea(VERIFY_DEFAULT_CRITERION, 4, 40);
        verifyCriterionField.setLineWrap(true);
        verifyCriterionField.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(verifyCriterionField);
        sp.setPreferredSize(new Dimension(400, 100));
        sp.setBorder(new TitledBorder("Success criterion (free-form, used as part of the meta-prompt):"));
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        panel.add(sp, gbc);

        // Row gridy=2 is reserved for the "Generate from prompt!" button, added by
        // ByteBanterBurpExtension.addGenerateVerifyPromptButton() after construction.

        // Sizing widget swaps based on Stateful Interaction state:
        //  - stateless: "Truncate request/response (chars)" spinner
        //  - stateful : "Conversation history depth (turns)" spinner
        final CardLayout sizingCardLayout = new CardLayout();
        final JPanel sizingCard = new JPanel(sizingCardLayout);

        JPanel statelessSubpanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        verifyTruncateCharsSpinner = new JSpinner(new SpinnerNumberModel(4000, 500, 64000, 500));
        statelessSubpanel.add(new JLabel("Truncate request/response (chars):"));
        statelessSubpanel.add(verifyTruncateCharsSpinner);
        sizingCard.add(statelessSubpanel, "stateless");

        JPanel statefulSubpanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        verifyHistoryDepthSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
        statefulSubpanel.add(new JLabel("Conversation history depth (turns):"));
        statefulSubpanel.add(verifyHistoryDepthSpinner);
        sizingCard.add(statefulSubpanel, "stateful");

        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        panel.add(sizingCard, gbc);

        // Wire the swap: ItemListener fires both for user clicks and programmatic
        // setSelected() calls (e.g. loadParams), so the right card is shown after
        // restoring settings too.
        if (statefulCheck != null) {
            statefulCheck.addItemListener(ev ->
                    sizingCardLayout.show(sizingCard,
                            statefulCheck.isSelected() ? "stateful" : "stateless"));
            sizingCardLayout.show(sizingCard,
                    statefulCheck.isSelected() ? "stateful" : "stateless");
        }

        // Toggle enabled state of dependent fields
        verifyEnabledCheck.addActionListener(e -> {
            boolean on = verifyEnabledCheck.isSelected();
            verifyCriterionField.setEnabled(on);
            verifyHistoryDepthSpinner.setEnabled(on);
            verifyTruncateCharsSpinner.setEnabled(on);
        });
        verifyCriterionField.setEnabled(false);
        verifyHistoryDepthSpinner.setEnabled(false);
        verifyTruncateCharsSpinner.setEnabled(false);

        return panel;
    }

    public JTextArea getPromptField() {
        return promptField;
    }

    public JTextArea getVerifyCriterionField() {
        return verifyCriterionField;
    }

    public JPanel getAIConfPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(new TitledBorder("Engine Configuration:"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);

        // --- Model Invocation Parsing (URL & Headers) ---
        urlField = new JTextField("http://localhost:1337/v1/", 40);
        urlField.setBorder(new TitledBorder("URL:"));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        configPanel.add(urlField, gbc);

        headersField = new JTextArea("", 4, 40);
        headersField.setBorder(new TitledBorder("Headers (optional):"));
        JScrollPane headersScrollPane = new JScrollPane(headersField);
        headersScrollPane.setPreferredSize(new Dimension(400, 80));
        gbc.gridy = 1;
        configPanel.add(headersScrollPane, gbc);

        // --- Request Limit Configuration ---
        JPanel limitPanel = buildRequestLimitsPanel();

        // --- Persist Sensitive Data Opt-In (placed above Request Limits to make
        // clear it refers to the headers field above) ---
        JPanel persistPanel = new JPanel(new BorderLayout());
        persistPanel.setBorder(new TitledBorder("Sensitive Data:"));

        persistSensitiveCheck = new JCheckBox("Persist API key and custom headers across sessions");
        persistSensitiveCheck.setSelected(false);
        JPanel checkboxRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        checkboxRow.setOpaque(false);
        checkboxRow.add(persistSensitiveCheck);
        persistPanel.add(checkboxRow, BorderLayout.NORTH);

        JTextArea warning = new JTextArea(
                "Warning: Burp stores extension data in plaintext on disk. "
                        + "Enable only on machines you trust; otherwise re-enter the values each session.");
        warning.setEditable(false);
        warning.setLineWrap(true);
        warning.setWrapStyleWord(true);
        warning.setOpaque(false);
        warning.setFocusable(false);
        warning.setBorder(null);
        warning.setForeground(new Color(0xB00020));
        warning.setFont(warning.getFont().deriveFont(Font.ITALIC));
        persistPanel.add(warning, BorderLayout.CENTER);

        gbc.gridy = 2;
        configPanel.add(persistPanel, gbc);

        gbc.gridy = 3;
        configPanel.add(limitPanel, gbc);

        return configPanel;
    }

    public JPanel getParamPanel() {
        JPanel paramPanel = new JPanel(new GridLayout(6, 1));

        // Max Tokens
        maxTokensSpinner = new JSpinner(new SpinnerNumberModel(512, 1, 4096, 1));
        JPanel maxTokensPanel = new JPanel(new BorderLayout());
        maxTokensPanel.add(new JLabel("Max Tokens:"), BorderLayout.WEST);
        maxTokensPanel.add(maxTokensSpinner, BorderLayout.CENTER);
        paramPanel.add(maxTokensPanel);

        // Temperature
        temperatureSlider = new JSlider(0, 500, 70);
        JLabel tempLabel = new JLabel("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0));
        temperatureSlider.addChangeListener(
                e -> tempLabel.setText("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0)));
        JPanel tempPanel = new JPanel(new BorderLayout());
        tempPanel.add(tempLabel, BorderLayout.NORTH);
        tempPanel.add(temperatureSlider, BorderLayout.CENTER);
        paramPanel.add(tempPanel);

        // Top P
        topPSlider = new JSlider(0, 20, 20);
        JLabel topPLabel = new JLabel("Top P: " + String.format("%.2f", topPSlider.getValue() / 20.0));
        topPSlider.addChangeListener(
                e -> topPLabel.setText("Top P: " + String.format("%.2f", topPSlider.getValue() / 20.0)));
        JPanel topPPanel = new JPanel(new BorderLayout());
        topPPanel.add(topPLabel, BorderLayout.NORTH);
        topPPanel.add(topPSlider, BorderLayout.CENTER);
        paramPanel.add(topPPanel);

        // Frequency Penalty
        frequencyPenaltySlider = new JSlider(0, 20, 0);
        JLabel freqLabel = new JLabel(
                "Frequency Penalty: " + String.format("%.2f", frequencyPenaltySlider.getValue() / 20.0));
        frequencyPenaltySlider.addChangeListener(e -> freqLabel
                .setText("Frequency Penalty: " + String.format("%.2f", frequencyPenaltySlider.getValue() / 20.0)));
        JPanel freqPanel = new JPanel(new BorderLayout());
        freqPanel.add(freqLabel, BorderLayout.NORTH);
        freqPanel.add(frequencyPenaltySlider, BorderLayout.CENTER);
        paramPanel.add(freqPanel);

        // Presence Penalty
        presencePenaltySlider = new JSlider(0, 20, 0);
        JLabel presLabel = new JLabel(
                "Presence Penalty: " + String.format("%.2f", presencePenaltySlider.getValue() / 20.0));
        presencePenaltySlider.addChangeListener(e -> presLabel
                .setText("Presence Penalty: " + String.format("%.2f", presencePenaltySlider.getValue() / 20.0)));
        JPanel presPanel = new JPanel(new BorderLayout());
        presPanel.add(presLabel, BorderLayout.NORTH);
        presPanel.add(presencePenaltySlider, BorderLayout.CENTER);
        paramPanel.add(presPanel);

        return paramPanel;
    }

    public JSONObject getParams() {
        JSONObject params = new JSONObject();
        params.put("prompt", promptField.getText());
        params.put("stateful", statefulCheck.isSelected());
        params.put("regex", regexField.getText());
        params.put("b64", b64Check.isSelected());

        // Context extraction mode + Groovy script (variant of the regex extraction).
        if (extractModeCombo != null) {
            params.put("extract_mode", extractModeCombo.getSelectedIndex() == 1 ? "script" : "regex");
        }
        if (extractScriptField != null) {
            params.put("extract_script", extractScriptField.getText());
        }

        // ASCII smuggling
        if (smuggleEnabledCheck != null) {
            params.put("smuggle_enabled", smuggleEnabledCheck.isSelected());
            params.put("smuggle_mode", (String) smuggleModeCombo.getSelectedItem());
            params.put("smuggle_hidden_text", smuggleHiddenText.getText());
        }

        // New Request Limit params
        if (infiniteRequestCheck != null) {
            params.put("isInfiniteRequests", infiniteRequestCheck.isSelected());
        }
        if (requestLimitField != null) {
            params.put("requestsLimit", requestLimitField.getValue());
        }

        if (persistSensitiveCheck != null) {
            params.put("persist_sensitive", persistSensitiveCheck.isSelected());
        }

        if (verifyEnabledCheck != null) {
            params.put("verify_enabled", verifyEnabledCheck.isSelected());
            params.put("verify_criterion", verifyCriterionField.getText());
            params.put("verify_history_depth", ((Number) verifyHistoryDepthSpinner.getValue()).intValue());
            params.put("verify_truncate_chars", ((Number) verifyTruncateCharsSpinner.getValue()).intValue());
        }

        // Those fields are not mandatory in engines UIs
        if (urlField != null) {
            params.put("URL", urlField.getText());
        }
        if (headersField != null) {
            params.put("headers", headersField.getText());
        }
        if (maxTokensSpinner != null) {
            params.put("max_tokens", maxTokensSpinner.getValue());
        }
        if (temperatureSlider != null) {
            params.put("temperature", temperatureSlider.getValue());
        }
        if (topPSlider != null) {
            params.put("top_p", topPSlider.getValue());
        }
        if (frequencyPenaltySlider != null) {
            params.put("frequency_penalty", frequencyPenaltySlider.getValue());
        }
        if (presencePenaltySlider != null) {
            params.put("presence_penalty", presencePenaltySlider.getValue());
        }

        return params;
    }

    /**
     * Returns the params subset safe to persist to Burp's extension data.
     * If the user has not opted in to persisting sensitive data, custom headers are stripped.
     * Subclasses with engine-specific sensitive fields (e.g. API keys) should override and strip those too.
     */
    public JSONObject getPersistableParams() {
        JSONObject params = getParams();
        if (persistSensitiveCheck != null && !persistSensitiveCheck.isSelected()) {
            params.remove("headers");
        }
        return params;
    }

    public void loadParams(JSONObject params) {
        promptField.setText(params.getString("prompt"));
        statefulCheck.setSelected(params.getBoolean("stateful"));
        regexField.setText(params.getString("regex"));
        b64Check.setSelected(params.getBoolean("b64"));

        if (extractModeCombo != null) {
            // Firing setSelectedIndex re-shows the matching card via the ActionListener.
            extractModeCombo.setSelectedIndex(
                    "script".equals(params.optString("extract_mode", "regex")) ? 1 : 0);
        }
        if (extractScriptField != null) {
            extractScriptField.setText(params.optString("extract_script", DEFAULT_EXTRACT_SCRIPT));
        }

        if (smuggleEnabledCheck != null) {
            smuggleEnabledCheck.setSelected(params.optBoolean("smuggle_enabled", false));
            smuggleModeCombo.setSelectedItem(params.optString("smuggle_mode", "Encode entire payload"));
            smuggleHiddenText.setText(params.optString("smuggle_hidden_text", ""));
            syncSmuggleEnabled();
        }

        if (infiniteRequestCheck != null) {
            infiniteRequestCheck.setSelected(params.optBoolean("isInfiniteRequests", false));
            requestLimitField.setEnabled(!infiniteRequestCheck.isSelected());
        }
        if (requestLimitField != null) {
            requestLimitField.setValue(params.optInt("requestsLimit", 10));
        }

        if (persistSensitiveCheck != null) {
            persistSensitiveCheck.setSelected(params.optBoolean("persist_sensitive", false));
        }

        if (verifyEnabledCheck != null) {
            boolean on = params.optBoolean("verify_enabled", false);
            verifyEnabledCheck.setSelected(on);
            verifyCriterionField.setText(params.optString("verify_criterion", VERIFY_DEFAULT_CRITERION));
            verifyHistoryDepthSpinner.setValue(params.optInt("verify_history_depth", 1));
            verifyTruncateCharsSpinner.setValue(params.optInt("verify_truncate_chars", 4000));
            verifyCriterionField.setEnabled(on);
            verifyHistoryDepthSpinner.setEnabled(on);
            verifyTruncateCharsSpinner.setEnabled(on);
        }

        // Those fields are not mandatory in engines UIs
        if (urlField != null) {
            urlField.setText(params.optString("URL", urlField.getText()));
        }
        if (headersField != null) {
            // Sensitive: may be absent if user opted out of persistence.
            headersField.setText(params.optString("headers", ""));
        }
        if (maxTokensSpinner != null) {
            maxTokensSpinner.setValue(params.getInt("max_tokens"));
        }
        if (temperatureSlider != null) {
            temperatureSlider.setValue(params.getInt("temperature"));
        }
        if (topPSlider != null) {
            topPSlider.setValue(params.getInt("top_p"));
        }
        if (frequencyPenaltySlider != null) {
            frequencyPenaltySlider.setValue(params.getInt("frequency_penalty"));
        }
        if (presencePenaltySlider != null) {
            presencePenaltySlider.setValue(params.getInt("presence_penalty"));
        }
    }
}
