// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

import "./ForgerStakes.sol";

contract NativeInterop {
    ForgerStakes nativeContract = ForgerStakes(0x0000000000000000000022222222222222222222);

    function GetForgerStakes() public view returns (ForgerStakes.StakeInfo[] memory) {
        // set an explicit gas limit of 10000 for this call for the unit test
        return nativeContract.getAllForgersStakes{gas: 100000}();
    }

    function GetForgerStakesDelegateCall() public {
        // This call does not really make sense as the storage layout of this contract does not match the
        // forger stakes contracts at all. It is only here because it should immediately throw an error.
        (bool success, bytes memory result) = address(nativeContract).delegatecall(
            abi.encodeWithSignature("getAllForgersStakes()")
        );
        if (success == false) {
            assembly {
                revert(add(result, 32), mload(result))
            }
        }
    }

    function GetForgerStakesCallCode() public {
        // This call does not really make sense as the storage layout of this contract does not match the
        // forger stakes contracts at all. It is only here because it should immediately throw an error.
        // NOTE: we can't directly use "callcode" anymore as the solidity compiler deprecated it long ago and
        // will not compile this anymore
//        (bool success, bytes memory data) = address(nativeContract).callcode(
//            abi.encodeWithSignature("getAllForgersStakes()")
//        );
        // using inline assembly CALLCODE can still be used
        address contractAddr = address(nativeContract);
        // function signature
        bytes4 sig = bytes4(keccak256("getAllForgersStakes()"));
        assembly {
            let x := mload(0x40)   //Find empty storage location using "free memory pointer"
            mstore(x, sig) //Place signature at beginning of empty storage
            mstore(0x40, add(x, 0x04)) // set free pointer before function call. so it is used by called function.
            // new free pointer position after the output values of the called function.

            let success := callcode(
            10000, //10 gas
            contractAddr, //To addr
            0, //No wei passed
            x, // Inputs are at location x
            0x04, //Inputs size, just the signature, so 4 bytes
            x, //Store output over input - never used, throws before this
            0x20 //Output is 32 bytes long - never used, throws before this
            )
        }
    }
}
