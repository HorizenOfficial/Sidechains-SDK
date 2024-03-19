package io.horizen.account.state.nativescdata.forgerstakev2

import io.horizen.proposition.VrfPublicKey
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes32}

trait VRFDecoder {

  protected[horizen] def decodeVrfKey(vrfFirst32Bytes: Bytes32, vrfLastByte: Bytes1): VrfPublicKey = {
    val vrfinBytes = vrfFirst32Bytes.getValue ++ vrfLastByte.getValue
    new VrfPublicKey(vrfinBytes)
  }

}
