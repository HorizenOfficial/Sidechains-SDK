// SPDX-License-Identifier: MIT

pragma solidity >=0.8.0 <0.9.0;

/**
 * @title OpCodes
 * @dev uses specific opcodes for testing
 */
contract OpCodes {
    function getGasPrice() external view returns (uint256) {
        return tx.gasprice;
    }

    function getChainID() external view returns (uint256) {
        return block.chainid;
    }

    function getCoinbase() external view returns (address) {
        return block.coinbase;
    }

    function getGasLimit() external view returns (uint256) {
        return block.gaslimit;
    }

    function getBlockNumber() external view returns (uint256) {
        return block.number;
    }

    function getTime() external view returns (uint256) {
        return block.timestamp;
    }

    function getBaseFee() external view returns (uint256) {
        return block.basefee;
    }

    function getRandom() external view returns (uint256) {
        // TODO: use block.prevrandao as soons as Solidity has this implemented
        // note: the emitted opcode remains the same, so there is no functional difference - it's just a cosmetic rename
        return block.difficulty;
    }

    function getBlockHash() external view returns (bytes32) {
        return blockhash(block.number - 1);
    }
}
