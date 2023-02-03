// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

contract DelegateCaller {
    uint public number;

    function store(address _contract, uint _num) public {
        (bool success, bytes memory result) = _contract.delegatecall(
            abi.encodeWithSignature("store(uint256)", _num)
        );
        if (success == false) {
            assembly {
                revert(add(result, 32), mload(result))
            }
        }
    }

    function retrieve() public view returns (uint){
        return number;
    }
}
