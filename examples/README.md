**Example Apps** 
---------

The Example Apps are Java applications that run a basic Sidechain Node, able to receive and send coins from/to the mainchain ("Forward" and "Backward transfers"). They do not come with any application-specific code (e.g. custom boxes or transactions); that logic can be easily added to either EVM App or Simple App, and implement that way a complete, blockchain based distributed application.

Example App node(s) can also be run without a connection to mainchain.

The EvmApp is a sidechain node using the "account" model, while the SimpleApp uses the "UTXO" model.

**Install Dependencies**

Solidity Compiler
```
sudo add-apt-repository ppa:ethereum/ethereum
sudo apt-get update
sudo apt-get install solc
```

Yarn
```
sudo npm install -global yarn
```

Python3 Packages
```
pip3 install -r requirements.txt
```

**Execution**

You can run an Example App inside an IDE by:
* Executing the SimpleApp class in the examples/utxo/simpleapp module.
* Executing the EvmApp class in the examples/account/evmapp module.

Otherwise, to run an Example App outside the IDE:
1. Build and package the project
    ```
    mvn package
    ```
2. Go to the project root directory and execute in a command line:

**Model: UTXO**

* (Windows)
    ```
    cd Sidechains-SDK\examples\simpleapp
    java -cp ./target/sidechains-sdk-simpleapp-0.13.0-RC5.jar;./target/lib/* io.horizen.examples.SimpleApp <path_to_config_file>
    ```
* (Linux)
    ```
    cd ./Sidechains-SDK/examples/utxo/simpleapp
    java -cp ./target/sidechains-sdk-simpleapp-0.13.0-RC5.jar:./target/lib/\* io.horizen.examples.SimpleApp <path_to_config_file>
    ```
**Model: Account**

* (Windows)
    ```
    cd Sidechains-SDK\examples\evmapp
    java -cp ./target/sidechains-sdk-evmapp-0.13.0-RC5.jar;./target/lib/* io.horizen.examples.EvmApp <path_to_config_file>
    ```
* (Linux)
    ```
    cd ./Sidechains-SDK/examples/account/evmapp
    java -cp ./target/sidechains-evmapp-0.13.0-RC5.jar:./target/lib/\* io.horizen.examples.EvmApp <path_to_config_file>
    ```

On some Linux OSs during backward transfers certificates proofs generation an extremely large RAM consumption may happen, that will lead to the process being force killed by the OS.

While we keep monitoring the memory footprint of the proofs generation process, we have verified that using Jemalloc library (version 3.x only) instead of Glibc keeps the memory consumption in check. Glibc starting from version 2.26 is affected by this issue. To check and fix this issue on Linux OS follow these steps:
 - Check your version of Glibc. To check your version of Glibc on Ubuntu, run the command `ldd --version`
 - Install Jemalloc library. Please remember that only version 3.x of the Jemalloc library is tested and will solve the issue. Jemalloc is available as apt package, just execute the command line:
           - `sudo apt-get install libjemalloc1` (Ubuntu)
	    - Locate the installation folder of the Jemalloc library. On Ubuntu 18.04 (64bit) the library should be located in this path: `/usr/lib/x86_64-linux-gnu/libjemalloc.so.1`
	     - After the installation, just run `export LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1` before starting the sidechain node, or run the sidechain node adding `LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1` at the beginning of the java command line as follows:

	     ```
	     LD_PRELOAD=/usr/lib/x86_64-linux-gnu/libjemalloc.so.1 java -cp ./target/sidechains-sdk-simpleapp-0.13.0-RC5.jar:./target/lib/* io.horizen.examples.SimpleApp <path_to_config_file>
	     ```
	      - In the folder `ci` you will find the script `run_sc.sh` to automatically check and use jemalloc library while starting the sidechain node.

**Running Example Apps isolated from Mainchain**

You can use either of the predefined configuration files: [sc_settings.conf](simpleapp/src/main/resources/sc_settings.conf "sc_settings.conf") or [sc_evm_settings.conf](evmapp/src/main/resources/sc_evm_settings.conf "sc_settings.conf"), which contains some genesis data that will start a node with a first mainchain block reference and some coins transferred to the sidechain.

Since there will be no real mainchain node running, with the mainchain block reference included by the genesis just made up, the sidechain will not receive any subsequent mainchain information; the sidechain will run stand-alone, "isolated".

Such a configuration is useful to test a sidechain's logic and behaviour, without any interaction with the mainchain network.


**Running Example Apps connected to a Mainchain node**

This configuration allows the full use of sidechain node functionalities, i.e. create new sidechains, synchronize a sidechain with the mainchain (i.e. mainchain block references are included in sidechain blocks), transfer coins from/to mainchain and vice versa.
Please find a detailed guide [here](./mc_sc_workflow_example.md) on how to set up this complete configuration.


