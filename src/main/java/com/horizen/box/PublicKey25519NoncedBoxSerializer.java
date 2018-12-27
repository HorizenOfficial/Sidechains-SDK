package com.horizen.box;

import scala.util.Try;

class PublicKey25519NoncedBoxSerializer<PKNB extends PublicKey25519NoncedBox> implements BoxSerializer<PKNB>
{
    public Try<PKNB> parseBytes(byte[] bytes) {
        return null;
    }

    public byte[] toBytes(PKNB obj) {
        return null;
    }
}