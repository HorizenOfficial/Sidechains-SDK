// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

contract DelegateReceiver {
    uint public number;

    function store(uint num) public {
        number = num;
    }

    function retrieve() public view returns (uint){
        return number;
    }
}
