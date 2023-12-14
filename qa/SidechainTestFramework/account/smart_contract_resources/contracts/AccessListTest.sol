// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

/**
 * @title AccessListTest
 * @dev Used to test the change in gas used when accessing the state of an address in access list.
 */
contract AccessListTest {


    function getBalance(address dest) public view returns (uint) {
        return dest.balance;
    }
}
