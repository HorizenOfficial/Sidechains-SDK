package io.horizen.utxo.csw

import io.horizen.block.WithdrawalEpochCertificate
import io.horizen.utils.ByteArrayWrapper
import io.horizen.utxo.utils.{ForwardTransferCswData, UtxoCswData}

case class CswWitnessHolder(utxoCswDataMap: Map[ByteArrayWrapper, UtxoCswData],
                            ftCswDataMap: Map[ByteArrayWrapper, ForwardTransferCswData],
                            lastActiveCertOpt: Option[WithdrawalEpochCertificate],
                            mcbScTxsCumComStart: Array[Byte],
                            scTxsComHashes: Seq[Array[Byte]], // from start + 1 to end
                            mcbScTxsCumComEnd: Array[Byte])
