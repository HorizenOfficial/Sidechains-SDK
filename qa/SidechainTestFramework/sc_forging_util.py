from SidechainTestFramework.sc_boostrap_info import Account
from test_framework.util import assert_equal, fail
import json

def check_scparent(parent_scblock_id, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    assert_equal(parent_scblock_id, res["result"]["block"]["header"]["parentId"],
                 "SC Block {0} parent id is different.".format(scblock_id))
    print("SC Block {0} has parent id {1}.".format(scblock_id, parent_scblock_id))


def check_mcreference_presence(mcblock_hash, scblock_id, sc_node):
    check_mcheader_presence(mcblock_hash, scblock_id, sc_node)
    check_mcreferencedata_presence(mcblock_hash, scblock_id, sc_node)


def check_mcheader_presence(mcblock_hash, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    # print(json.dumps(res, indent=4))
    headers = res["result"]["block"]["mainchainHeaders"]
    for header in headers:
        if header["hash"] == mcblock_hash:
            print("MC hash {0} is present in SC Block {1} mainchain headers.".format(mcblock_hash, scblock_id))
            return
    fail("MC hash {0} was not found in SC Block {1} mainchain headers.".format(mcblock_hash, scblock_id))


def check_mcreferencedata_presence(mcblock_hash, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    # print(json.dumps(res, indent=4))
    refDataList = res["result"]["block"]["mainchainBlockReferencesData"]
    for refData in refDataList:
        if refData["headerHash"] == mcblock_hash:
            print("MC hash {0} is present in SC Block {1} mainchain reference data.".format(mcblock_hash, scblock_id))
            return
    fail("MC hash {0} was not found in SC Block {1} mainchain reference data.".format(mcblock_hash, scblock_id))


def check_mcheaders_amount(amount, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    headers = res["result"]["block"]["mainchainHeaders"]
    assert_equal(amount, len(headers), "SC Block {0} mainchain headers amount is different".format(scblock_id))
    print("SC block {0} contains {1} mainchain headers.".format(scblock_id, amount))


def check_mcreferencedata_amount(amount, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    headers = res["result"]["block"]["mainchainBlockReferencesData"]
    assert_equal(amount, len(headers), "SC Block {0} mainchain ref data amount is different".format(scblock_id))
    print("SC block {0} contains {1} mainchain reference data.".format(scblock_id, amount))


def check_ommers_amount(amount, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    ommers = res["result"]["block"]["ommers"]
    assert_equal(amount, len(ommers), "SC Block {0} ommers amount is different".format(scblock_id))
    print("SC block {0} contains {1} ommers.".format(scblock_id, amount))


def check_ommers_cumulative_score(score, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    actual_score = res["result"]["block"]["header"]["ommersCumulativeScore"]
    assert_equal(score, actual_score, "SC Block {0} ommers cumulative score is different".format(scblock_id))
    print("SC block {0} has cumulative score {1}.".format(scblock_id, score))


def check_ommer(ommer_scblock_id, ommer_mcheaders_hashes, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    ommers = res["result"]["block"]["ommers"]
    for ommer in ommers:
        if ommer["header"]["id"] == ommer_scblock_id:
            print("Ommer id {0} is present in SC Block {1} ommers.".format(ommer_scblock_id, scblock_id))
            ommer_mcheaders = ommer["mainchainHeaders"]
            if len(ommer_mcheaders_hashes) == 0:
                return
            for header in ommer_mcheaders:
                if header["hash"] in ommer_mcheaders_hashes:
                    print("MC hash {0} is present in Ommer {1} mainchain headers.".format(header["hash"],
                                                                                          ommer_scblock_id))
                    return
                else:
                    fail("MC hash {0} was not found in Ommer {1} mainchain headers.".format(header["hash"],
                                                                                            ommer_scblock_id))
    fail("Ommer id {0} was not found in SC Block {1} ommers.".format(ommer_scblock_id, scblock_id))


def check_subommer(ommer_scblock_id, subommer_scblock_id, subommer_mcheader_hashes, scblock_id, sc_node):
    res = sc_node.block_findById(blockId=scblock_id)
    ommers = res["result"]["block"]["ommers"]
    for ommer in ommers:
        if ommer["header"]["id"] == ommer_scblock_id:
            print("Ommer id {0} is present in SC Block {1} ommers.".format(ommer_scblock_id, scblock_id))
            subommers = ommer["ommers"]
            for subommer in subommers:
                if subommer["header"]["id"] == subommer_scblock_id:
                    print("Subommer id {0} is present in Ommer {1} in SC Block {2}.".format(subommer_scblock_id,
                                                                                            ommer_scblock_id,
                                                                                            scblock_id))
                    subommer_mcheaders = subommer["mainchainHeaders"]
                    if len(subommer_mcheader_hashes) == 0:
                        return
                    for header in subommer_mcheaders:
                        if header["hash"] in subommer_mcheader_hashes:
                            print("MC hash {0} is present in Subommer {1} mainchain headers.".format(header["hash"],
                                                                                                     subommer_scblock_id))
                            return
                        else:
                            fail("MC hash {0} was not found in Subommer {1} mainchain headers.".format(header["hash"],
                                                                                                       subommer_scblock_id))
            fail("Subommer id {0} was not found in Ommer {1] in SC Block {2} ommers.".format(subommer_scblock_id,
                                                                                             ommer_scblock_id,
                                                                                             scblock_id))
    fail("Ommer id {0} was not found in SC Block {1} ommers.".format(ommer_scblock_id, scblock_id))


def mc_make_forward_transfer(mc_node, sc_node, sc_id, ft_zen_amount):
    # Do FT of `ft_amount` Zen to SC Node
    sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
    sc_account = Account("", sc_address)
    mc_mempool_size = mc_node.getmempoolinfo()["size"]
    mc_tx_id = mc_node.sc_send(sc_address, ft_zen_amount, sc_id)
    print("MC Tx with FT created: " + mc_tx_id)
    assert_equal(mc_mempool_size + 1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")
    mc_block_hash = mc_node.generate(1)[0]
    print("MC block with FT generated: " + mc_block_hash)
    return mc_block_hash


def sc_create_forging_stake_mempool(sc_node, stake_zen_amount):
    # Create forging stake of `stake_amount` Zen
    sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
    sc_vrf_address = sc_node.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
    forger_stake = {
        "outputs": [
            {
                "publicKey": sc_address,
                "blockSignPublicKey": sc_address,
                "vrfPubKey": sc_vrf_address,
                "value": stake_zen_amount * 100000000  # in Satoshi
            }
        ]
    }

    res = sc_node.transaction_makeForgerStake(json.dumps(forger_stake))
    if "result" not in res:
        fail("Forger stake creation failed: " + json.dumps(res))
    else:
        print("Forget stake created: " + json.dumps(res))


def sc_make_withdrawal_request_mempool(mc_node, sc_node, bt_zen_amount):
    addresses = mc_node.listaddresses()
    mc_address_hash = mc_node.getnewaddress("", True)  # what we will see in SC WRBox
    mc_address_standard = (set(mc_node.listaddresses()) - set(addresses)).pop()

    withdrawal_request = {
        "outputs": [
            {
                "publicKey": mc_address_standard,
                "value": bt_zen_amount * 100000000  # in Satoshi
            }
        ]
    }
    res = sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))
    if "result" not in res:
        fail("Withdraw coins failed: " + json.dumps(res))
    else:
        print("Coins withdrawn: " + json.dumps(res))
