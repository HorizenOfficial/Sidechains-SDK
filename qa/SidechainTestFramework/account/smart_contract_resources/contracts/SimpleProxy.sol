// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

contract SimpleProxy {


    function doStaticCall(address to, bytes calldata data) public returns (bytes memory) {
	uint256 gasRemaining = gasleft();
        (bool success, bytes memory result) = to.staticcall{gas:gasRemaining}(
            data
        );
        require(success, "staticcall should work");
        return result;
    }


    function doCall(address to, uint value, bytes calldata data) public returns (bytes memory) {
	uint256 gasRemaining = gasleft();
        (bool success, bytes memory result) = to.call{value:value, gas:gasRemaining}(
            data
        );
        require(success, "call should work");
        return result;
    }

    fallback() external payable {
    }

}
