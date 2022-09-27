// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

contract LoopTestA {
    constructor(uint256[] memory initial) {
        for (uint i = 0; i < initial.length; i++) {
            require(initial[i] == 123);
        }
    }
}

contract LoopTestB {
    constructor(uint256[] memory initial) {
        for (uint i = 0; i < initial.length;) {
            require(initial[i] == 123);
            ++i;
        }
    }
}

contract LoopTestC {
    constructor(uint256[] memory initial) {
        uint i = 0;
        while (i < initial.length) {
            require(initial[i] == 123);
            i++;
        }
    }
}

contract LoopTestD {
    constructor(uint256[] memory initial) {
        uint i = 0;
        while (i < initial.length) {
            require(initial[i] == 123);
            ++i;
        }
    }
}
