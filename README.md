**Sidechains-SDK Blaze**
-------------------
Sidechains are an innovation devised to enable blockchain scalability and extensibility by creating parallel platform and application layers that are bound to the mainchain without imposing a significant burden. Each sidechain implements the desired features and custom business logic, rooted in a protocol that offers a way to transfer coins from and to the original mainchain and each sidechain.

Zendoo is a unique sidechain and scaling solution developed by Horizen. The Zendoo sidechain SDK is a framework that supports the creation of sidechains and their custom business logic, with the Horizen public blockchain as the mainchain. A detailed description of the concept, the protocol, and details about mainchain/sidechain interaction can be found in the [Zendoo Whitepaper](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf).

**Blaze Features**

* The Cross-Chain Transfer Protocol (CCTP) implementation to support sidechain declaration, forward transfers, backward transfer requests, withdrawal certificates and ceased sidechain withdrawals
* Basic zk-SNARK threshold signature verification circuit to authenticate withdrawal certificates See [zendoo-sc-cryptolib](https://github.com/HorizenOfficial/zendoo-sc-cryptolib)
* Full implementation of the [Latus Proof-of-Stake consensus protocol](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf)
* Built-in transactions enabling transfers of coins within the sidechain
* Forging right delegation mechanism
* HTTP API for basic node operations
* Extensible transactions and boxes allowing the introduction of custom logic and data within the sidechain
* Extensible node API interface
* Command-line tool to interact with the sidechain node
* Sidechain Bootstrapping Tool to create and configure a new sidechain network
* Graphical Wallet allowing easy sidechain creations, forward transfers to sidechain, list of existing sidechains and more: [Sphere by Horizen](https://github.com/HorizenOfficial/Sphere_by_Horizen_Sidechain_Testnet/releases/tag/desktop-v2.0.0-beta-sidechain-testnet).

**Supported platforms**

The Zendoo Sidechain SDK is available and tested on Linux and Windows (64bit).

For more details see [zendoo-sc-cryptolib](https://github.com/HorizenOfficial/zendoo-sc-cryptolib)

**Requirements**

* Java 11 or newer
* Scala 2.12.10+
* Python 3
* Maven

On some Linux OSs during backward transfers certificates proofs generation a extremely big RAM consumption may happen, that will lead to the process force killing by the OS.
While we keep monitoring the memory footprint of the proofs generation process, we have verified that using Jemalloc library (version 3.x only) instead of Glibc keeps the memory consumption in check. Glibc starting from version 2.26 is affected by this issue. To check and fix this issue on Linux OS follow these steps:
 - Check your version of Glibc. To check your version of Glibc on Ubuntu, run the command `ldd --version`
 - Install Jemalloc library. Please remember that only version 3.x of the Jemalloc library is tested and will solve the issue. Jemalloc is available as apt package, just execute the command line:
	 - `sudo apt-get install libjemalloc1` (Ubuntu)
 - Locate the installation folder of the Jemalloc library. On Ubuntu 18.04 (64bit) the library should be located in this path: `/usr/lib/x86_64-linux-gnu/libjemalloc.so.1`
 - After the installation, just run `export LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1` before starting the sidechain node, or run the sidechain node adding `LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1` at the beginning of the java command line as follows:

```
LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1 java -cp ./target/sidechains-sdk-simpleapp-0.3.0.jar:./target/lib/* com.horizen.examples.SimpleApp <path_to_config_file>
```
 - In the folder `ci` you will find the script `run_sc.sh` to automatically check and use jemalloc library while starting the sidechain node. 

**Interaction**

Each node has an API server bound to the `address:port` specified in a configuration file. The node also starts the Swagger server.

There are two ways to interact with the node:
1. Swagger web interface. Open the path to the API server in your browser. By default, it's `localhost:9085`.
2. Use any HTTP client that supports POST requests. For example, curl or Postman.

**Project Structure**

The project has a Maven module structure and consists of 4 modules:
1) SDK - The core of the sidechain SDK
2) ScBootstrappingTool - A tool that supports the creation of a sidechain configuration file that allows the synchronization with the mainchain network
3) [Simple App](examples/simpleapp/README.md) - An example application without any specific custom logic that runs a node. The node can be connected to the mainchain network or isolated from it
4) Q/A - [Sidechain Test Framework](qa/README.md) for sidechain testing via RPC/REST commands

**Examples**

You can find an example of a sidechain implementation without any custom business logic here: [Simple App](examples/simpleapp/README.md). A detailed description of how to set up and run a sidechain node with a connection to the mainchain [can be found here](examples/simpleapp/mc_sc_workflow_example.md).
