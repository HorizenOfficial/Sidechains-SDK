**Sidechains-SDK Blaze**
-------------------

Sidechains are an innovation devised to enable blockchain scalability and extensibility by creating parallel platform and application layers that are bound to the mainchain without imposing a significant burden. Each sidechain implements the desired features and custom business logic, rooted in a protocol that offers a way to transfer coins from and to the original mainchain and each sidechain.

Zendoo is a unique sidechain and scaling solution developed by Horizen. The Zendoo sidechain SDK is a framework that supports the creation of sidechains and their custom business logic, with the Horizen public blockchain as the mainchain. A detailed description of the concept, the protocol, and details about mainchain/sidechain interaction can be found in the [Zendoo Whitepaper](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf).


[Public Release Notes](/doc/index.md)

[Changelog](/CHANGELOG.md)

**Blaze Features**

* The Cross-Chain Transfer Protocol (CCTP) implementation to support sidechain declaration, forward transfers, backward transfer requests, withdrawal certificates and ceased sidechain withdrawals
* Basic zk-SNARK threshold signature verification circuit to authenticate withdrawal certificates. See [zendoo-sc-cryptolib](https://github.com/HorizenOfficial/zendoo-sc-cryptolib)
* Implementation based on the [Latus Sidechain Model](https://www.horizen.global/assets/files/Horizen-Sidechain-Zendoo-A_zk-SNARK-Verifiable-Cross-Chain-Transfer-Protocol.pdf) with signers for certificate submission, and [Ouroboros Praos](https://eprint.iacr.org/2017/573.pdf) proof of stake protocol
* Built-in transactions enabling transfers of coins within the sidechain
* Forging right delegation mechanism
* HTTP API for basic node operations
* Two model supported:
    * UTXO Model, with extensible transactions and boxes allowing the introduction of custom logic and data within the sidechain
    * Account Model, offering an Ethereum Virtual Machine compatible environment
* Extensible node API interface
* Command-line tool to interact with the sidechain node
* Sidechain Bootstrapping Tool to create and configure a new sidechain network

**Supported platforms**

The Zendoo Sidechain SDK is available and tested on Linux and Windows (64bit).

For more details see [zendoo-sc-cryptolib](https://github.com/HorizenOfficial/zendoo-sc-cryptolib)

**Requirements**

* Java 11 or newer
* Scala 2.12.10+
* Python 3.10
* Maven

On some Linux OSs during backward transfers certificates proofs generation a extremely big RAM consumption may happen, that will lead to the process force killing by the OS.
While we keep monitoring the memory footprint of the proofs generation process, we have verified that using Jemalloc library instead of Glibc keeps the memory consumption in check. Glibc starting from version 2.26 is affected by this issue. To check and fix this issue on Linux OS follow these steps:
 - Check your version of Glibc. To check your version of Glibc on Ubuntu, run the command `ldd --version`
 - Install Jemalloc library only in the case Glibc version is 2.26 or above. Please remember that only versions 3.x and above of the Jemalloc library are tested and will solve the issue. Jemalloc is available as apt package, which is different depending on the Ubuntu version you have. To check which Ubuntu version you have run `lsb_release -a`. Proceed with installing Jemalloc library by executing the command line:
	 - `sudo apt-get install libjemalloc2` (for Ubuntu versions 20.x and above)
	 - `sudo apt-get install libjemalloc1` (for Ubuntu versions less than 20.x)
 - Locate the installation folder of the Jemalloc library. On Ubuntu 18.04 (64bit) the library should be located in this path: `/usr/lib/x86_64-linux-gnu/libjemalloc.so.1`. Bear in mind that on Ubuntu 20.x and above the whole process is the same, but the file name should be `libjemalloc.so.2`
 - After the installation, just run `export LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1` before starting the sidechain node, or run the sidechain node adding `LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1` at the beginning of the java command line as follows:

```
LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1 java -cp ./target/sidechains-sdk-simpleapp-0.13.0-RC5.jar:./target/lib/* io.horizen.examples.SimpleApp <path_to_config_file>
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
3) [Simple App](examples/utxo/simpleapp/README.md) - An example application without any specific custom logic that runs a node. The node can be connected to the mainchain network or isolated from it
4) Q/A - [Sidechain Test Framework](qa/README.md) for sidechain testing via RPC/REST commands

**Configuration**

If you need to run the sidechain node as a **docker container** or behind a **NAT** the hostname and port pair that the node binds to will be different from the “logical” host name and port pair that is used to connect to the system from the outside. This requires special configuration that sets both the logical and the bind pairs for remoting.
You need to set the *declaredAddress* field with the host machine's address.
```
sparkz {
    dataDir = /tmp/sparkz/data/blockchain
    logDir = /tmp/sparkz/data/log
	...
    network {
        nodeName = "node name"
        bindAddress = "127.0.0.1:9084"
        declaredAddress = "45.123.0.0:9084"
    }
```

**Examples**

You can find an example of a sidechain implementation without any custom business logic here: [Simple App](examples/README.md). A detailed description of how to set up and run a sidechain node with a connection to the mainchain [can be found here](examples/mc_sc_workflow_example.md).

## Extras

In order to build and use `SNAPSHOT.jar` package version refer to the following [documentation](./ci/README.md)


**Test Sidechains-SDK with unreleased version of zendoo-sc-cryptolib**
1. git clone https://github.com/HorizenOfficial/zendoo-sc-cryptolib
2. git checkout -b name_of_needed_branch
3. cargo build --release --manifest-path=api/Cargo.toml
4. cp target/release/libzendoo_sc.so jni/src/main/resources/native/linux64/libzendoo_sc.so
5. cd jni
6. mvn clean install -Dmaven.test.skip=true
7. Go to project Sidechains-SDK
8. sdk/pom.xml change version of <artifactId>zendoo-sc-cryptolib to version in line 6 of zendoo-sc-cryptolib/jni/pom.xml
9. mvn clean install -Dmaven.test.skip=true

## Backward Compatibility

Version 0.7.0 is not backward compatible with any previous versions.
Due to a change in the consensus protocol, you cannot update any existing Sidechain of version <=0.6.1 into a >=0.7.0

