# Truffle

- Update `.env` file
- Use `truffle compile` to compile Solidity contracts in the project
- Use `truffle migrate --network zen` to execute deployment scripts in the project
- Use `truffle console --network zen` to open an interactive Javascript console

Within the console the fake contracts can be used like this:

```
var w = await WithdrawalRequests.at("0x0000000000000000000011111111111111111111");
var f = await ForgerStakes.at("0x0000000000000000000022222222222222222222");

w.getWithdrawalRequests.call(1);
w.submitWithdrawalRequest.sendTransaction("0xdbcbaf2b14a48cfc24941ef5acfdac0a8c590255", { value: 1000000000000 });

f.getAllForgersStakes.call();
f.delegate.estimateGas("0x11a95db17906bfeeebc308cc22938832c0606bea4fcff1e1170083922a551588", "0x859918d7be65ae7e2e1191289633f8252ad1b2aef4b9f92d65f1e18fd8b29416", "0x80", "0x7507Cebb915af00019be3a5FE8897b2eE115B166", { nonce:0, value:0 });
```
