package com.anvilsecure.bytebanter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.handler.HttpResponseReceived;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
 * cost is paid once, not on every Intruder response. The cache is a bounded LRU
 * (see {@link #MAX_CACHED_SCRIPTS}) so iterating on a script does not grow it
 * without limit. Each invocation creates a fresh {@link Script} instance, so
 * concurrent Intruder threads do not share script state.</p>
 *
 * <p>The {@code request} and {@code response} full-message serializations are
 * built lazily (only when the script actually reads those variables), so a
 * script that only touches {@code body}/{@code status}/{@code headers} does not
 * pay to stringify the entire request and response on every response.</p>
 *
 * <p>{@link #shutdown()} must be called from the extension's unload handler:
 * {@link GroovyClassLoader} is {@link java.io.Closeable} and would otherwise
 * leak its whole classloader hierarchy (and every compiled class) into
 * Metaspace on each extension reload cycle.</p>
 */
public final class ResponseScriptRunner {

    /** Upper bound on distinct compiled scripts retained; least-recently-used are evicted. */
    private static final int MAX_CACHED_SCRIPTS = 64;

    private static final GroovyClassLoader CLASS_LOADER =
            new GroovyClassLoader(ResponseScriptRunner.class.getClassLoader());

    // Access-ordered LinkedHashMap (LRU) wrapped for thread-safety. Bounded so a user
    // iterating on a script during a session cannot grow it without limit.
    private static final Map<String, Class<?>> COMPILED_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Class<?>>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Class<?>> eldest) {
                    return size() > MAX_CACHED_SCRIPTS;
                }
            });

    private ResponseScriptRunner() {
    }

    /**
     * Closes the shared {@link GroovyClassLoader} and clears the compiled-class cache.
     * Invoked from {@code ByteBanterBurpExtension.extensionUnloaded()} to avoid leaking
     * the classloader hierarchy and its compiled classes into Metaspace across reloads.
     */
    public static void shutdown() {
        COMPILED_CACHE.clear();
        try {
            CLASS_LOADER.close();
        } catch (IOException ignored) {
            // Best-effort cleanup on unload; nothing actionable if close() fails.
        }
    }

    public static String run(String scriptSource, HttpResponseReceived r, MontoyaApi api) {
        // request/response are passed as suppliers so their (potentially large) full-message
        // serialization is only performed if the script reads the 'request'/'response' vars.
        return evaluateLazy(scriptSource,
                r.bodyToString(),
                r.statusCode(),
                headersToMap(r),
                () -> r.initiatingRequest().toString(),
                () -> r.toString(),
                msg -> api.logging().logToError(msg));
    }

    /**
     * Pure evaluation core, decoupled from the Montoya API so it is unit-testable.
     * Returns the extracted String, or {@code null} on a null result, blank source,
     * or any exception (which is reported via {@code errorLog}). {@code request} and
     * {@code response} are eager here; per-response callers should prefer {@link #run}.
     */
    public static String evaluate(String scriptSource, String body, int status, Map<String, String> headers,
                                  String request, String response, Consumer<String> errorLog) {
        return evaluateLazy(scriptSource, body, status, headers, () -> request, () -> response, errorLog);
    }

    /**
     * Evaluation core with lazily-supplied {@code request}/{@code response}. The suppliers
     * are invoked at most once, only when the script first reads the corresponding variable.
     */
    static String evaluateLazy(String scriptSource, String body, int status, Map<String, String> headers,
                               Supplier<String> request, Supplier<String> response, Consumer<String> errorLog) {
        if (scriptSource == null || scriptSource.isBlank()) {
            return null;
        }
        try {
            Class<?> scriptClass = compile(scriptSource);

            LazyBinding binding = new LazyBinding();
            binding.setVariable("body", body);
            binding.setVariable("status", status);
            binding.setVariable("headers", headers);
            binding.setLazyVariable("request", request);
            binding.setLazyVariable("response", response);

            Script script = InvokerHelper.createScript(scriptClass, binding);
            Object result = script.run();
            return result == null ? null : result.toString();
        } catch (Throwable t) {
            errorLog.accept("[ByteBanter] Response script failed: " + t);
            return null;
        }
    }

    /** Returns the cached compiled class for a source, compiling (outside the cache lock) on a miss. */
    private static Class<?> compile(String scriptSource) {
        Class<?> scriptClass = COMPILED_CACHE.get(scriptSource);
        if (scriptClass == null) {
            // Compile outside the lock so a ~100ms parse does not block other lookups.
            // A rare concurrent double-compile is harmless (last put wins).
            scriptClass = CLASS_LOADER.parseClass(scriptSource, "ByteBanterResponseScript.groovy");
            COMPILED_CACHE.put(scriptSource, scriptClass);
        }
        return scriptClass;
    }

    /**
     * A {@link Binding} whose {@code request}/{@code response} variables are materialised
     * from a {@link Supplier} on first read, so the expensive full-message serialization is
     * deferred until (and unless) the script actually reads them.
     */
    private static final class LazyBinding extends Binding {
        private final Map<String, Supplier<String>> lazy = new HashMap<>();

        void setLazyVariable(String name, Supplier<String> supplier) {
            lazy.put(name, supplier);
        }

        @Override
        public Object getVariable(String name) {
            if (lazy.containsKey(name) && !getVariables().containsKey(name)) {
                setVariable(name, lazy.get(name).get());
            }
            return super.getVariable(name);
        }

        @Override
        public boolean hasVariable(String name) {
            return lazy.containsKey(name) || super.hasVariable(name);
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
