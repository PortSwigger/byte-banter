package com.anvilsecure.bytebanter;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
