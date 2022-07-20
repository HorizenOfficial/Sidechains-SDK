//SPDX-License-Identifier: Unlicense
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract ExampleContract {
    string private _storage;
    constructor(){
    }
    function set(string calldata value) external {
        _storage = value;
    }

    function get() external view returns (string memory){
        return _storage;
    }
}
