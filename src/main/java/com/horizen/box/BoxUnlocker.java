package com.horizen.box;

import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;

public interface BoxUnlocker<P extends Proposition>
{
    byte[] closedBoxId();

    Proof<P> boxKey();
}
