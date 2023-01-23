// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract SimpleWallet {
    string private _storage;
    address public owner;
    // Only owners can call transactions marked with this modifier
    modifier onlyOwner() {
        require(owner == msg.sender, "Caller is not the owner");
        _;
    }

    constructor(address _owner) payable {
        owner = _owner;
    }

    // Allows the owner to transfer ownership of the contract
    function transferOwnership(address _newOwner) external onlyOwner {
        owner = _newOwner;
    }

    // Returns ETH balance from this contract
    function getBalance() public view returns (uint256) {
        return address(this).balance;
    }

    // Allows contract owner to withdraw all funds from the contract
    function withdraw() external onlyOwner {
        payable(msg.sender).transfer(address(this).balance);
    }

    // Destroys this contract instance
    function destroy(address payable recipient) public onlyOwner {
        selfdestruct(recipient);
    }

    function setStorageValue(string memory value) public {
        _storage = value;
    }

    function getStorageValue() external view returns (string memory){
        return _storage;
    }
}
