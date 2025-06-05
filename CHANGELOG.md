**0.13.0**
1. Fork configuration to disable backward transfers towards mainchain
2. Enabling preimages configuration
3. [eth RPC endpoint]: added zen_dump rpc method for requesting an EVM state dump on a json file
4. Migration from OSSHR to Central Portal

**0.12.0**
1. Sparkz dependency updated to 2.4.0
    * Updates in EON forger nodes connection policy (see release notes for further info) 
2. New stake management support (see release notes for further info)
3. Reward from mainchain - new rules (see release notes for further info)
4. Added metrics endpoint
5. Minor fixes:
    * [eth RPC endpoint]  web3_clientVersion now returns also the EON version
    * [eth RPC endpoint] Better error handling: in case of error the response status code will be 400 instead of 200
    * [eth RPC endpoint] In case of batch requests, the response is  now always an array,  even with a batch composed of only one element.

**0.11.0**
1. Sparkz dependency updated to 2.3.0
2. Updated third-party dependencies
3. Forger Stake native smart contract: added getPagedForgersStakesByUser, getPagedForgersStakes, stakeOf and upgrade methods
4. EVM implementation updated from go-ethereum Paravin (v1.10.26) to  go-ethereum Archanes (v1.13.4) compatibility. As a result:
    * Solidity compiler supported version upgraded from  0.8.19 to  0.8.23
    * Added compatibility  of following Ethereum EIPs:
        * EIP-3651: Warm COINBASE: with this EIP, the COINBASE address will be loaded at the beginning of a transaction in order to save some gas. 
        * EIP-3855: PUSH0 instruction, which pushes the constant value 0 onto the stack. It is used by Solidity compiler from version 0.8.20. 
        * EIP-3860: Limit and meter initcode. This introduces a maximum size limit for initcode. In addition, it introduces a charge of 2 gas for every 32-byte chunk of initcode.  
5. Pause forging: block production is temporary paused if no mainchain references are present in the last 99 EON blocks
6. For forger nodes it is now possible to specify a custom address where the block reward will be directed
7. Minor fixes:
    * [eth RPC endpoint] Fixed eth_getLogs errors in case of wrong usage of input parameters (for example negative integer values in fromBlock/toBlock)
    * [eth RPC endpoint] json "data" field in case of errors will contain more meaningful info on the root cause
    * [eth RPC endpoint] Fixed json result of  zen_getFeePayments method (if there are no fee payments in the block now it returns an empty array instead of null)
    * [http endpoint] /transaction/withdrawCoins: additional check to raise an error in case a withdraw to a mainchain address different than "Pay to key hash" is requested
    * Avoid to start forging activity if a forger node is not at the tip (avoid forging if consensus epoch and slot derived from block history is  too far from consensus epoch and block calculated by elapsed time since the genesis block)

**0.10.1**
* [eth RPC endpoint] Additional fix on json representation in RPC response of signature V field for transaction type 2 - it should be in range of 0-1.

**0.10.0**
1. Added support for multisig MC addresses in ZenDAO Native Smart Contract
2. Added support for ZenIP 42203/42206: 
    *  it is now possible to move founds with a forward transfer directly to a smart contract address in the EON sidechain
    *  it is now possible to increment the EON forgers reward pool with a forward transfer to a specific address
5. Minor fixes:
    * Forger Stake native smart contract: OpenStakeForgerList function can be invoked using the ABI-compliant signature. The old signature is still valid for backward compatibility.
    * [eth RPC endpoint] Added upper limit (10000) for number of blocks to inspect when calling eth_getLogs
    * [eth RPC endpoint] Fixed json representation in RPC response of signature V field for transaction type 2 - it should be in range of 0-1.
    * [eth RPC endpoint] eth_gasPrice - algorithm to suggest gas price will take now 20th percentile instead of 40th

**0.9.0**
1. libevm dependency updated to 1.0.0.
2. Added support for EVM and native smart contracts interoperability.
3. Sparkz dependency updated to 2.2.0.
4. Improved storage versioning (fullsynch time reduced by 5x)
5. Minor fixes:
    * [eth RPC endpoint] debug_traceCall now returns a more accurate error response for reverted transactions 
    * [eth RPC endpoint] debug_traceCall and debug_traceTransaction now return a correct value for the gasUsed field when topmost call is a call to a Solidity Smart Contract function.
    * Certificates older than 4 epochs are now deleted from the storage only if more recent certificates appeared.

