package io.horizen.account.state.nativescdata.forgerstakev2

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.account.abi.ABIEncodable
import io.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import io.horizen.account.state.{ForgerPublicKeys, ForgerPublicKeysSerializer}
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


case class PagedForgersStakesByDelegatorOutput(nextStartPos: Int, listOfStakes: Seq[StakeDataForger])
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
case class StakeDataForger( val forgerPublicKeys: ForgerPublicKeys,
                              val stakedAmount: BigInteger)
  extends BytesSerializable  with ABIEncodable[StaticStruct]  {

  require(stakedAmount.signum() != -1, "stakeAmount expected to be non negative.")

  override type M = StakeDataForger

  override def serializer: SparkzSerializer[StakeDataForger] = StakeDataForgerSerializer

  override def toString: String = "%s(forger: %s, stakedAmount: %s)"
    .format(this.getClass.toString,  forgerPublicKeys, stakedAmount)


  private[horizen] def asABIType(): StaticStruct = {
    val forgerPublicKeysAbi = forgerPublicKeys.asABIType()
    val listOfParams: util.List[Type[_]] = new util.ArrayList(forgerPublicKeysAbi.getValue.asInstanceOf[util.List[Type[_]]])
    listOfParams.add(new Uint256(stakedAmount))
    new StaticStruct(listOfParams)
  }
}

object StakeDataForgerSerializer extends SparkzSerializer[StakeDataForger] {
  override def serialize(s: StakeDataForger, w: Writer): Unit = {
    ForgerPublicKeysSerializer.serialize(s.forgerPublicKeys, w)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
  }

  override def parse(r: Reader): StakeDataForger = {
    val forgerPublicKeys = ForgerPublicKeysSerializer.parse(r)
    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigIntegerUInt256(r.getBytes(stakeAmountLength)).getBigInt
    StakeDataForger(forgerPublicKeys, stakeAmount)
  }
}
