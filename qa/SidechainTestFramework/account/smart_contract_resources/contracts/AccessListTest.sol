// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

/**
 * @title Storage
 * @dev Store & retrieve value in a variable
 */
contract AccessListTest {


    function getBalance(address dest) public view returns (uint) {
        return dest.balance;
    }
}