**How to choose the address of the first forward transfers**

**Model: UTXO**

The `sc_create` RPC method requires **two** public keys, that are part of the Vrf and the ed25519 keypairs. To generate a new pair of secret-public keys, you can use the `generatekey` and `generateVrfKey` ScBootstrappingTool commands: e.g. `generatekey {"seed":"myuniqueseed"}` and `generateVrfKey {"seed":"my seed"}`

Then you can put the newly generated public keys as destinations in `sc_create`:

<pre>
 ./zen-cli -regtest \
    sc_create '{
        "version": 1,
        "withdrawalEpochLength": 123,
        <b>"toaddress": "generateKey_PUBLIC_KEY_GOES_HERE"</b>, 
        "amount": 600.0,
        "wCertVk": "02386700000000000004000000000000003c67000000000000bfbc0100000000000c00000000000000016234e8c194d555b5ff6a082286b8241f7a1bc3d4dd71e8a88e6db8729a3de324000001c8bbcabb872abdd97870980b4020f7d6a69bd8650298c0e8984299f6ddc9eb35800001e0b56d7145dd7b006e0d9b2ea1b5bf65c26dafc33dd36fc7675eeb58ba247613800001101c1881ee4e14ce72f762869875980e3e338ce353efd0469d2d74b6254fcc0c0000015e77dbb1889b9d36cb1f345b4290894b5d27c4fea13c19ff202cbffc54d78123800001cadc641bb884c20af1d99f02c6fdb3bc837e8a436a7710252165ec6880a8d23f8000013b43af52de1e9c5e4eb52414ff12c7ec15bc499b83395dea1351640e778bf228000001c3913d5f157b058abe12eda99febaa5bf8cd3eecd768497a12112f22dc3cdd3e000001bb2e8f15c078157fbdd179843702ff55e432cac0fa60ba7459ed918f2c2df91c000001ebcda0614fa4946d2a109bd5871a10966510b9e7fa4e05d2848a4cbe49c46f1480000191aa89e90907e0c714f7c0d931dc6707d7ff2ab4e85f79769b8392d99bc95d1e000001a0877645bea04a236ec766cc0b2439c22c4c8e0fca9755ed070a1d21c42734148000",
        <b>"customData": "generateVrfKey_PUBLIC_KEY_GOES_HERE"</b>,
        "constant": "e258c6b6e0abfbdd29a6acf0b47ac977767d7d92d5f95dd5f374e3252518f436",
        "wCeasedVk": "02d7160000000000000400000000000000db16000000000000a3a40000000000000c0000000000000001240b188009319f7b6d12739001b47917cad72fde9d0ffaa7dabf7836b46bb4300000014f0c379235a1038e5851edd33a6586a8f714c4ec898ddcd066f7ae43b7a0f317800001a5efe5004f93193ec208703cd2b9b97d4a323ba0f7b7c1454ba435a3de51b130000001a98bfe4439a478c0dddf5e75f504e21f7681a022253a19bc9b8801c59e3aef39800001487a4d0201582fe4d8eafa36fc5d8d65dc9620fdfa204f19e63cd3a69962ec3c800001263ca5ab720ee91d4db9b668a8fb3304453ac58fef70587681c567b1b27fa332800001fa0c2b7b756fab8968631267404b93c76baf329f05be068f1397e728c2a5960e80000121483d74799c0d77720e12a913ebae05014d521b0dea4235263ccd1b5c97a50c000001b3c7a90ddf7f74f4f24013a6ab97f5b791da9bb0dd109c079db6ff8511d2233780000175a25117ecd062b6eff590623ad2ad97f71afb4ba7f1951b4ba97cb49f87ae3200000171543933865335ccc19f7162f34eb43cd4554b2a61ab33c011ea66b60d2cfc1a0000013799cf00e1c8194ea1195f047330f55056d713b15448565e9158cf37a644153e8000",
        "vFieldElementCertificateFieldConfig": []
    }'
</pre>

**Model: Account**

The `sc_create` RPC method requires **three** public keys, that are part of the Vrf, ed25519 and Secp256k1 keypairs. To generate a new pair of secret-public keys, you can use the `generatekey`, `generateVrfKey` and `generateAccountKey` ScBootstrappingTool commands:

`generatekey {"seed":"myuniqueseed"}`

`generateVrfKey {"seed":"my seed"}`

`generateAccountKey {"seed":"my seed"}`

Then you can put the newly generated public keys as destinations in `sc_create`, note customData will be a concatenation of the Vrf and ed25519 public keys:

