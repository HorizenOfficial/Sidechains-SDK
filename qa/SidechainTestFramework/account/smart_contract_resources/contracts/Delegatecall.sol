// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

// NOTE: Deploy this contract first
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

    function setVars(address _contract, uint _num) public payable {
        // DelegateCaller's storage is set, DelegateReceiver is not modified.
        (bool success, bytes memory data) = _contract.delegatecall(
            abi.encodeWithSignature("setVars(uint256)", _num)
        );
    }

    function getNum() public returns (uint) {
        return num;
    }
}
