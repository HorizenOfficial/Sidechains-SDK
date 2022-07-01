**0.4.0**
1. Memory pool improvements: upper bound size limit introduced (default 300Mb) altogether with cleanup strategy (the lowest fee rate transaction removed first).
2. CSW is optional. Sidechains without CSW support are available now. Note: keep using CSW feature in real environment to have a possibility to withdraw coins in case of ceasing.
3. API Authorization added to the coin critical endpoints, like keys management, transaction creation and submission, csw creation, etc.
4. API freezing during node synchronization resolved.
5. New API endpoints added for importing/exporting keys to/from the wallet.
6. Forger is now sort transactions by fee rate instead of fee.
7. Wallet: max fee check added for locally generated transactions to prevent absurdly high fees.
8. Custom propositions wallet management improved: complex multi-key propositions are now recognized.
9. Peers spam detection mechanism improved: "trash" data detection in the end of the block/transaction added.
10. Bootstrapping tool: dlog keys multiple initialization prevented.
11. Extra verbosity added to the API responses.
12. Logging system improved. Application specific configs are allowed.
13. FeePayments visibility bug fixed: wrongly added fee payments to the block info when there were no payment at all.

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