**Evm Simple App**
---------

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

EVM Simple App follows the same instructions found [HERE](../simpleapp/README.md) but any references to
*simpleapp* should be replaced with *evmapp*:

* (Linux)
    ```
    cd ./Sidechains-SDK/examples/evmapp
    java -cp ./target/sidechains-sdk-evmapp-0.6.0.jar:./target/lib/* com.horizen.examples.evmapp <path_to_config_file>
    ```