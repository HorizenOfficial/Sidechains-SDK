package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIEncodable
import io.horizen.account.abi.ABIUtil.{getArgumentsFromData, getFunctionSignature}
import io.horizen.account.state.ContractInteropTestBase._
import io.horizen.account.utils.BigIntegerUtil.toUint256Bytes
import io.horizen.account.utils.{FeeUtils, Secp256k1}
import io.horizen.evm._
import io.horizen.utils.BytesUtils
import org.junit.Assert.{assertArrayEquals, assertEquals, fail}
import org.junit.Test
import org.scalatest.Assertions.intercept
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.{DynamicBytes, DynamicStruct, Type, Address => AbiAddress}
import org.web3j.abi.{TypeEncoder, datatypes}
import sparkz.crypto.hash.Keccak256

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import scala.util.{Failure, Try}

class ContractInteropCallShanghaiTest extends ContractInteropCallTest {

  override val blockContext =
    new BlockContext(Address.ZERO, 0, FeeUtils.INITIAL_BASE_FEE, gasLimit, 1, Shanghai_Fork_Point, 1, 1234, null, Hash.ZERO)

}