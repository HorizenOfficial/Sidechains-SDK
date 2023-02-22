package com.horizen.secret;

public interface SecretCreator<S extends Secret>
{
    // Generate secret without context of previously generated secrets stored in wallet.
    // Mostly for tests.
    S generateSecret(byte[] seed);

    /**
     * Method to get salt.
     * In this case salt serves as a domain separation
     *
     * @return salt as byte array in UTF-8 encoding
     */
    byte[] salt();
}