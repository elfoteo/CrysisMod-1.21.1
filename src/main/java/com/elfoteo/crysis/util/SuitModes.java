package com.elfoteo.crysis.util;

public enum SuitModes {
    NOT_EQUIPPED(0),
    ARMOR(1),
    CLOAK(2),
    VISOR(3);

    private final int value;

    SuitModes(int value) {
        this.value = value;
    }

    public int get() {
        return value;
    }

    public static SuitModes from(int value) {
        for (SuitModes mode : values()) {
            if (mode.get() == value) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Invalid mode value: " + value);
    }
}
