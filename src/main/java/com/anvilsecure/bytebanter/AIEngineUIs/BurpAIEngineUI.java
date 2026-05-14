package com.anvilsecure.bytebanter.AIEngineUIs;

import com.anvilsecure.bytebanter.AIEngines.AIEngine;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.text.NumberFormat;

public class BurpAIEngineUI extends AIEngineUI {

    public BurpAIEngineUI(AIEngine engine) {
        super(engine);
    }

    @Override
    public JPanel getAIConfPanel() {
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(new TitledBorder("Engine Configuration:"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 2, 2);

        // --- Request Limit Configuration ---
        JPanel limitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        limitPanel.setBorder(new TitledBorder("Request Limits:"));

        infiniteRequestCheck = new JCheckBox("Infinite Requests");
        limitPanel.add(infiniteRequestCheck);

        // Create formatter for request limit
        NumberFormat format = NumberFormat.getIntegerInstance();
        format.setGroupingUsed(false);
        NumberFormatter formatter = new NumberFormatter(format);
        formatter.setValueClass(Integer.class);
        formatter.setMinimum(1);
        formatter.setMaximum(1000000);
        formatter.setAllowsInvalid(false);
        formatter.setCommitsOnValidEdit(true);

        requestLimitField = new JFormattedTextField(formatter);
        requestLimitField.setValue(10); // default
        requestLimitField.setColumns(8);
        limitPanel.add(new JLabel("Limit:"));
        limitPanel.add(requestLimitField);

        infiniteRequestCheck.addActionListener(e -> {
            requestLimitField.setEnabled(!infiniteRequestCheck.isSelected());
        });

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        configPanel.add(limitPanel, gbc);

        return configPanel;
    }

    @Override
    public JPanel getParamPanel() {
        JPanel paramPanel = new JPanel(new GridLayout(6, 1));
        temperatureSlider = new JSlider(1, 200, 50);

        JLabel tempLabel = new JLabel("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0));
        temperatureSlider.addChangeListener(
                e -> tempLabel.setText("Temperature: " + String.format("%.2f", temperatureSlider.getValue() / 100.0)));

        JPanel tempPanel = new JPanel(new BorderLayout());
        tempPanel.add(tempLabel, BorderLayout.NORTH);
        tempPanel.add(temperatureSlider, BorderLayout.CENTER);

        paramPanel.add(tempPanel);
        return paramPanel;
    }
}
