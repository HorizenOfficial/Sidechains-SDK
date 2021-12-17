**STF**
---------

STF is test framework to run integration tests.

It is possible to test a SC node or nodes with or without real MC node connection.

**Requirements**

Install Python 3

**Additional settings**

Setup these environment variables.

Example for Linux environment:

```
BITCOINCLI="/home/user/zen/zen-cli"
BITCOIND="/home/user/zen/zend"
SIDECHAIN_SDK="/home/user/Sidechains-SDK"
```

**Execution**

You can run all tests using command.

```
python run_sc_tests.py
```
    
Or run individual test using command

```
python <test.py>
```

**Template configuration files**

Template configuration files located in resources directory. 

File template.conf is the template for testing SC node(s) connected to MC node(s).

File template_predefined_genesis.conf is the template for testing SC node(s) standalone mode (without any connections to MC node(s)).




