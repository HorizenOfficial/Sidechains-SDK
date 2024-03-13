// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

/*
 Native Contract managing forgers stakes - Version 2 (activated from EON version 1.4)
 contract address: 0x0000000000000000000022222222222222222333
*/
interface ForgerStakesV2 {

    // Event declaration
    // Up to 3 parameters can be indexed.
    // Indexed parameters helps you filter the logs by the indexed parameter
    event RegisterForger(address indexed sender, bytes32 indexed vrf1, bytes1 indexed vrf2, bytes32 signKey, uint256 value, uint32 rewardShare, address reward_address); 
    event UpdateForger(address indexed sender, bytes32 indexed vrf1, bytes1 indexed vrf2, bytes32 signKey, uint32 rewardShare, address reward_address); 
    event DelegateForgerStake(address indexed sender, bytes32 indexed vrf1, bytes1 indexed vrf2, bytes32 signKey, uint256 value);
    event WithdrawForgerStake(address indexed owner, bytes32 indexed vrf1, bytes1 indexed vrf2, bytes32 signKey, uint256 value);
    event StakeUpgrade(uint32 oldVersion, uint32 newVersion);


    //Data structures
    struct ForgerInfo {       
        bytes32 vrf1;
        bytes1 vrf2;
        bytes32 signKey;
        uint32 rewardShare;
        address reward_address;
    }

    struct StakeDataDelegator {
        address delegator;
        uint256 stakedAmount;
    }

    struct StakeDataForger {
        bytes32 vrf1;
        bytes1 vrf2;
        bytes32 signKey; 
        uint256 stakedAmount;
    }

    //read-write methods

    /*
      Register a new forger.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
      The methods accepts ZEN value: the sent value will be converted to the initial stake assigned to the forger.
    */
    function registerForger(bytes32 vrf1, bytes1 vrf2, bytes32 signKey, uint32 rewardShare, address reward_address) external payable;
    
     /*
      Upgrade an existingw forger.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
    */
    function updateForger(bytes32 vrf1, bytes1 vrf2, bytes32 signKey, uint32 rewardShare, address reward_address, bytes32 signature1, bytes32 signature2) external;
    
    /*
      Delegate a stake to a previously registered forger.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
    */
    function delegate(bytes32 vrf1, bytes1 vrf2, bytes32 signKey) external payable;

    /*
      Wiithdraw (unstake) a previously assigned stake.
      Vrf key is split in two separate parameters, being longer than 32 bytes.
    */ 
    function withdraw(bytes32 vrf1, bytes1 vrf2, bytes32 signKey, uint256 amount) external;

    //read only methods

    /*
       Returns the total stake amount at the end of one or more consensus epoch assigned to a specific foger.
       vrf, signKey and delegator are optional: if all are null, the total stake amount will be returned. If only 
         delegator is null, all the stakes assigned to the forger will be summed 
       consensusEpochStart and maxNumOfEpoch are optional: if both null, the data at the  current consensus epoch is returned
       Return array contains also elements with 0 value. Returned values are ordered by epoch, and the array lenght may
       be < maxNumOfEpoch if the current consensus epoch is < (consensusEpochStart + maxNumOfEpoch)
    */
    function stakeOf(bytes32 vrf1, bytes1 vrf2, bytes32 signKey, address delegator, uint32 consensusEpochStart, uint32 maxNumOfEpoch) external view returns (uint256[] memory);

    /*
       Return total sum payd to reward_address  at the end of one or more consensus epochs.
       Return array contains also elements with 0 value. Returned values are ordered by epoch, and the array lenght may
       be < maxNumOfEpoch if the current consensus epoch is < (consensusEpochStart + maxNumOfEpoch)
    */
    function rewardsReceived(bytes32 vrf1, bytes1 vrf2, bytes32 signKey, uint32 consensusEpochStart, uint32 maxNumOfEpoch) external view returns (uint256[] memory);

    /*
    /  Returns the info of a specific registered forger.
    */
    function getForger(bytes32 vrf1, bytes1 vrf2, bytes32 signKeyrVRFKey) external view returns (ForgerInfo memory);

    /*
    /  Returns the paginated list of all the registered forgers
    /  Each element of the list is the detail of a specific forger.
    */
    function getPagedForgers(int32 startIndex, int32 pageSize) external view returns (int32 next, ForgerInfo[] memory);

    /*
    /  Returns the paginated list of stakes delegated to a specific forger, grouped by delegator address.
    /  Each element of the list is the total amount delegated by a specific address.
    */
    function getPagedForgersStakesByForger(bytes32 vrf1, bytes1 vrf2, bytes32 signKeyrVRFKey, int32 startIndex, int32 pageSize) external view returns  (int32, StakeDataDelegator[] memory);

    /*
    /  Returns the paginated list of stakes delegated by a specific address, grouped by forger.
    /  Each element of the list is the total amount delegated to  a specific forger.
    */
    function getPagedForgersStakesByDelegator(address delegator, int32 startIndex, int32 pageSize) external view returns  (int32, StakeDataForger[] memory);

    function upgrade(uint32) external returns (uint32);
}