package com.horizen;

import java.security.MessageDigest;

public class ConsolePrinter implements MessagePrinter {
    @Override
    public void print(String message) {
        System.out.println(message);
    }
}
