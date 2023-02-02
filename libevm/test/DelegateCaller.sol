// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

contract DelegateCaller {
    uint public number;

    function store(uint num, address _contract) public {
        (bool success, bytes memory data) = _contract.delegatecall(
            abi.encodeWithSignature("store(uint256)", num)
        );
    }

    function retrieve() public view returns (uint){
        return number;
    }
}
