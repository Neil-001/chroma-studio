package dev.chromastudio.core.palette;

import java.util.List;

public final class UnreadablePalette extends IllegalArgumentException {
    public enum Reason {
        NO_SHARE,
        OTHER_VERSION,
        NOT_SHARE,
        EMPTY_ENTRY,
        OPEN_BRACKET,
        BAD_PROPERTIES,
        NOT_BLOCK,
        BAD_WEIGHT,
        NOT_JSON,
        NOT_PALETTE,
        NO_FORMAT,
        OTHER_FORMAT
    }

    private static final long serialVersionUID = 1;

    private final Reason reason;
    private final String[] args;

    UnreadablePalette(Reason reason, String... args) {
        this(reason, null, args);
    }

    UnreadablePalette(Reason reason, Throwable cause, String... args) {
        super(reason + " " + List.of(args), cause);
        this.reason = reason;
        this.args = args.clone();
    }

    public Reason reason() { return reason; }

    public List<String> args() { return List.of(args); }
}
