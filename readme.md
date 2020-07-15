**Sidechains-SDK Beta**
-------------------
Sidechains are an innovation devised to enable blockchain scalability and extensibility. The basic idea is simple yet powerful: construct parallel chains – "sidechains" – each implementing the desired features and custom business logic, rooted on a protocol that offers a way to transfer coins from and to the original mainchain and each sidechain.

Our Sidechains-SDK is a framework that supports the creation of such sidechains and their custom business logic, with the [Horizen](https://www.horizen.global/) blockchain as the "mainchain". Detailed description of the concept, the protocol, and details about mainchain / sidechain interaction can be found on the [Zendoo Whitepaper](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf).

**Beta features**
1. Cross-Chain Transfer Protocol implementation to support Sidechain Declaration, Forward Transfers, Backward Transfer requests and Withdrawal Certificates;
2. Basic zk-SNARK threshold signature verification circuit to authenticate Withdrawal Certificates. For more details see [zendoo-sc-cryptolib](https://github.com/ZencashOfficial/zendoo-sc-cryptolib);
3. Full implementation of the [Latus Proof-of-Stake consensus protocol](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf);
4. Built-in transactions enabling coins transfers within the sidechain;
5. HTTP API for basic node operations;
6. Extensible transactions and boxes allowing the introduction of custom logic and data within the sidechain;
7. Extensible node API interface;
8. Command line tool to interact with the sidechain node;
9. Sidechain Bootstrapping Tool to create and configure a new sidechain network;
10. Graphical Wallet allowing easy sidechain creations, fordward transfers to sidechain, list of existing sidechains and more: [Sphere by Horizen](https://github.com/ZencashOfficial/Sphere_by_Horizen_Sidechain_Testnet/releases/tag/desktop-v2.0.0-beta-sidechain-testnet).

**Supported platforms**

Sidechains-SDK is available and tested on Linux and Windows (64bit).

**Requirements**

1. Java 8 or newer (Java 11 recommended) 
2. Scala 2.12.10+

**Interaction**

Each node has an API server bound to the `address:port` specified in a configuration file.
Also the node starts the Swagger server.
 
There are two ways to interact with the Node:
1. Swagger web interface. Just open in your browser the path to the API server. By default it's `localhost:9085`.
2. Use any convenient HTTP client. For example, curl or postman.  

**Project structure**

Project has a Maven module structure and consists of 4 modules:
1) SDK - core of the Sidechains-SDK.
2) ScBootstrappingTool - tool that supports the creation of a Sidechain configuration file that allows the synchronization with the mainchain network.
3) Simple App - an example application, without any specific custom logic, that runs a Node, that can be either connected to the mainchain network, or isolated from it.
4) Qa - [Sidechain Test Framework](qa/readme.md) for Sidechain testing via RPC/REST commands.

**Examples**
You can find an example of a sidechain implementation, without any custom business logic, here: [Simple App](examples/simpleapp/readme.md). A detailed description about how to setup and run this sidechain node with a connection to mainchain is detailed here: [description](examples/simpleapp/mc_sc_workflow_example.md).
