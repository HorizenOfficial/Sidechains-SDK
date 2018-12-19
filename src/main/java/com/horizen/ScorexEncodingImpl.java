package com.horizen;

public class ScorexEncodingImpl implements ScorexEncoding {
    @Override
    public ScorexEncoder encoder() {
        return new ScorexEncoder();
    }
}
