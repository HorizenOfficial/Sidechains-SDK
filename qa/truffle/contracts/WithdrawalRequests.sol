pragma solidity 0.8.17;

// fake contract address: 0x0000000000000000000011111111111111111111
// var w = await WithdrawalRequests.at("0x0000000000000000000011111111111111111111");
// w.submitWithdrawalRequest.sendTransaction("0xdbcbaf2b14a48cfc24941ef5acfdac0a8c590255", { value: 1000000000000 });
interface WithdrawalRequests {
    function getWithdrawalRequests(uint32) external;

    function submitWithdrawalRequest(bytes20) external payable;
}
