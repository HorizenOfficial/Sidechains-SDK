// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/*
 Native Contract managing forgers stakes - Version 2 (activated from EON version 1.4)
 contract address: 0x0000000000000000000022222222222222222333
*/
interface ForgerStakesV2 {

    // Event declaration
    // Up to 3 parameters can be indexed.
    // Indexed parameters help you filter the logs by the indexed parameter
    event RegisterForger(address indexed sender,  bytes32 signPubKey, bytes32 indexed vrf1, bytes1 indexed vrf2, uint256 value, uint32 rewardShare, address reward_address);
    event UpdateForger(address indexed sender, bytes32 signPubKey,  bytes32 indexed vrf1, bytes1 indexed vrf2,  uint32 rewardShare, address reward_address);
    event DelegateForgerStake(address indexed sender, bytes32 signPubKey, bytes32 indexed vrf1, bytes1 indexed vrf2, uint256 value);
    event WithdrawForgerStake(address indexed sender, bytes32 signPubKey, bytes32 indexed vrf1, bytes1 indexed vrf2, uint256 value);
    event ActivateStakeV2();


    //Data structures
    struct ForgerInfo {
        bytes32 signPubKey;
        bytes32 vrf1;
        bytes1 vrf2;
        uint32 rewardShare;
        address reward_address;
    }

    struct StakeDataDelegator {
        address delegator;
        uint256 stakedAmount;
    }

    struct StakeDataForger {
        bytes32 signPubKey;
        bytes32 vrf1;
        bytes1 vrf2;
        uint256 stakedAmount;
    }

    //read-write methods

    /*
      Register a new forger.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
      The method accepts ZEN value: the sent value will be converted to the initial stake assigned to the forger.
    */
    function registerForger(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2, uint32 rewardShare, address reward_address) external payable;

     /*
      Updates an existing forger.
      A forger can be updated just once and only if reward_address == null and rewardShare == 0.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
    */
    function updateForger(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2, uint32 rewardShare, address reward_address, bytes32 signature1, bytes32 signature2) external;

    /*
      Delegate a stake to a previously registered forger.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
    */
    function delegate(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2) external payable;

    /*
      Withdraw (unstake) a previously assigned stake.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
    */
    function withdraw(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2, uint256 amount) external;

    //read only methods

    /*
       Returns the total stake amount, at the end of one or more consensus epochs, assigned to a specific forger.
       vrf, signKey and delegator are optional: if all are null, the total stake amount will be returned. If only
       delegator is null, all the stakes assigned to the forger will be summed.
       If vrf and signKey are null, but delegator is defined, the method will fail.
       consensusEpochStart and maxNumOfEpoch are optional: if both null, the data at the current consensus epoch is returned.
       Returned array contains also elements with 0 value. Returned values are ordered by epoch, and the array length may
       be < maxNumOfEpoch if the current consensus epoch is < (consensusEpochStart + maxNumOfEpoch) or if the forger was
       registered after consensusEpochStart.
    */
    function stakeTotal(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2, address delegator, uint32 consensusEpochStart, uint32 maxNumOfEpoch) external view returns (uint256[] memory listOfStakes);

    /*
       Return total sum paid to the forger reward_address at the end of one or more consensus epochs.
       Returned array contains also elements with 0 value. Returned values are ordered by epoch, and the array length may
       be < maxNumOfEpoch if the current consensus epoch is < (consensusEpochStart + maxNumOfEpoch) or if the forger was
       registered after consensusEpochStart.
    */
    function rewardsReceived(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2, uint32 consensusEpochStart, uint32 maxNumOfEpoch) external view returns (uint256[] memory listOfRewards);

    /*
      Returns the info of a specific registered forger.
    */
    function getForger(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2) external view returns (ForgerInfo memory forgerInfo);

    /*
      Returns the paginated list of all the registered forgers.
      Each element of the list is the detail of a specific forger.
      nextIndex will contain the index of the next element not returned yet. If no element is still present, next will be -1.
    */
    function getPagedForgers(int32 startIndex, int32 pageSize) external view returns (int32 nextIndex, ForgerInfo[] memory listOfForgerInfo);

    /*
      Returns the paginated list of stakes delegated to a specific forger, grouped by delegator address.
      Each element of the list is the total amount delegated by a specific address.
      nextIndex will contain the index of the next element not returned yet. If no element is still present, next will be -1.
    */
    function getPagedForgersStakesByForger(bytes32 signPubKey, bytes32 vrf1, bytes1 vrf2, int32 startIndex, int32 pageSize) external view returns (int32 nextIndex, StakeDataDelegator[] memory listOfDelegatorStakes);

    /*
      Returns the paginated list of stakes delegated by a specific address, grouped by forger.
      Each element of the list is the total amount delegated to  a specific forger.
      nextIndex will contain the index of the next element not returned yet. If no element is still present, next will be -1.
    */
    function getPagedForgersStakesByDelegator(address delegator, int32 startIndex, int32 pageSize) external view returns (int32 nextIndex, StakeDataForger[] memory listOfForgerStakes);

    /*
    /  Returns the current consensus epoch.
    */
    function getCurrentConsensusEpoch() external view returns (uint32 epoch);

    function activate() external;
}
