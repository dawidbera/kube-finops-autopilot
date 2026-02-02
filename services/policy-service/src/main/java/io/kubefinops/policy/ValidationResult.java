package io.kubefinops.policy;

import lombok.Value;

@Value
public class ValidationResult {
    boolean valid;
    String reason;
    String policyName;

    /**
     * Creates a successful validation result.
     *
     * @return A valid ValidationResult.
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, null, null);
    }

    /**
     * Creates a failed validation result with a reason.
     *
     * @param reason     The reason for failure.
     * @param policyName The name of the policy that was violated.
     * @return An invalid ValidationResult.
     */
    public static ValidationResult invalid(String reason, String policyName) {
        return new ValidationResult(false, reason, policyName);
    }
}
