package com.horizen

import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.proposition.ProofOfKnowledgeProposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction

class SidechainTypes {

  type S <: Secret
  type P <: Proposition
  type B <: Box[P]
  type BT <: BoxTransaction[P,B]

}
