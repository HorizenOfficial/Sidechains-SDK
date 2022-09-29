//SPDX-License-Identifier: Unlicense
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC721/presets/ERC721PresetMinterPauserAutoId.sol";

contract TestERC721 is ERC721PresetMinterPauserAutoId {
    constructor(string memory name, string memory symbol, string memory metadataURI)
    ERC721PresetMinterPauserAutoId(name, symbol, metadataURI)
    {}

    function mint(uint256 id) public payable {
        if (msg.value != 1) revert("Needs money");
        _mint(msg.sender, id);
    }
}
