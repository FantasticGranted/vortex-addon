package me.aleksilassila.litematica.printer.core.action;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeEpoch;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record ActionRequest(
        String owner,
        RuntimeEpoch epoch,
        Set<ResourceLease> resources,
        long deadlineNanos,
        ConfirmationPolicy confirmation,
        RetryPolicy retry
) {
    public ActionRequest {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(epoch, "epoch");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(confirmation, "confirmation");
        Objects.requireNonNull(retry, "retry");
        if (owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        resources = resources.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(resources));
    }
}
