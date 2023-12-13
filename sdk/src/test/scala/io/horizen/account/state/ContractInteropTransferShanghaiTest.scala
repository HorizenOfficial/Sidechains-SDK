package io.horizen.account.state

import io.horizen.account.utils.FeeUtils
import io.horizen.evm._

class ContractInteropTransferShanghaiTest extends ContractInteropTransferTest {

  override val blockContext =
    new BlockContext(Address.ZERO, 0, FeeUtils.INITIAL_BASE_FEE, gasLimit, 1, Shanghai_Fork_Point, 1, 1234, null, Hash.ZERO)

}

