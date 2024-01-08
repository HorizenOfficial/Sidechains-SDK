package io.horizen.account.state

import io.horizen.account.utils.FeeUtils
import io.horizen.evm.{Address, Hash}

class ContractInteropStackShanghaiTest extends ContractInteropStackTest {

  override val blockContext =
    new BlockContext(Address.ZERO, 0, FeeUtils.INITIAL_BASE_FEE, gasLimit, 1, Shanghai_Fork_Point, 1, 1234, null, Hash.ZERO)

}
