// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;


// contract address: 0x0000000000000000000044444444444444444444
interface CertKeyRotation {

    // Event declaration
    // Up to 3 parameters can be indexed.
    // Indexed parameters helps you filter the logs by the indexed parameter
    event SubmitKeyRotation(uint32 indexed keyType, uint32 indexed keyIndex, bytes32 newKeyValue_32, bytes1 newKeyValue_1, uint32 epochNumber);

    function submitKeyRotation(uint32 key_type, uint32 index, bytes32 newKey_1, bytes1 newKey_2, bytes32 signKeySig_1, bytes32 signKeySig_2, bytes32 masterKeySig_1, bytes32 masterKeySig_2, bytes32 newKeySig_1, bytes32 newKeySig_2) external returns (uint32, uint32, bytes32, bytes1, bytes32, bytes32, bytes32, bytes32);
}
