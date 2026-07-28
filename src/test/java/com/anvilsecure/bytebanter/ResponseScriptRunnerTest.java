package com.anvilsecure.bytebanter;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseScriptRunnerTest {

    private static final Map<String, String> NO_HEADERS = Map.of();

    @Test
    void extractsCaptureGroupFromBody() {
        String script = "def m = (body =~ /\"answer\":\"([^\"]*)\"/); m ? m[0][1] : null";
        String out = ResponseScriptRunner.evaluate(script,
                "{\"answer\":\"s3cret\",\"defender\":true}", 200, NO_HEADERS, "req", "resp", msg -> {});
        assertEquals("s3cret", out);
    }

    @Test
    void canUseStatusAndHeaders() {
        String script = "status == 200 && headers['X-Flag'] == 'yes' ? 'hit' : null";
        String out = ResponseScriptRunner.evaluate(script,
                "body", 200, Map.of("X-Flag", "yes"), "req", "resp", msg -> {});
        assertEquals("hit", out);
    }

    @Test
    void nullResultSkips() {
        String out = ResponseScriptRunner.evaluate("null", "body", 200, NO_HEADERS, "req", "resp", msg -> {});
        assertNull(out);
    }

    @Test
    void exceptionIsSwallowedAndLogged() {
        AtomicReference<String> logged = new AtomicReference<>();
        String out = ResponseScriptRunner.evaluate("throw new RuntimeException('boom')",
                "body", 200, NO_HEADERS, "req", "resp", logged::set);
        assertNull(out);
        assertNotNull(logged.get());
    }

    @Test
    void blankScriptReturnsNull() {
        assertNull(ResponseScriptRunner.evaluate("   ", "body", 200, NO_HEADERS, "req", "resp", msg -> {}));
    }

    @Test
    void requestAndResponseAreSerializedLazily() {
        // A script that only touches body/status/headers must not trigger the (potentially
        // expensive) request/response serialization suppliers.
        AtomicInteger requestCalls = new AtomicInteger();
        AtomicInteger responseCalls = new AtomicInteger();
        String out = ResponseScriptRunner.evaluateLazy(
                "status == 200 ? 'ok' : null",
                "body", 200, NO_HEADERS,
                () -> { requestCalls.incrementAndGet(); return "req"; },
                () -> { responseCalls.incrementAndGet(); return "resp"; },
                msg -> {});
        assertEquals("ok", out);
        assertEquals(0, requestCalls.get(), "request supplier should not run when unused");
        assertEquals(0, responseCalls.get(), "response supplier should not run when unused");
    }

    @Test
    void responseSupplierRunsOnceWhenAccessed() {
        // When the script reads 'response', the supplier is invoked exactly once (result cached).
        AtomicInteger responseCalls = new AtomicInteger();
        String out = ResponseScriptRunner.evaluateLazy(
                "response.length() + '/' + response.length()",
                "body", 200, NO_HEADERS,
                () -> "req",
                () -> { responseCalls.incrementAndGet(); return "abc"; },
                msg -> {});
        assertTrue(out.startsWith("3/3"));
        assertEquals(1, responseCalls.get(), "response supplier should be invoked exactly once");
    }
}
