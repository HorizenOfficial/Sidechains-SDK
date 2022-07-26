//SPDX-License-Identifier: Unlicense
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC721/presets/ERC721PresetMinterPauserAutoId.sol";

contract TestERC721 is ERC721PresetMinterPauserAutoId {
    constructor()
    ERC721PresetMinterPauserAutoId("Test ERC721", "TNFT", "metadata-uri")
    {}
}
