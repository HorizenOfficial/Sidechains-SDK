package com.horizen

trait SecretCompanionWrapper[S <: scorex.core.transaction.state.Secret,
    PKP <: scorex.core.transaction.box.proposition.Proposition,
    PRP <: scorex.core.transaction.proof.Proof[PKP] ]
    //PKP, PRP]
    extends scorex.core.transaction.state.SecretCompanion[S]
{
  override type PK = PKP
  override type PR = PRP

  //override def verify(message: Array[Byte], publicImage: PKP, proof: PR): Boolean
  def verify1(message: Array[Byte], publicImage: PKP, proof: PRP): Boolean
}