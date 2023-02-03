// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract DelegateReceiver {
    // NOTE: storage layout must be the same as contract DelegateCaller
    uint public num;
    address public sender;
    uint public value;

    function setVars(uint _num) public payable {
        num = _num;
        sender = msg.sender;
        value = msg.value;
    }
}

contract DelegateCaller {
    uint public num;
    address public sender;
    uint public value;

    // This function does not report any error of the delegated call
    function setVars(address _contract, uint _num) public payable {
        // DelegateCaller's storage is set, DelegateReceiver is not modified.
        (bool success, bytes memory result) = _contract.delegatecall(
            abi.encodeWithSignature("setVars(uint256)", _num)
        );
    }

    // This function asserts that the delegated call succeeds,
    // if not it will revert with the same reason as the nested call
    function setVarsAssert(address _contract, uint _num) public payable {
        // DelegateCaller's storage is set, DelegateReceiver is not modified.
        (bool success, bytes memory result) = _contract.delegatecall(
            abi.encodeWithSignature("setVars(uint256)", _num)
        );
        if (success == false) {
            assembly {
                revert(add(result, 32), mload(result))
            }
        }
    }
}
