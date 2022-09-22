package com.horizen;

import scorex.util.encode.BytesEncoder;

// do we actually need to have SparkzEncoding?
// do we need to extend from sparkz.util.SparkzEncoding?
// TO DO: check it

public class SparkzEncoding //implements sparkz.util.SparkzEncoding
{
    public static final BytesEncoder encoder() {
        return scorex.util.encode.Base16$.MODULE$;
    }

}
