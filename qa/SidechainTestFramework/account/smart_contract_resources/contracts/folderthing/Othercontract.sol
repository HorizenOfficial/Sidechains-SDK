//SPDX-License-Identifier: Unlicense
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract ExampleERC20Contract is ERC20 {
    string private greeting;

    constructor() ERC20("Example ERC20", "EE2"){
    }

    function greet() public view returns (string memory) {
        return name();
    }
}
