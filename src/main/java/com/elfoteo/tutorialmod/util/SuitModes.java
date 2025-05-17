package com.elfoteo.tutorialmod.util;

public enum SuitModes {
    ARMOR(0),
    CLOAK(1),
    VISOR(2);

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