**0.8.1**
1. Improved precision of eth_gasPrice RPC call

**0.8.0**
1. ZenDao native smart contracts
2. Added support for consensus parameter change (epoch length, slot time, active slot coefficient) using an hardfork 
3. Added support for delayed mainchain block inclusion
4. Sparkz dependency updated to 2.1.0

**0.7.2**
1. Sparkz dependency updated to 2.0.3

**0.7.1**
1. Seeder nodes support
2. PeerToPeer and API rate limiting - tx rebroadcast feature
3. Sparkz dependency updated to 2.0.2
4. Minor fixes:
    * expose app version in  node/info rest api
    * account model: fixes in debug_traceTransaction and eth_feeHistory rpc commands
    * improved logs in certificate submission process
    * fixed HTTP Header on SecureEnclaveApiClient
    * fix on syncing mechanism to prevent issue that caused nodes being unable to sync indefinitely
    * fixed default gasLimit in createKeyRotationTransaction
    * signaturesFromEnclave timeout handling improvements

**0.7.0**
1. Account model introduced. EvmApp application example added.
2. Base package renamed from `com.horizen` to `io.horizen`
3. Packages overall refactoring, in particular UTXO specific classed moved to `io.horizen.utxo`, Account to `io.horizen.account`. 
4. Change in the consensus protocol: forger eligibility rule. 
5. zendoo-sc-cryptolib updated: certificate and CSW circuits were modified (backward incompatible to previous version). 
6. Sparkz dependency updated from 2.0.0-RC9 to 2.0.1
7. Deterministic key generation mechanism changed: all secret keys now deterministic. Ed25519 key generation algorithm modified.
8. PeerToPeer and API rate limiting

**0.6.1**
1. Update zendoo-sc-cryptolib to final 0.6.0

**0.6.0**
1. Sidechain version 2 support with the new circuit type - threshold signature circuit with key rotation.
2. Certificate key rotation API endpoints added. 
3. Non ceasing sidechain support. 
4. Bootstrapping tool: virtualWithdrawalEpochLength parameter added to `genesisinfo` command. 
5. Bootstrapping tool: `generateCertWithKeyRotationProofInfo` command added for certificate circuit with key rotation.
6. Remote keys manager added to the CertificateSubmitter: submitter is able to sign certificates using the Secure Enclave hosted keys.
7. Config file structure updated: remote keys manager configuration section added `sparkz.remoteKeysManager`.
8. Signing tool introduced.
9. MC2SCAggregatedTransaction max size limit fixed to fit max FT allowed by the mainchain. 

**0.5.0**
1. Scorex dependency has been updated from Scorex 2.0.0-RC6 to Sparkz 2.0.0-RC9 (package name has been changed to sparkz).
2. Bootstrapping tool interface changed: cert proof info was separated from signers key generation.
3. Fork manager introduced to be able to implement new backward incompatible functionality.
4. Coin boxes dust check added in the Fork 1.
5. Forward transfer minimum amount limit defined in the Fork 1.
6. Numerous consensus improvements introduced in the Fork 1.
7. OpenStakeTransaction - new core transaction type added to allow the majority of forgers to open staking for everyone. Introduced in the Fork 1.
8. Backward Transfers limit introduced in the Fork 1. The total limit of 3999 BTs per withdrawal epoch, using the "slots" opening strategy per mainchain block reference. Introduced in the Fork 1.
9. Forger block generation fixes: no transactions allowed in case of ommers.
10. Network data checks improved.
11. Network API: connected peers info updated.
12. Numerous library dependencies were updated.

**0.4.3**
1. Blocks network propagation fixed: allow to send blocks greater than 1 Mb.

**0.4.2**
1. Explorer synchronization issue solved: history.chainAfter method was optimized.
2. Certificate commitment tree calculation issue solved: fixed an inconsistency between SC and MC implementations.

**0.4.1**
1. CCTP: other sidechains with version 2+ are supported.
2. API authentication behavior updated: can be disabled now.
3. Swagger API schema fixed.
4. DBTool: custom storages support added.

