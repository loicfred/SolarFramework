package org.solarframework.ai.enums;

public enum Role {
    SYSTEM, USER, ASSISTANT, TOOL;

    public boolean isSystem() { return this == SYSTEM; }
    public boolean isUser() { return this == USER; }
    public boolean isAssistant() { return this == ASSISTANT; }
    public boolean isTool() { return this == TOOL; }
}
