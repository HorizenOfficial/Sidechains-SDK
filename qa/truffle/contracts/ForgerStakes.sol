// SPDX-License-Identifier: MIT

pragma solidity 0.8.17;

// fake contract address: 0000000000000000000022222222222222222222
// var f = await ForgerStakes.at("0x0000000000000000000022222222222222222222");
// f.delegate.estimateGas("0x11a95db17906bfeeebc308cc22938832c0606bea4fcff1e1170083922a551588", "0x859918d7be65ae7e2e1191289633f8252ad1b2aef4b9f92d65f1e18fd8b29416", "0x80", "0x7507Cebb915af00019be3a5FE8897b2eE115B166");
interface ForgerStakes {

    struct AccountForgingStakeInfo {
        bytes32 stakeId;
        uint256 stakedAmount;
        address ownerPublicKey;
        bytes32 blockSignPublicKey;
        bytes32 vrf1;
        bytes1 vrf2;
    }

    function getAllForgersStakes() external returns (AccountForgingStakeInfo[] memory);

    function delegate(bytes32, bytes32, bytes1, address) external;

    function withdraw(bytes32, bytes1, bytes32, bytes32) external;
}
