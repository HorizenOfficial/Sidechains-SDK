// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

type MCAddress is bytes20;

// contract address: 0x0000000000000000000011111111111111111111
interface WithdrawalRequests {

    struct WithdrawalRequest {
        MCAddress mcAddress;
        uint256 value;
    }

    // Event declaration
    // Up to 3 parameters can be indexed.
    // Indexed parameters helps you filter the logs by the indexed parameter
    event AddWithdrawalRequest(address indexed sender, bytes20 indexed mcDest, uint256 value, uint32 epochNumber);


    function getBackwardTransfers(uint32 withdrawalEpoch) external view returns (WithdrawalRequest[] memory);

    function backwardTransfer(MCAddress mcAddress) external payable returns (WithdrawalRequest memory);
}
