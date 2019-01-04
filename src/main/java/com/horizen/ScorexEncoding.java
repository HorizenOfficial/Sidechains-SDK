package com.horizen;

import scorex.util.encode.BytesEncoder;

// do we actually need to have ScorexEncoding?
// do we need to extend from scorex.util.ScorexEncoding?
// TO DO: check it

public class ScorexEncoding //implements scorex.util.ScorexEncoding
{
    public static final BytesEncoder encoder() {
        return scorex.util.encode.Base16$.MODULE$;
    }

}
