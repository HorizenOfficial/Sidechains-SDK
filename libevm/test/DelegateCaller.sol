// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

contract DelegateCaller {
    uint public number;

    function store(uint num, address _contract) public {
        (bool success, ) = _contract.delegatecall(
            abi.encodeWithSignature("store(uint256)", num)
        );
        if (success == false) {
            assembly {
                let ptr := mload(0x40)
                let size := returndatasize()
                returndatacopy(ptr, 0, size)
                revert(ptr, size)
            }
        }
    }

    function retrieve() public view returns (uint){
        return number;
    }
}
