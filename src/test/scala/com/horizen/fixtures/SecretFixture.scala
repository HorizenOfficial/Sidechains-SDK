package com.horizen.fixtures

import com.horizen.secret._

trait SecretFixture {
  val pkc = PrivateKey25519Companion.getCompanion()

  val pk1 = pkc.generateSecret("seed1".getBytes())
  val pk2 = pkc.generateSecret("seed2".getBytes())
  val pk3 = pkc.generateSecret("seed3".getBytes())
  val pk4 = pkc.generateSecret("seed4".getBytes())
  val pk5 = pkc.generateSecret("seed5".getBytes())
  val pk6 = pkc.generateSecret("seed6".getBytes())

}
