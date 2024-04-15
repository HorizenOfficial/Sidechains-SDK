package io.horizen.account.dump

import io.horizen.account.storage.{AccountStateMetadataStorage, AccountStateMetadataStorageView}
import io.horizen.evm.{Address, Evm, EvmContext, ForkRules, Hash, LevelDBDatabase, StateDB}
import io.horizen.params.TestNetParams
import io.horizen.storage.leveldb.VersionedLevelDbStorageAdapter
import io.horizen.utils.BytesUtils

import java.io.File
import java.math.BigInteger

class DumpService {

  def doDump(dataDirAbsolutePath: String) = {

    val metaStateStore = new File(dataDirAbsolutePath + "/state")
    val stateMetadataStorage = new AccountStateMetadataStorage(
      new VersionedLevelDbStorageAdapter(metaStateStore, 100 * 2))

    System.out.println("state root: "+BytesUtils.toHexString(stateMetadataStorage.getAccountStateRoot))

    val stateDbStorage: LevelDBDatabase = new LevelDBDatabase(dataDirAbsolutePath + "/evm-state")
    var stateDB = new StateDB(stateDbStorage, new Hash(stateMetadataStorage.getAccountStateRoot))
    System.out.println(stateDB.getBalance( new Address("0x62b1bC6Fd237b775138d910274Ff2911D7aEa5cc")))

    stateDB.stateDump(new Address("0x62b1bC6Fd237b775138d910274Ff2911D7aEa5cc"))



    val evmContext: EvmContext = new EvmContext(
      BigInteger.valueOf(TestNetParams().chainId),
      new Address("0x0000000000000000000000000000000000000000"),
      BigInteger.valueOf(0),
      BigInteger.valueOf(0),
      BigInteger.valueOf(0),
      BigInteger.valueOf(0),
      BigInteger.valueOf(0),
      new Hash("0x0000000000000000000000000000000000000000000000000000000000000000"),
      new ForkRules(true)
    )

    // execute EVM dump
    var result = Evm.Dump(stateDbStorage, evmContext);


  }

}
