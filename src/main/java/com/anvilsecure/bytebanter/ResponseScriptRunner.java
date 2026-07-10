package com.anvilsecure.bytebanter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.handler.HttpResponseReceived;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Runs a small user-supplied Groovy script against an HTTP response to extract
 * the string that should be fed back into a stateful ByteBanter conversation.
 * This is the scriptable variant of the "Context Regex" feature.
 *
 * <p>The script is evaluated with the following variables in scope:</p>
 * <ul>
 *   <li>{@code body}     — the response body as a String</li>
 *   <li>{@code status}   — the HTTP status code (int)</li>
 *   <li>{@code headers}  — response headers as a {@code Map<String,String>} (last value wins)</li>
 *   <li>{@code request}  — the full initiating request as a String</li>
 *   <li>{@code response} — the full response as a String</li>
 * </ul>
 *
 * <p>The value of the last expression is the return value: return the extracted
 * String, or {@code null} to skip injecting anything for this response. Any
 * exception (compilation or runtime) is logged and swallowed — it returns
 * {@code null} so an Intruder attack is never interrupted by a bad script.</p>
 *
 * <p>Compiled scripts are cached by source so the ~100ms Groovy compilation
 * cost is paid once, not on every Intruder response. Each invocation creates a
 * fresh {@link Script} instance, so concurrent Intruder threads do not share
 * script state.</p>
 */
public final class ResponseScriptRunner {

    private static final GroovyClassLoader CLASS_LOADER =
            new GroovyClassLoader(ResponseScriptRunner.class.getClassLoader());

    private static final Map<String, Class<?>> COMPILED_CACHE = new ConcurrentHashMap<>();

    private ResponseScriptRunner() {
    }

    public static String run(String scriptSource, HttpResponseReceived r, MontoyaApi api) {
        return evaluate(scriptSource,
                r.bodyToString(),
                r.statusCode(),
                headersToMap(r),
                r.initiatingRequest().toString(),
                r.toString(),
                msg -> api.logging().logToError(msg));
    }

    /**
     * Pure evaluation core, decoupled from the Montoya API so it is unit-testable.
     * Returns the extracted String, or {@code null} on a null result, blank source,
     * or any exception (which is reported via {@code errorLog}).
     */
    public static String evaluate(String scriptSource, String body, int status, Map<String, String> headers,
                                  String request, String response, Consumer<String> errorLog) {
        if (scriptSource == null || scriptSource.isBlank()) {
            return null;
        }
        try {
            Class<?> scriptClass = COMPILED_CACHE.computeIfAbsent(scriptSource,
                    src -> CLASS_LOADER.parseClass(src, "ByteBanterResponseScript.groovy"));

            Binding binding = new Binding();
            binding.setVariable("body", body);
            binding.setVariable("status", status);
            binding.setVariable("headers", headers);
            binding.setVariable("request", request);
            binding.setVariable("response", response);

            Script script = InvokerHelper.createScript(scriptClass, binding);
            Object result = script.run();
            return result == null ? null : result.toString();
        } catch (Throwable t) {
            errorLog.accept("[ByteBanter] Response script failed: " + t);
            return null;
        }
    }

    /** Flattens response headers into a name-&gt;value map (last value wins on duplicates). */
    private static Map<String, String> headersToMap(HttpResponseReceived r) {
        Map<String, String> map = new LinkedHashMap<>();
        for (HttpHeader h : r.headers()) {
            map.put(h.name(), h.value());
        }
        return map;
    }
}
