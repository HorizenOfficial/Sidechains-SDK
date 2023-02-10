# Truffle

- Create `.env` file from `.env.example`
- Use `truffle compile` to compile Solidity contracts in the project
- Use `truffle migrate --network zen` to execute deployment scripts in the project
- Use `truffle console --network zen` to open an interactive Javascript console

Within the console the native contracts can be used like this:

```
var w = await WithdrawalRequests.at("0x0000000000000000000011111111111111111111");
var f = await ForgerStakes.at("0x0000000000000000000022222222222222222222");

w.getBackwardTransfers.call(1);
w.backwardTransfer.sendTransaction("0xdbcbaf2b14a48cfc24941ef5acfdac0a8c590255", { value: 1000000000000 });

f.getAllForgersStakes.call();
f.delegate.estimateGas("0x11a95db17906bfeeebc308cc22938832c0606bea4fcff1e1170083922a551588", "0x859918d7be65ae7e2e1191289633f8252ad1b2aef4b9f92d65f1e18fd8b29416", "0x80", "0x7507Cebb915af00019be3a5FE8897b2eE115B166", { nonce:0, value:0 });
```

## Contract deployments

To add more contracts add Solidity sources to `/contracts` and add a deployment script to `/migrations`.

```
> truffle migrate --network zen

Compiling your contracts...
===========================
> Everything is up to date, there is nothing to compile.


Starting migrations...
======================
> Network name:    'zen'
> Network id:      1997
> Block gas limit: 30000000 (0x1c9c380)


1_deploy_storage.js
===================

   Replacing 'Storage'
   -------------------
   > transaction hash:    0xddb32a384eaf14a5798bdad250dbad4cfc595222518fd97d27938b69a57ccaaf
   > Blocks: 1            Seconds: 4
   > contract address:    0x84F0dCE10408136E527520c9A28312f87a5c48BE
   > block number:        5
   > block timestamp:     1664036313
   > account:             0x7507Cebb915af00019be3a5FE8897b2eE115B166
   > balance:             24.67939760873495005
   > gas used:            195175 (0x2fa67)
   > gas price:           3.83984375 gwei
   > value sent:          0 ETH
   > total cost:          0.00074944150390625 ETH

   > Saving artifacts
   -------------------------------------
   > Total cost:     0.00074944150390625 ETH


2_deploy_erc20.js
=================

   Replacing 'TestERC20'
   ---------------------
   > transaction hash:    0xd8ea8be1124f1dab86bb6b0854eb96236e3e79aa2947241693c054aaa66b0d72
   > Blocks: 0            Seconds: 0
   > contract address:    0xEC32B7efCe2Abc45878A4F3b55E61686C9E7A1c8
   > block number:        6
   > block timestamp:     1664036433
   > account:             0x7507Cebb915af00019be3a5FE8897b2eE115B166
   > balance:             24.675867354601546612
   > gas used:            1171259 (0x11df3b)
   > gas price:           3.672832228 gwei
   > value sent:          0 ETH
   > total cost:          0.004301837802535052 ETH

   > Saving artifacts
   -------------------------------------
   > Total cost:     0.004301837802535052 ETH


3_deploy_erc721.js
==================

   Replacing 'TestERC721'
   ----------------------
   > transaction hash:    0x77472b38887885a7257bed2088390fdd525f1f309bc1964d3c13560ffa1cf0a5
   > Blocks: 0            Seconds: 0
   > contract address:    0xB9749FE4F0B15d1C09C1F0Ecf78058b1edE713ba
   > block number:        7
   > block timestamp:     1664036553
   > account:             0x7507Cebb915af00019be3a5FE8897b2eE115B166
   > balance:             24.662995637893004414
   > gas used:            4356166 (0x427846)
   > gas price:           3.528135764 gwei
   > value sent:          0 ETH
   > total cost:          0.015369145058520824 ETH

   > Saving artifacts
   -------------------------------------
   > Total cost:     0.015369145058520824 ETH

Summary
=======
> Total deployments:   3
> Final cost:          0.020420424364962126 ETH
```
