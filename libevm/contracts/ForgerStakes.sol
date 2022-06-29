// SPDX-License-Identifier: GPL-3.0

pragma solidity >=0.7.0 <0.9.0;

contract ForgerStakeContract {

    struct Stake {
        uint256 value;
        uint256 forgerPublicKey;
        uint256 vrfPublicKey;
        address owner;
    }

    Stake[] private stakes;

    constructor() {
    }

    function getStakes() public view returns (Stake[] memory) {
        return stakes;
    }

    function addStake(uint256 forgerPublicKey, uint256 vrfPublicKey, address owner) public payable returns (uint256) {
        uint256 stakeId = 0;
        return stakeId;
    }

    function removeStake(uint256 stakeId, bytes calldata signature) public {
    }
}
