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

**Beta changes**
1. Mainchain synchronization: added backward transfer support with Withdrawal certificate with threshold signature zero-knowledge proof by using [zendoo-sc-cryptolib](https://github.com/HorizenOfficial/zendoo-sc-cryptolib)
2. Added [Latus Proof-of-Stake consensus protocol](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf)  for sidechain based on [Ouroboros Praos](https://eprint.iacr.org/2017/573.pdf) consensus protocol which supporting forks in Sidechain and Mainchain. Autoforging for Sidechain node is added as well.
3. Reworked Transactions structure: introduced SidechainCoreTranscation, Boxes structure was improved/changed now three types of boxes are present by default: zen box, withdrawal request box and forger box. Forger box is used for consensus forger selection.


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