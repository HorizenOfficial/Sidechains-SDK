package com.horizen.csw

import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.utils.{ByteArrayWrapper, ForwardTransferCswData, UtxoCswData}

case class CswWitnessHolder(utxoCswDataMap: Map[ByteArrayWrapper, UtxoCswData],
                            ftCswDataMap: Map[ByteArrayWrapper, ForwardTransferCswData],
                            lastActiveCertOpt: Option[WithdrawalEpochCertificate],
                            mcbScTxsCumComStart: Array[Byte],
                            scTxsComHashes: Seq[Array[Byte]], // from start + 1 to end
                            mcbScTxsCumComEnd: Array[Byte])
