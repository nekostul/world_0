package ru.nekostul.worldzero;

public enum WorldZeroMajorEventType {
    WATCHING("watching"),
    STALKER("stalker"),
    VOID_CALL("void_call"),
    CORRUPTION("corruption"),
    GROWTH("growth"),
    SWARM("swarm"),
    TIME_LOOP("time_loop"),
    GLITCH_RAIN("glitch_rain");

    private final String worldzero$debugName;

    WorldZeroMajorEventType(String debugName) {
        this.worldzero$debugName = debugName;
    }

    public String worldzero$debugName() {
        return this.worldzero$debugName;
    }
}
