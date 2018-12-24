package com.horizen.transaction;

import com.horizen.box.NoncedBox;
import com.horizen.proposition.Proposition;

abstract class NoncedBoxTransaction<P extends Proposition, B extends NoncedBox<P>> extends BoxTransaction<P, B>
{

}