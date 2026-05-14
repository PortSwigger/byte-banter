package com.anvilsecure.bytebanter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.intruder.GeneratedPayload;
import burp.api.montoya.intruder.IntruderInsertionPoint;
import burp.api.montoya.intruder.PayloadGenerator;
import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import com.anvilsecure.bytebanter.AIEngines.AnthropicAIEngine;
import com.anvilsecure.bytebanter.AIEngines.BurpAIEngine;
import com.anvilsecure.bytebanter.AIEngines.ClaudeCodeEngine;
import com.anvilsecure.bytebanter.AIEngines.OllamaAIEngine;
import com.anvilsecure.bytebanter.AIEngines.OpenAIEngine;

import java.util.ArrayList;
import java.util.List;


public class ByteBanterPayloadGenerator implements PayloadGenerator {

    private final List<AIEngine> engines;
    private AIEngine engine;

    public ByteBanterPayloadGenerator(MontoyaApi api) {
        engines = new ArrayList<>();
        // BurpAI is the default provider (BApp Store requirement)
        engines.add(new BurpAIEngine(api));
        engines.add(new OllamaAIEngine(api));
        engines.add(new OpenAIEngine(api));
        engines.add(new AnthropicAIEngine(api));
        // Claude Code (CLI) — uses the user's existing Anthropic subscription via
        // the Claude Code agent. Spawns a local subprocess; not BApp-Store-shaped.
        engines.add(new ClaudeCodeEngine(api));
    }

    public void setEngine(int index) {
        engine = engines.get(index);
    }

    public AIEngine getEngine() {
        return engine;
    }

    public List<AIEngine> getEngines() {
        return engines;
    }

    public String[] getEnginesNames() {
        String[] names = new String[engines.size()];
        for (int i = 0; i < engines.size(); i++) {
            names[i] = engines.get(i).getName();
        }
        return names;
    }

    @Override
    public GeneratedPayload generatePayloadFor(IntruderInsertionPoint intruderInsertionPoint) {
        try {
            String payload = engine.askAi();
            // Treat both null and empty/whitespace-only as end-of-generation:
            // an attack stop can leave a subprocess engine (e.g. Claude Code CLI) returning
            // an empty stdout, and Burp NPEs internally on a payload whose value is empty.
            if (payload == null || payload.isBlank()) {
                return GeneratedPayload.end();
            }
            return GeneratedPayload.payload(payload);
        } catch (Throwable t) {
            // Never propagate an exception from askAi up into Intruder.
            return GeneratedPayload.end();
        }
    }

    /**
     * Called by Intruder once per attack. Resets the engine's conversation
     * state so context from a previous attack does not leak into the new one.
     */
    public void resetForNewAttack() {
        if (engine != null) {
            engine.resetConversation();
        }
    }
}
