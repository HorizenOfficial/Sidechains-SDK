//SPDX-License-Identifier: Unlicense
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract ExampleERC20 is ERC20 {
    string private greeting;

    struct InputStruct {
        address name;
        uint256 id;
    }
    constructor() ERC20("Example ERC20", "EE2"){
    }

    function greet(InputStruct calldata input) public view returns (address) {
        return input.name;
    }
}
