**Sidechains-SDK Beta**
-------------------
Sidechains are an appealing innovation devised to enable blockchain scalability and extensibility. The basic idea is simple yet powerful: construct a parallel chain – sidechain – with desired
features like custom business logic, and provide a way to transfer coins between the mainchain and the sidechain
Sidechains-SDK is a framework, that allows to create such sidechains with custom business logic for [Horizen](https://www.horizen.global/) blockchain. Detailed description mainchain / sidechain interaction could be found on next [whitepaper](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf)  

**Beta features**
1. Multiple sidechain nodes network.
2. Mainchain synchronization: Cross-chain Transfer Protocol support for sidechain declaration: forward and backward transfers.
3. Proof-of-Stake consensus protocol for sidechain based on Ouroboros Praos consensus protocol.
4. Built-in coins transferring operations inside sidechain.
5. HTTP API for basic node operations.
6. Possibility to declare custom Transactions/Boxes/Secrets/etc.
7. Possibility to extend/manage basic API.
8. Web interface and command line tool for interaction with the Node.
9. Sidechain Bootstapping Tool to configure sidechain network according to the mainchain network.


**Supported platforms**

Sidechains-SDK is available and tested on Linux and Windows (64bit).

**Requirements**

Install Java 11 or newer, Scala 2.12.x, SBT 0.13.x

**Interaction**

Each node has an API server bound to the `address:port` specified in a configuration file.
Also the node starts the Swagger server.
 
There are two ways how to interact with the Node:
1. Swagger web interface. Just open in your browser the path to API server. By default it's `localhost:9085`.
2. Use any convenient HTTP client. For example, curl or postman.  

**Project structure**

Project has a module maven structure and consists of 3 modules:
1) SDK - core of the Sidechains-SDK.
2) ScBootsrappingTool - tool that allow you to prepare configuration file for SC Node to be able to synchronize with MC network.
3) Simple App - example application without any custom logic that allow to run a Node both connected to MC network and isolated from MC.

**Examples**

To have a sidechain node without any custom business logic run [Simple App](examples/simpleapp/mc_sc_workflow_example.md) example.
