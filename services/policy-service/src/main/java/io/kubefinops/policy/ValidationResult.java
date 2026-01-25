package io.kubefinops.policy;

import lombok.Value;

@Value
public class ValidationResult {
    boolean valid;
    String reason;
    String policyName;

    public static ValidationResult valid() {
        return new ValidationResult(true, null, null);
    }

    public static ValidationResult invalid(String reason, String policyName) {
        return new ValidationResult(false, reason, policyName);
    }
}
