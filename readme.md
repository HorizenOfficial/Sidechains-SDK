**Sidechains-SDK Alpha**
-------------------
Sidechains-SDK is a framework, that allows to create [Horizen](https://www.horizen.global/) blockchain compatible sidechains with custom business logic.  

**Alpha features**
1. Multiple sidechain nodes network.
2. Mainchain synchronization: Cross-chain Transfer Protocol support for sidechain declaration and forward transfers.
3. Basic Consensus (anyone can forge).
4. Built-in coins transferring operations inside sidechain.
5. HTTP API for basic node operations.
6. Possibility to declare custom Transactions/Boxes/Secrets/etc.
7. Possibility to extend/manage basic API.
8. Web interface and command line tool for interaction with the Node.
9. Sidechain Bootstapping Tool to configure sidechain network according to the mainchain network.


**Supported platforms**

Sidechains-SDK is available and tested on Linux and Windows (64bit).

**Requirements**

Install Java 8 or newer, Scala 2.12.x, SBT 0.13.x

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

To have a sidechain node without any custom business logic run [Simple App](examples/simpleapp/readme.md) example.
