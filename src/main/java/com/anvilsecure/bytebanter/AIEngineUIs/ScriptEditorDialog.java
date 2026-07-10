package com.anvilsecure.bytebanter.AIEngineUIs;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.anvilsecure.bytebanter.AIEngines.AIEngine;
import com.anvilsecure.bytebanter.ResponseScriptRunner;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modal pop-up editor for the Groovy response-extraction script. Provides:
 * <ul>
 *   <li>a syntax-highlighted Groovy editor (RSyntaxTextArea),</li>
 *   <li>a "sample response" pane (pasted or loaded from a saved file),</li>
 *   <li><b>Test</b> — runs the script against the sample response and shows the result,</li>
 *   <li><b>Generate from response (AI)</b> — asks the configured engine to write a script
 *       from the sample response.</li>
 * </ul>
 * On OK, {@link #isConfirmed()} is true and {@link #getScript()} returns the edited script.
 */
public class ScriptEditorDialog extends JDialog {

    private static final String GEN_SYSTEM_PROMPT =
            "You are an expert in Burp Suite and the Groovy language. The user gives you an\n"
                    + "example HTTP response captured during an attack. Write a Groovy script that\n"
                    + "extracts, from such a response, the single most relevant value to feed back into\n"
                    + "a multi-turn attack conversation (e.g. the assistant's answer, a token, a flag,\n"
                    + "an error detail).\n"
                    + "\n"
                    + "The script runs with these variables in scope:\n"
                    + "  body     - the response body (String)\n"
                    + "  status   - the HTTP status code (int)\n"
                    + "  headers  - response headers as a Map<String,String>\n"
                    + "  request  - the full initiating request (String)\n"
                    + "  response - the full raw response (String)\n"
                    + "\n"
                    + "The value of the LAST expression is the result: it must be the extracted String,\n"
                    + "or null when nothing relevant is present. Prefer matching against `body`. Keep it\n"
                    + "short and robust (use a regex with a capture group when the value is embedded in\n"
                    + "JSON or HTML).\n"
                    + "\n"
                    + "Output ONLY the Groovy script. No markdown fences, no prose.";

    private final transient AIEngine engine;
    private final String goalPrompt;

    // JTextArea, not RSyntaxTextArea: newGroovyEditor() may return a plain JTextArea fallback
    // if the highlighted editor cannot accept keyboard input in the current environment.
    private final JTextArea scriptArea;
    private final JTextArea responseArea;
    private final JTextArea outputArea;
    private final JButton generateButton;
    private final JButton testButton;

    private boolean confirmed = false;

    public ScriptEditorDialog(Window owner, String initialScript, AIEngine engine, String goalPrompt,
                              String initialResponse) {
        super(owner, "Response extraction script (Groovy)", ModalityType.APPLICATION_MODAL);
        this.engine = engine;
        this.goalPrompt = goalPrompt == null ? "" : goalPrompt;

        GroovyEditor ed = newGroovyEditor(20, 70);
        scriptArea = ed.area;
        scriptArea.setText(initialScript == null ? "" : initialScript);
        JComponent scriptScroll = ed.scroll;
        scriptScroll.setBorder(new TitledBorder("Groovy script (last expression = extracted value, or null):"));

        responseArea = new JTextArea(12, 40);
        responseArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        responseArea.setLineWrap(false);
        if (initialResponse != null && !initialResponse.isEmpty()) {
            responseArea.setText(initialResponse);
            responseArea.setCaretPosition(0);
        }
        JScrollPane responseScroll = new JScrollPane(responseArea);
        responseScroll.setBorder(new TitledBorder("Sample response (paste a raw HTTP response or a body):"));

        outputArea = new JTextArea(12, 30);
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(new TitledBorder("Test output:"));

        // Toolbar
        JButton loadButton = new JButton("Load response from file...");
        loadButton.addActionListener(e -> loadResponseFromFile());
        generateButton = new JButton("Generate from response (AI)");
        generateButton.addActionListener(e -> generateFromResponse());
        testButton = new JButton("Test");
        testButton.addActionListener(e -> testScript());
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(loadButton);
        toolbar.add(generateButton);
        toolbar.add(testButton);

        // Lower area: response input | test output
        JSplitPane lower = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, responseScroll, outputScroll);
        lower.setResizeWeight(0.6);

        JSplitPane center = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scriptScroll, lower);
        center.setResizeWeight(0.55);

        // OK / Cancel
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okButton);
        buttons.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        content.add(toolbar, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        setSize(980, 720);
        setLocationRelativeTo(owner);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getScript() {
        return scriptArea.getText();
    }

    /** Current sample-response text, so the caller can remember it across editor opens. */
    public String getSampleResponse() {
        return responseArea.getText();
    }

    private void loadResponseFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load saved response");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            responseArea.setText(normalizeToRawResponse(Files.readString(chooser.getSelectedFile().toPath())));
            responseArea.setCaretPosition(0);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not read file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void testScript() {
        String raw = normalizeToRawResponse(responseArea.getText());
        if (raw == null || raw.isBlank()) {
            outputArea.setText("Paste a sample response first.");
            return;
        }
        // If the input was a Burp export, show the extracted raw response so what is
        // tested matches what is displayed.
        if (!raw.equals(responseArea.getText())) {
            responseArea.setText(raw);
            responseArea.setCaretPosition(0);
        }
        Parsed p = parseResponse(raw);
        StringBuilder log = new StringBuilder();
        String result = ResponseScriptRunner.evaluate(scriptArea.getText(),
                p.body, p.status, p.headers, "", raw, log::append);

        StringBuilder sb = new StringBuilder();
        sb.append("Parsed: status=").append(p.status)
                .append(", headers=").append(p.headers.size())
                .append(", body=").append(p.body == null ? 0 : p.body.length()).append(" chars\n");
        if (log.length() > 0) {
            sb.append("\n").append(log).append("\n");
        }
        sb.append("\nResult:\n");
        sb.append(result == null ? "(null — nothing extracted; this response would be skipped)" : result);
        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private void generateFromResponse() {
        String raw = normalizeToRawResponse(responseArea.getText());
        if (raw == null || raw.isBlank()) {
            outputArea.setText("Paste a sample response first, then generate.");
            return;
        }
        generateButton.setEnabled(false);
        testButton.setEnabled(false);
        outputArea.setText("Generating script from the sample response...");

        StringBuilder userInput = new StringBuilder();
        if (!goalPrompt.isBlank()) {
            userInput.append("Attack goal (for context):\n").append(goalPrompt).append("\n\n");
        }
        userInput.append("Example HTTP response:\n").append(raw);

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return engine.askAi(GEN_SYSTEM_PROMPT, userInput.toString());
            }

            @Override
            protected void done() {
                generateButton.setEnabled(true);
                testButton.setEnabled(true);
                try {
                    String script = stripCodeFences(get());
                    if (script != null && !script.isBlank()) {
                        scriptArea.setText(script.strip());
                        scriptArea.setCaretPosition(0);
                        outputArea.setText("Script generated. Review it, then click Test.");
                    } else {
                        // Null/empty result (e.g. Burp AI disabled): leave the script untouched.
                        outputArea.setText("No script was generated (is Burp AI enabled?). "
                                + "The existing script was left unchanged.");
                    }
                } catch (Exception ex) {
                    outputArea.setText("Error generating script: " + ex.getMessage()
                            + "\nCheck Burp's event log for details.");
                }
            }
        };
        worker.execute();
    }

    /** Strips a leading/trailing markdown code fence (```groovy ... ```) if the model added one. */
    private static String stripCodeFences(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl >= 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t;
    }

    private static final Pattern RESPONSE_TAG =
            Pattern.compile("<response\\b([^>]*)>(.*?)</response>", Pattern.DOTALL);
    private static final Pattern CDATA =
            Pattern.compile("<!\\[CDATA\\[(.*?)\\]\\]>", Pattern.DOTALL);

    /**
     * If the text is a Burp "Save item(s)" / history XML export, extracts the first
     * {@code <response>} element (Base64-decoding it when {@code base64="true"}) and
     * returns the raw HTTP response. Otherwise returns the text unchanged. Idempotent,
     * so it is safe to call on already-raw responses.
     */
    static String normalizeToRawResponse(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.stripLeading();
        boolean looksLikeExport = trimmed.startsWith("<?xml")
                || trimmed.startsWith("<items")
                || text.contains("<response");
        if (!looksLikeExport) {
            return text;
        }
        Matcher m = RESPONSE_TAG.matcher(text);
        if (!m.find()) {
            return text;
        }
        String attrs = m.group(1);
        String inner = m.group(2).trim();
        Matcher c = CDATA.matcher(inner);
        if (c.find()) {
            inner = c.group(1);
        }
        boolean base64 = attrs.contains("base64=\"true\"") || attrs.contains("base64='true'");
        if (base64) {
            try {
                byte[] decoded = Base64.getMimeDecoder().decode(inner.replaceAll("\\s", ""));
                // ISO-8859-1 is byte-preserving, so a later re-parse sees the exact bytes.
                return new String(decoded, StandardCharsets.ISO_8859_1);
            } catch (IllegalArgumentException ex) {
                return text;
            }
        }
        return inner;
    }

    /** Parses a pasted raw HTTP response; falls back to treating the whole text as a body. */
    private static Parsed parseResponse(String raw) {
        try {
            HttpResponse resp = HttpResponse.httpResponse(raw);
            List<HttpHeader> headerList = resp.headers();
            String body = resp.bodyToString();
            short status = resp.statusCode();
            if (status == 0 && (headerList == null || headerList.isEmpty()) && (body == null || body.isBlank())) {
                return new Parsed(raw, 0, new LinkedHashMap<>());
            }
            Map<String, String> headers = new LinkedHashMap<>();
            if (headerList != null) {
                for (HttpHeader h : headerList) {
                    headers.put(h.name(), h.value());
                }
            }
            return new Parsed(body, status, headers);
        } catch (Exception ex) {
            return new Parsed(raw, 0, new LinkedHashMap<>());
        }
    }

    /**
     * A working Groovy editor plus its scroll pane. {@code area} is an RSyntaxTextArea
     * (highlighted) when keyboard input works in this environment, otherwise a plain
     * editable JTextArea fallback — either way callers use it through the JTextArea API.
     */
    public static final class GroovyEditor {
        public final JTextArea area;
        public final JComponent scroll;
        public final boolean highlighted;

        GroovyEditor(JTextArea area, JComponent scroll, boolean highlighted) {
            this.area = area;
            this.scroll = scroll;
            this.highlighted = highlighted;
        }
    }

    /**
     * Builds a Groovy editor that is guaranteed to accept keyboard input. Prefers a
     * highlighted RSyntaxTextArea; if its editing actions turn out to belong to a foreign
     * classloader (see {@link #typingIsOurs}), falls back to a plain JTextArea so the user
     * can always type. Must be called on the EDT.
     */
    static GroovyEditor newGroovyEditor(int rows, int cols) {
        try {
            RSyntaxTextArea area = newGroovyArea(rows, cols);
            if (typingIsOurs(area)) {
                return new GroovyEditor(area, new RTextScrollPane(area), true);
            }
        } catch (Throwable ignored) {
            // fall through to the plain-JTextArea fallback
        }
        JTextArea plain = new JTextArea(rows, cols);
        plain.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        plain.setTabSize(2);
        plain.setEditable(true);
        plain.setEnabled(true);
        return new GroovyEditor(plain, new JScrollPane(plain), false);
    }

    /** Creates a Groovy-highlighted editor area themed to match Burp's light/dark look. */
    static RSyntaxTextArea newGroovyArea(int rows, int cols) {
        // See resetSharedKeymaps(): clear the JVM-global keyboard tables before AND after
        // construction so this area builds/keeps its own maps and Burp's own editors rebuild
        // theirs instead of inheriting ours.
        resetSharedKeymaps();
        RSyntaxTextArea area = new RSyntaxTextArea(rows, cols);
        area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_GROOVY);
        area.setCodeFoldingEnabled(true);
        area.setHighlightCurrentLine(true);
        area.setEditable(true);
        area.setEnabled(true);
        area.setTabSize(2);
        applyThemeFromLaf(area);
        resetSharedKeymaps();
        return area;
    }

    /**
     * RSyntaxTextArea caches its keyboard machinery (Keymap / InputMap / ActionMap) in
     * JVM-global, JDK-owned tables keyed by hard-coded string literals. Burp Suite bundles
     * its own RSyntaxTextArea and seeds those tables first with actions bound to Burp's
     * classloader; our (shaded) copy would then reuse them, and because
     * {@code RecordableTextAction} only acts when the focused component is an instance of
     * <em>its own</em> RTextArea class, every keystroke on our differently-classloaded
     * component becomes a silent no-op (the editor looks editable but accepts nothing).
     * Shade relocation does not help — the keys are string literals, and JTextComponent /
     * UIManager are single JVM-global JDK classes shared across classloaders. Clearing the
     * entries forces the next RSyntaxTextArea to build fresh maps in the current classloader.
     */
    private static void resetSharedKeymaps() {
        JTextComponent.removeKeymap("RTextAreaKeymap");
        UIManager.put("RTextAreaUI.actionMap", null);
        UIManager.put("RTextAreaUI.inputMap", null);
        UIManager.put("RSyntaxTextAreaUI.actionMap", null);
        UIManager.put("RSyntaxTextAreaUI.inputMap", null);
    }

    /**
     * True only if this area's default key-typed action and core editing actions come from
     * OUR classloader — i.e. the keymap reset worked and keystrokes will actually edit.
     * A foreign classloader means the shared-map collision persists and we must fall back.
     */
    private static boolean typingIsOurs(RSyntaxTextArea area) {
        ClassLoader ours = RSyntaxTextArea.class.getClassLoader();
        javax.swing.text.Keymap km = area.getKeymap();
        if (km != null && km.getDefaultAction() != null
                && km.getDefaultAction().getClass().getClassLoader() != ours) {
            return false;
        }
        ActionMap am = area.getActionMap();
        for (Object key : new Object[] { DefaultEditorKit.insertContentAction,
                DefaultEditorKit.insertBreakAction, DefaultEditorKit.deletePrevCharAction }) {
            Action act = (am == null) ? null : am.get(key);
            if (act != null && act.getClass().getClassLoader() != ours) {
                return false;
            }
        }
        return true;
    }

    /** Applies the RSyntaxTextArea dark or default theme based on the current L&amp;F background. */
    static void applyThemeFromLaf(RSyntaxTextArea area) {
        Color bg = UIManager.getColor("Panel.background");
        boolean dark = bg != null
                && (0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue()) < 128;
        // Resolve relative to the RSyntaxTextArea class so the path follows the shaded/relocated
        // package (avoids a hard-coded "/org/fife/..." string that relocation would not rewrite).
        String themeName = (dark ? "dark.xml" : "default.xml");
        try (InputStream in = RSyntaxTextArea.class.getResourceAsStream("themes/" + themeName)) {
            if (in != null) {
                Theme.load(in).apply(area);
            }
        } catch (IOException ignored) {
            // Non-fatal: fall back to RSyntaxTextArea's built-in default colors.
        }
    }

    /** Simple parsed-response holder. */
    private static final class Parsed {
        final String body;
        final int status;
        final Map<String, String> headers;

        Parsed(String body, int status, Map<String, String> headers) {
            this.body = body;
            this.status = status;
            this.headers = headers;
        }
    }
}
