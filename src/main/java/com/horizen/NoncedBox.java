package com.horizen;

import com.horizen.box.Box;
import com.horizen.proposition.Proposition;

public interface NoncedBox<P extends Proposition> extends Box<P>
{
    long nonce();
}
