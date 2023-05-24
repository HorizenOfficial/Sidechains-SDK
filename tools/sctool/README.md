**Bootstrapping Tool** 
---------

This module offers a way to create a sidechain's configuration file and some utilities.

**Classes**
 - ScBootstrappingToolCommandProcessor: a command line processor to interact with the tool;
 - AbstractScBootstrappingTool: an abstract class offering an extensible entry point;
 - AbstractUTXOModel: an abstract class offering base configuration for an utxo sidechain;
 - AbstractAccountModel: an abstract class offering base configuration for an account sidechain;
 - DefaultUTXOBootstrappingTool;
 - UTXOModel: base concrete implementation of SidechainModel interface;
 - DefaultAccountBootstrappingTool;
 - AccountModel: base concrete implementation of SidechainModel interface;

**Usage**

As an example of usage you can refer to:
 - [UTXO bootstrapping tool](examples/utxo/utxoapp_sctool)
 - [Account bootstrapping tool](examples/account/evmapp_sctool)