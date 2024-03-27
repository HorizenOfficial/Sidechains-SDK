package io.horizen.account.state.nativescdata.forgerstakev2

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.ABIEncodable
import io.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import io.horizen.account.utils.BigIntegerUInt256
import io.horizen.json.Views
import org.web3j.abi.datatypes.{DynamicArray, DynamicStruct, StaticStruct, Type}
import org.web3j.abi.datatypes.generated.{Int32, Uint256}
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.util.serialization.{Reader, Writer}
import org.web3j.abi.datatypes.{Address => AbiAddress}
import java.math.BigInteger
import java.util
import scala.collection.JavaConverters


case class PagedForgersStakesByForgerOutput(nextStartPos: Int, listOfStakes: Seq[StakeDataDelegator])
  extends ABIEncodable[DynamicStruct] {

  override def asABIType(): DynamicStruct = {

    val seqOfStruct = listOfStakes.map(_.asABIType())
    val listOfStruct = JavaConverters.seqAsJavaList(seqOfStruct)
    val theType = classOf[StaticStruct]
    val listOfParams: util.List[Type[_]] = util.Arrays.asList(
      new Int32(nextStartPos),
      new DynamicArray(theType, listOfStruct)
    )
    new DynamicStruct(listOfParams)

  }

  override def toString: String = "%s(startPos: %s, listOfStake: %s)"
    .format(
      this.getClass.toString,
      nextStartPos, listOfStakes)
}

@JsonView(Array(classOf[Views.Default]))
case class StakeDataDelegator(val delegator: AddressProposition,
                              val stakedAmount: BigInteger)
  extends BytesSerializable  with ABIEncodable[StaticStruct]  {

  require(stakedAmount.signum() != -1, "stakeAmount expected to be non negative.")

  override type M = StakeDataDelegator

  override def serializer: SparkzSerializer[StakeDataDelegator] = StakeDataDelegatorSerializer

  override def toString: String = "%s(delegator: %s, stakedAmount: %s)"
    .format(this.getClass.toString,  delegator, stakedAmount)


  private[horizen] def asABIType(): StaticStruct = {
    val listOfParams = new util.ArrayList[Type[_]]()
    listOfParams.add(new Uint256(stakedAmount))
    listOfParams.add(new AbiAddress(delegator.address().toString))
    new StaticStruct(listOfParams)
  }
}

object StakeDataDelegatorSerializer extends SparkzSerializer[StakeDataDelegator] {
  override def serialize(s: StakeDataDelegator, w: Writer): Unit = {
    AddressPropositionSerializer.getSerializer.serialize(s.delegator, w)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
  }

  override def parse(r: Reader): StakeDataDelegator = {
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigIntegerUInt256(r.getBytes(stakeAmountLength)).getBigInt
    StakeDataDelegator(ownerPublicKey, stakeAmount)
  }
}
