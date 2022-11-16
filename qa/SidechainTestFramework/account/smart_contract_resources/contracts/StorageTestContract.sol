//SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract StorageTestContract {
    string private _storage;

    event StorageSet(address indexed operator, string indexed newValue);

    constructor(string memory initialValue) {
        set(initialValue);
    }

    function set(string memory value) public {
        _storage = value;
        emit StorageSet(msg.sender, value);
    }

    function get() external view returns (string memory){
        return _storage;
    }
}
