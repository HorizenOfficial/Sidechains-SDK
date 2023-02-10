//SPDX-License-Identifier: MIT
pragma solidity ^0.8.18;

contract PrevrandaoTestContract {

    uint public persistentRandom;

    function persistRandom() public payable {
        persistentRandom = block.prevrandao;
    }

    function getRandomByCallingPrevrandao() external view returns (uint256) {
        uint256 randomNumber = block.prevrandao;

        return randomNumber;
    }

    function getRandomByCallingDifficulty() external view returns (uint256) {
        uint256 randomNumber = block.difficulty;

        return randomNumber;
    }
}