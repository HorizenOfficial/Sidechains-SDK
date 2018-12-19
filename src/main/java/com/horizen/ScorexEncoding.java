package com.horizen;
//import scorex.core.utils.ScorexEncoder;
//import scorex.core.utils.ScorexEncoding;

import scorex.core.utils.ScorexEncoder;

public interface ScorexEncoding extends scorex.core.utils.ScorexEncoding
{

    @Override
    ScorexEncoder encoder();
}
