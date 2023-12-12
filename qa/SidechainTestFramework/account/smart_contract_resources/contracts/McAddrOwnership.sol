// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;


// contract address: 0x0000000000000000000088888888888888888888
interface McAddrOwnership {

    struct McAddrOwnershipData {
        address scAddress;
        bytes3 mcAddrBytes1;
        bytes32 mcAddrBytes2;
    }

    function getAllKeyOwnerships() external view returns (McAddrOwnershipData[] memory);

    function getKeyOwnerships(address scAddress) external view returns (McAddrOwnershipData[] memory);

    function getKeyOwnerScAddresses() external view returns (address[] memory);

    function sendKeysOwnership(bytes3 mcAddrBytes1, bytes32 mcAddrBytes2, bytes24 signature1, bytes32 signature2, bytes32 signature3) external returns (bytes32);

    function sendMultisigKeysOwnership(string memory mcMultisigAddress, string memory redeemScript, string[] memory mcSignatures) external returns (bytes32);

    function removeKeysOwnership(bytes3 mcAddrBytes1, bytes32 mcAddrBytes2) external returns (bytes32);
}
