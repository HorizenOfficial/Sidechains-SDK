// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract ReceivingEther {

    // Helper function to check the balance of this contract
    function getBalance() public view returns (uint) {
        return address(this).balance;
    }
}