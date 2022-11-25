//SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract TestDeployingContract {
    string private _secret;

    TestDeployedContract[] _children;

    event ChildDeployed(address indexed addr);
    event SecretSet(string indexed newSecret);

    constructor(string memory secret) {
        _secret = secret;
    }
    function deployContract() external {
        TestDeployedContract newContr = new TestDeployedContract(this);
        _children.push(newContr);
        emit ChildDeployed(address(newContr));
    }
    modifier onlyChild() {
        uint256 len = _children.length;
        bool found = false;
        for (uint256 i = 0; i < len; i++) {
            if (msg.sender == address(_children[i])) {
                found = true;
                break;
            }
        }
        if (!found)
            revert("Can only be called from the child");
        _;
    }
    function getSecret() onlyChild public view returns (string memory) {

        return _secret;
    }

    function setSecret(string memory secret) onlyChild external {
        _secret = secret;
        emit SecretSet(_secret);
    }

    function getChildren() external view returns (TestDeployedContract[] memory) {
        return _children;
    }
}

contract TestDeployedContract {
    TestDeployingContract _parent;
    constructor(TestDeployingContract deployer) {
        _parent = deployer;
    }
    function checkParentSecret() external view returns (string memory) {
        return _parent.getSecret();
    }

    function setParentSecret(string memory secret) external {
        _parent.setSecret(secret);
    }

    function getParent() external view returns (TestDeployingContract) {
        return _parent;
    }
}
