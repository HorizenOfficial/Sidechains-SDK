package com.horizen.secret;


public interface SecretCreator<S extends Secret>
{
    // Generate secret without context of previously generated secrets stored in wallet.
    // Mostly for tests.
    S generateSecret(byte[] seed);

    byte[] salt();
}