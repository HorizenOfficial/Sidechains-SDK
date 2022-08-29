**STF**
---------

STF is test framework to run integration tests.

It is possible to test a SC node or nodes with or without real MC node connection.

**Requirements**

- Install Python 3.5 or newer
```
sudo apt install python
sudo apt-get -y install python3-pip
```

- Install requirements via `pip3 install -r ./SidechainTestFramework/account/requirements.txt` or a similar way.
    - You can also manually install the requirements listed

- Install node >= 14.0.0 and npm >= 7.0.0
    - eg. by using nvm (node version manager)
- Install yarn globally (eg. via `npm -i -g yarn`)

**Additional settings**

1. Example for Linux:
```
sudo nano /etc/environment
```
2. In this file after Path from the new line put Environment variables:
```
BITCOINCLI=/home/yourName/yourProjectDirectory/zen/src/zen-cli
BITCOIND=/home/yourName/yourProjectDirectory/zen/src/zend
SIDECHAIN_SDK=/home/yourName/yourProjectDirectory/Sidechains-SDK
```
change yourName and yourProjectDirectory to the relevant one.
4. Save file, exit and restart your computer.
5. Then make sure that environment variables are set:
```
echo $BITCOINCLI
echo $BITCOIND
echo $SIDECHAIN_SDK
```
verify that all path are valid and are referenced to existing files.

**Execution**
1. Install Maven, go to root folder of Sidechain-SDK and run Maven to clean previous and build a new JAR

*Parallel Testing:* Tests can be parallelized to reduce test run time by specifying the *-parallel* flag and passing in an integer value:

```
./run_sc_tests.sh -parallel=2
```

*Note: Setting this parallel value too high can result in test failures due to resource usage on your machine.*

You can use _-evm_only_ or _-utxo_only_ options for running only a subset of the tests.

The log output for this test run can be found in the qa directory with the name "sc_test.log".

2. You can run all tests using command.
```
cd qa
python run_sc_tests.py
```
Or run individual test using command
```
cd qa
python <test.py>
```
replacing <test.py> with the name of test that you want to execute.


The following command-line options can be used in addition:
- `--nocleanup`: Do not remove sc_test.* datadirs on exit or error.
- `--noshutdown`: Don't stop Mainchain and Sidechain nodes after the test execution.
- `--restapitimeout=<timeout>`: Timeout in seconds for rest API execution.
- `--logfilelevel=<log level>`: log4j log level for sidechain node logging on log file. Valid values are: fatal, error, warn, info, debug, trace, off, all 
- `--logconsolelevel=<log level>`: log4j log level for sidechain node logging on console.Valid values are: fatal, error, warn, info, debug, off, all
- `--testlogfilelevel=<log level>`: log level for test logging on log file. Valid values are: fatal, error, warn, info, debug, notset
- `--testlogconsolelevel=<log level>`: log level for test logging on console. Valid values are: fatal, error, warn, info, debug, notset

Additional options can be found in sc_test_framework.py file.


**Template configuration files**

Template configuration files located in resources directory. 

File template.conf is the template for testing SC node(s) connected to MC node(s).

File template_predefined_genesis.conf is the template for testing SC node(s) standalone mode (without any connections to MC node(s)).

**UTXO vs EVM Sidechain**

STF can be used for testing both Sidechain models, i.e UTXO or EVM Sidechains. 
In order to select an EVM sc node in the py test, specify the corresponding binary in '_start_sc_nodes_' API
   call, for example:
   ```
   start_sc_nodes(1, self.options.tmpdir, binary=[EVM_APP_BINARY])
   ```

   For UTXO Sidechain, '_binary_'  value is SIMPLE_APP_BINARY, e.g.:
  ```
   start_sc_nodes(1, self.options.tmpdir, binary=[SIMPLE_APP_BINARY])
  ```
However, in case of UTXO Sidechain, '_binary_' argument could be omitted because it is the default.  

**Debugging**

In order to run a python test for debugging SDK application, the following procedure can be applied:
1. Open PyCharm
2. When starting a sc node in the py test, add the option '_-agentlib_' to the _extra_args_ list in the relevant API
   call, for example:
   ```
   start_sc_nodes(1, self.options.tmpdir, extra_args=['-agentlib'])
   ```
   This will cause the simpleApp process to start with the debug agent acting as a server. The process will wait until
   the debugger has been connected.
   
   As an alternative, the optional argument _--debugnode=\<i\>_ can be used for the same purpose, where _i_ is the index of the node to be debugged
3. Open Run Configuration of Python script that you want to debug
4. Put in parameters: --debugnode=0 --restapitimeout=99999
If you use console, run with parameters, for example:
```
   python sc_forward_transfer.py --debugnode=0  --restapitimeout=200
   ```
   (instead of 0 you can put any number of node present in Python script you want to debug)
5. Click debug
6. Open Intellij Idea
7. Put a breakpoint in Java/Scala code in the beginning of API method
8. Press `Ctrl+Alt+F5` or  in upper main menu of Intellij Idea go Run->"Attach to process..."
The processes launched with the debug agent are shown under _**Java**_
9. Select the local process containing "node" and select the node with proper number
10. Wait a little, execution should stop on breakpoint in Java/Scala code
