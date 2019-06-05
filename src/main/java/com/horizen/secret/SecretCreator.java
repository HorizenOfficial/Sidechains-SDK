package com.horizen.secret;

interface SecretCreator<S extends Secret>
{
    S generateSecret(byte[] randomSeed);
    // TO DO: change to ...(NodeWallet wallet, byte[] seed);
}