<pre>
 ./zen-cli -regtest \
    sc_create '{
        "version": 1,
        "withdrawalEpochLength": 123,
        <b>"toaddress": "generateAccountKey_PUBLIC_KEY_GOES_HERE"</b>, 
        "amount": 600.0,
        "wCertVk": "02386700000000000004000000000000003c67000000000000bfbc0100000000000c00000000000000016234e8c194d555b5ff6a082286b8241f7a1bc3d4dd71e8a88e6db8729a3de324000001c8bbcabb872abdd97870980b4020f7d6a69bd8650298c0e8984299f6ddc9eb35800001e0b56d7145dd7b006e0d9b2ea1b5bf65c26dafc33dd36fc7675eeb58ba247613800001101c1881ee4e14ce72f762869875980e3e338ce353efd0469d2d74b6254fcc0c0000015e77dbb1889b9d36cb1f345b4290894b5d27c4fea13c19ff202cbffc54d78123800001cadc641bb884c20af1d99f02c6fdb3bc837e8a436a7710252165ec6880a8d23f8000013b43af52de1e9c5e4eb52414ff12c7ec15bc499b83395dea1351640e778bf228000001c3913d5f157b058abe12eda99febaa5bf8cd3eecd768497a12112f22dc3cdd3e000001bb2e8f15c078157fbdd179843702ff55e432cac0fa60ba7459ed918f2c2df91c000001ebcda0614fa4946d2a109bd5871a10966510b9e7fa4e05d2848a4cbe49c46f1480000191aa89e90907e0c714f7c0d931dc6707d7ff2ab4e85f79769b8392d99bc95d1e000001a0877645bea04a236ec766cc0b2439c22c4c8e0fca9755ed070a1d21c42734148000",
        <b>"customData": "generateVrfKey_PUBLIC_KEY_GOES_HERE+generatekey_PUBLIC_KEY_GOES_HERE"</b>,
        "constant": "e258c6b6e0abfbdd29a6acf0b47ac977767d7d92d5f95dd5f374e3252518f436",
        "wCeasedVk": "02d7160000000000000400000000000000db16000000000000a3a40000000000000c0000000000000001240b188009319f7b6d12739001b47917cad72fde9d0ffaa7dabf7836b46bb4300000014f0c379235a1038e5851edd33a6586a8f714c4ec898ddcd066f7ae43b7a0f317800001a5efe5004f93193ec208703cd2b9b97d4a323ba0f7b7c1454ba435a3de51b130000001a98bfe4439a478c0dddf5e75f504e21f7681a022253a19bc9b8801c59e3aef39800001487a4d0201582fe4d8eafa36fc5d8d65dc9620fdfa204f19e63cd3a69962ec3c800001263ca5ab720ee91d4db9b668a8fb3304453ac58fef70587681c567b1b27fa332800001fa0c2b7b756fab8968631267404b93c76baf329f05be068f1397e728c2a5960e80000121483d74799c0d77720e12a913ebae05014d521b0dea4235263ccd1b5c97a50c000001b3c7a90ddf7f74f4f24013a6ab97f5b791da9bb0dd109c079db6ff8511d2233780000175a25117ecd062b6eff590623ad2ad97f71afb4ba7f1951b4ba97cb49f87ae3200000171543933865335ccc19f7162f34eb43cd4554b2a61ab33c011ea66b60d2cfc1a0000013799cf00e1c8194ea1195f047330f55056d713b15448565e9158cf37a644153e8000",
        "vFieldElementCertificateFieldConfig": []
    }'
</pre>

**Secrets**

The secrets generated for each key pair (2 for UTXO, 3 for Account) must be placed into the configuration file, in the `wallet.genesisSecrets` section.

Please note: if you do not specify `genesisSecrets` properly, you will not see that balance in the wallet.

**How to choose the secret in ScBootstrapping `genesisinfo` command**

Just use a secret generated by the `generatekey` command.

**How to add authentication to the endpoints**

We support the Basic Authentication inside our REST interface.
You can add an api key hash inside the config file in the section: *restApi.apiKeyHash* which should be the **BCrypt** Hash of the password used in the Basic Auth.
You can calculate this Hash using the ScBootstrapping tool with the command `encodeString` (e.g. `encodeString {"string": "a8q38j2f0239jf2olf20f"}`).

Example:

HTTP request:

        "Authorization": "Basic a8q38j2f0239jf2olf20f"

Config file:

        restApi {
            "apiKeyHash": "$2y$08$sB9Zrn4S2/YYfJ8/lhNxXO/Ku7uciazel3hx4bvjMs7nrqcLqMS0y"
        }

**Sidechain-related RPC commands in Mainchain**
1. `sc_create` - to create a new sidechain, please refer to [example](./mc_sc_workflow_example.md) for more details.
2. `sc_send` - to send a forward transfers to specific addresses in a sidechain.

For more info please check the Horizen Mainchain [zen](https://github.com/HorizenOfficial/zen/) repository and guides.


**Sidechain HTTP API**

The node API address is specified in the configuration file, `network.bindAddress` section.
The description of all basic API, with examples, is available as a simple web interface; just type `network.bindAddress` in any browser to access it.
