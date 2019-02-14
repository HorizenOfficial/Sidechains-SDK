package com.horizen.transaction;

@FunctionalInterface
public interface MemoryPoolCompatibilityChecker {
    boolean isMemoryPoolCompatible();
}
