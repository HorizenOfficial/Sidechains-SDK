// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

type StakeID is bytes32;

// contract address: 0x0000000000000000000022222222222222222222
interface ForgerStakes {

    struct StakeInfo {
        StakeID stakeId;
        uint256 stakedAmount;
        address owner;
        bytes32 publicKey;
        bytes32 vrf1;
        bytes1 vrf2;
    }

    // Event declaration
    // Up to 3 parameters can be indexed.
    // Indexed parameters helps you filter the logs by the indexed parameter
    event DelegateForgerStake(address indexed sender, address indexed owner, bytes32 stakeId, uint256 value);
    event WithdrawForgerStake(address indexed owner, bytes32 stakeId);
    event StakeUpgrade(uint32 oldVersion, uint32 newVersion);
    event OpenForgerList(uint32 indexed forgerIndex, address sender, bytes32 blockSignProposition);

    function getAllForgersStakes() external view returns (StakeInfo[] memory);

    function delegate(bytes32 publicKey, bytes32 vrf1, bytes1 vrf2, address owner) external payable returns (StakeID);

    function withdraw(StakeID stakeId, bytes1 signatureV, bytes32 signatureR, bytes32 signatureS) external returns (StakeID);

    function openStakeForgerList(uint32 forgerIndex, bytes32 signature1, bytes32 signature2) external returns (bytes memory);
    
    function stakeOf(address owner) external view returns (uint256);

    function getPagedForgersStakesByUser(address owner, int32 startIndex, int32 pageSize) external view returns (int32, StakeInfo[] memory);

    function upgrade() external returns (uint32);

    function getPagedForgersStakes(int32 startIndex, int32 pageSize) external view returns (int32, StakeInfo[] memory);
}
