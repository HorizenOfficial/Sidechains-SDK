**STF**
---------

STF is test framework to run integration tests.

It is possible to test a SC node or nodes with or without real MC node connection.

**Requirements**

Install Python 2.7

**Additional settings**

Setup two environment variables.

Example for Linux environment:

```
BITCOINCLI="/home/user/zen/zen-cli"
BITCOIND="/home/user/zen/zend"
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

Template configuration files are located in directory resources. 

File template.conf is template for tests of SC node(s) is connected to MC node(s).

File template_predefined_genesis.conf is template for test of SC node(s) in standalone mode(without connection to MC node).




