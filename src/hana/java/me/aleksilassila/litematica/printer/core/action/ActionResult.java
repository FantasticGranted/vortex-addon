package me.aleksilassila.litematica.printer.core.action;

public enum ActionResult {
    ADMITTED,
    WAITING_RESOURCE,
    SENT,
    WAITING_CONFIRMATION,
    CONFIRMED,
    RETRY,
    STALE,
    FAILED
}
