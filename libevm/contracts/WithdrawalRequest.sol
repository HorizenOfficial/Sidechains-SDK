// SPDX-License-Identifier: MIT

pragma solidity >=0.7.0 <0.9.0;

contract WithdrawalRequests {

    struct Request {
        uint256 value;
        address sender;
        string mc_address;
    }

    mapping(uint256 => Request[]) private requests;

    uint256 private currentEpoch;

    // we need a way to make sure only we can call a certain set of functions
    // consider this a TODO, I'm not sure if we could make it work like this
    modifier onlyCoinbase {
        require(msg.sender == block.coinbase);
        _;
    }

    // initialize with the current epoch number
    constructor(uint256 epoch) {
        currentEpoch = epoch;
    }

    // update current epoch number
    function setEpoch(uint256 epoch) public onlyCoinbase {
        currentEpoch = epoch;
    }

    // clear requests for an epoch after successful withdrawal to the mainchain
    function clearRequests(uint256 epoch) public onlyCoinbase {
        delete requests[epoch];
    }

    // fetch all pending requests for a given epoch
    function getRequests(uint256 epoch) public view returns (Request[] memory) {
        return requests[epoch];
    }

    // submit a new withdrawal reuqest within the current epoch
    function submitRequest(string calldata mc_address) public payable returns (uint256) {
        // any value transfered with a call to this function is added to the contracts balance
        // TODO: assert validity of mc_address
        // assert minimum withdrawal amount
        require(msg.value > 2000);
        // store withdrawal request at current epoch
        requests[currentEpoch].push(Request(msg.value, msg.sender, mc_address));
        // return the epoch to which the withdrawal request was added
        return currentEpoch;
    }

    // refund the callers withdrawal requests in the given epoch
    function refund(uint256 epoch) public {
        // refunding is only allowed for epochs that have already passed, meaning withdrawal effectively failed
        // because it could not be included in a withdrawal certificate within the given epoch
        require(epoch < currentEpoch);
        uint256 l = requests[epoch].length;
        // iterate backwards because elements are removed during iteration
        for (uint256 i = l - 1; i >= 0; i--) {
            if (msg.sender == requests[epoch][i].sender) {
                // transfer refund
                payable(msg.sender).transfer(requests[epoch][i].value);
                // remove this request from the array
                requests[epoch][i] = requests[epoch][l - 1];
                requests[epoch].pop();
                l--;
            }
        }
    }
}
