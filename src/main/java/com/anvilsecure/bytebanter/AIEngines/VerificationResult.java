package com.anvilsecure.bytebanter.AIEngines;

import org.json.JSONObject;

public final class VerificationResult {

    private static final VerificationResult FAILED = new VerificationResult(false, "");

    public final boolean success;
    public final String strategy;

    private VerificationResult(boolean success, String strategy) {
        this.success = success;
        this.strategy = strategy == null ? "" : strategy;
    }

    public static VerificationResult failed() {
        return FAILED;
    }

    public static VerificationResult parse(String raw) {
        if (raw == null) {
            return failed();
        }
        try {
            String stripped = raw.trim();
            if (stripped.startsWith("```json")) {
                stripped = stripped.substring("```json".length()).trim();
            } else if (stripped.startsWith("```")) {
                stripped = stripped.substring(3).trim();
            }
            if (stripped.endsWith("```")) {
                stripped = stripped.substring(0, stripped.length() - 3).trim();
            }
            int start = stripped.indexOf('{');
            int end = stripped.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return failed();
            }
            JSONObject obj = new JSONObject(stripped.substring(start, end + 1));
            return new VerificationResult(
                    obj.optBoolean("success", false),
                    obj.optString("strategy", ""));
        } catch (Throwable t) {
            return failed();
        }
    }
}
