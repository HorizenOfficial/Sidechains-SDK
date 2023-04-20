// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

import "./ForgerStakes.sol";

contract NativeInterop {
    ForgerStakes nativeContract = ForgerStakes(0x0000000000000000000022222222222222222222);

    function GetForgerStakes() public view returns (ForgerStakes.StakeInfo[] memory){
        // set an explicit gas limit of 10000 for this call for the unit test
        return nativeContract.getAllForgersStakes{gas: 100000}();
    }
}
