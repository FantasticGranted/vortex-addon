package me.aleksilassila.litematica.printer.handler.handlers.print;

enum StripLogTaskStage {
    PLACE_LOG,
    WAIT_LOG_CONFIRM,
    STRIP_LOG,
    WAIT_STRIP_CONFIRM,
    RETRY_WAIT,
    COMPLETE;

    boolean waitsForWorldUpdate() {
        return this == WAIT_LOG_CONFIRM || this == WAIT_STRIP_CONFIRM;
    }
}
