// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract BlockHash {
    function get(uint offset) external view returns (bytes32) {
        return blockhash(block.number - offset);
    }
}
