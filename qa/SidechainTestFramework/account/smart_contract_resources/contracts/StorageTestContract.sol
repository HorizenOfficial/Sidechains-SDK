//SPDX-License-Identifier: Unlicense
pragma solidity ^0.8.0;

contract StorageTestContract {
    string private _storage;

    function set(string calldata value) external {
        _storage = value;
    }

    function get() external view returns (string memory){
        return _storage;
    }
}
