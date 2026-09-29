package me.aleksilassila.litematica.printer.core.action;

public record RetryPolicy(int maxAttempts, int backoffTicks) {
    public static final RetryPolicy NONE = new RetryPolicy(1, 0);

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (backoffTicks < 0) {
            throw new IllegalArgumentException("backoffTicks must not be negative");
        }
    }
}
