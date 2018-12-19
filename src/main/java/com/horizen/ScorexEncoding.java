//import scorex.core.utils.ScorexEncoder;
//import scorex.core.utils.ScorexEncoding;

import scorex.core.utils.ScorexEncoder;

interface ScorexEncoding extends scorex.core.utils.ScorexEncoding
{

    @Override
    ScorexEncoder encoder();
}

class ScorexEncodingImpl implements ScorexEncoding {
    @Override
    public ScorexEncoder encoder() {
        return new ScorexEncoder();
    }
}