**0.4.0**
1. Memory pool improvement: upper bound size limit introduced (default 300Mb) altogether with cleanup strategy (the lowest fee rate transaction removed first).
2. Memory pool improvement: minimum fee rate check added for incoming transactions. By default, is disabled.
3. CSW is optional. Sidechains without CSW support are available now. Note: keep using CSW feature in real environment to have a possibility to withdraw coins in case of ceasing.
4. API Authorization added to the coin critical endpoints, like keys management, transaction creation and submission, csw creation, etc.
5. API freezing during node synchronization resolved.
6. New API endpoints added for importing/exporting keys to/from the wallet.
7. Forger sorts transactions by fee rate instead of fee.
8. Wallet: max fee check added for locally generated transactions to prevent absurdly high fees. Max fee value is set in zennies. Default value is 10000000 (0.1 Zen).
9. Custom propositions wallet management improved: complex multi-key propositions are now recognized.
10. Peers spam detection mechanism improved: "trash" data detection in the end of the block/transaction added.
11. Bootstrapping tool: dlog keys multiple initialization prevented.
12. Extra verbosity added to the API responses.
13. Logging system improved. Application specific configs are allowed.
14. FeePayments visibility bug fixed: wrongly added fee payments to the block info when there were no payment at all.

**0.3.5**
1. Snark keys generation fixed: circuit specific segment size added.

**0.3.4**
1. Added the possibility to perform a backup of a sidechain non coin-boxes and restore these boxes into a new bootstrapped sidechain of the same type.
2. log4j version updated.

**0.3.3**
1. Mainchain block deserialization fix: CompactSize usage issue.
2. Bootstrapping tool improvement: scgenesisinfo data parsing.
3. Added logic for checking storages consistency at node startup, and trying to recover the situation for instance if a crash happened during update procedure.
4. CertificateSubmitter on active sync improvement in `getMessageToSign` method.
5. Added HTTP API for stopping the SC node and a hook for calling custom application stop procedure.

**0.3.2**
1. CertificateSubmitter and CertificateSignaturesManager actors restart strategy and failures processing improvement. 

**0.3.1**
1. Withdrawal epoch validator: fix wrongly rejected sidechain block containing McBlockRef with MC2SCAggTx leading to the end of the withdrawal epoch.


**Blaze changes (0.3.0)**
1. New proving system for certificates verification: Coboundary Marlin.
2. PGD: decentralized certificates signing.
3. API updated and improved: in particular certificate submitter, signer, csw and forging.
4. Transaction and Block versioning added for future forks.
5. Timestamp field removed from Transactions.
6. Forgers fee payments mechanism.
7. SidechainCoreTransaction become final. In general transactions structure was improved. Transactions class hierarchy changes.
8. LevelDB key-value storage is used now instead of IODB implementation. IODB was completely removed.
9. Sidechain Test Framework: python version updated from 2 to 3. Multiple improvements.
10. Ceased sidechain withdrawals support.
11. Better logging mechanism. Logging options introduced in the configuration file.
12. Objects serialization improved. New stream-based serialization schema introduced.
13. Sidechain creation versioning support.

**Beta changes**
1. Mainchain synchronization: added backward transfer support with Withdrawal certificate with threshold signature zero-knowledge proof by using [zendoo-sc-cryptolib](https://github.com/HorizenOfficial/zendoo-sc-cryptolib)
2. Added [Latus Proof-of-Stake consensus protocol](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf)  for sidechain based on [Ouroboros Praos](https://eprint.iacr.org/2017/573.pdf) consensus protocol which supporting forks in Sidechain and Mainchain. Autoforging for Sidechain node is added as well.
3. Reworked Transactions structure: introduced SidechainCoreTransaction, Boxes structure was improved/changed now three types of boxes are present by default: zen box, withdrawal request box and forger box. Forger box is used for consensus forger selection.


**Alpha features**
1. Multiple sidechain nodes network.
2. Mainchain synchronization: Cross-chain Transfer Protocol support for sidechain declaration and forward transfers.
3. Basic Consensus (anyone can forge).
4. Built-in coins transferring operations inside sidechain.
5. HTTP API for basic node operations.
6. Possibility to declare custom Transactions/Boxes/Secrets/etc.
7. Possibility to extend/manage basic API.
8. Web interface and command line tool for interaction with the Node.
9. Sidechain Bootstrapping Tool to configure sidechain network according to the mainchain network.
