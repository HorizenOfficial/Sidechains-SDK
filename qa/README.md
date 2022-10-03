**STF**
---------

STF is test framework to run integration tests.

It is possible to test a SC node or nodes with or without real MC node connection.

**Requirements**

- Install Python 3.5 or newer
- Install requirements via `pip3 install -r ./SidechainTestFramework/account/requirements.txt` or a similar way.
    - You can also manually install the requirements listed

- Install node >= 16.13.2 and npm >= 8.1.2
    - eg. by using nvm (node version manager)
- Install yarn globally (eg. via `npm -i -g yarn`)

**Additional settings**

Setup these environment variables.

Example for Linux environment:

```
BITCOINCLI="/home/user/zen/zen-cli"
BITCOIND="/home/user/zen/zend"
SIDECHAIN_SDK="/home/user/Sidechains-SDK"
```

**Execution**

You can run all tests by running the following command from the qa directory:

```
./run_sc_tests.sh
```

The log output for this test run can be found in the qa directory with the name "sc_test.log".

Or run individual test using command:

```
python3 <test.py> --logconsolelevel=info
```

**Template configuration files**

Template configuration files located in resources directory. 

File template.conf is the template for testing SC node(s) connected to MC node(s).

File template_predefined_genesis.conf is the template for testing SC node(s) standalone mode (without any connections to MC node(s)).

**Debugging**

In order to run a python test for debugging SDK application, the following procedure can be applied:

1) When starting a sc node in the py test, add the option '_-agentlib_' to the _extra_args_ list in the relevant API
   call, for example:
   ```
   start_sc_nodes(1, self.options.tmpdir, extra_args=['-agentlib'])
   ```
   This will cause the simpleApp process to start with the debug agent acting as a server. The process will wait until
   the debugger has been connected.


2) Run the py test.

   If needed, in order to increase the rest API timeout, use the optional argument _--restapitimeout=<
   timeout_value_in_secs>_, for example:
   ```
   python sc_forward_transfer.py --restapitimeout=200
   ```

3) Attach the debugger to the simpleApp process.

   For instance, if using IntelliJ:


- Press `Ctrl+Alt+F5` or choose **_Run | Attach to Process_** from the main menu.
- Select the process to attach to from the list of the running local processes. The processes launched with the debug
  agent are shown under _**Java**_. Those that don't use a debug agent are listed under **_Java Read Only_**.
