package com.horizen;

import scorex.util.encode.BytesEncoder;

// TO DO: do we actually need ScorexEncoding?
public class ScorexEncoding implements scorex.util.ScorexEncoding
{
    @Override
    public final BytesEncoder encoder() {
        return scorex.util.encode.Base16$.MODULE$;
    }
}
