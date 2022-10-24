#!/usr/bin/env python3
import json
import logging
import os
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, \
    MCConnectionInfo, SCBootstrapInfo
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, initialize_chain_clean, connect_nodes_bi, COIN
from SidechainTestFramework.scutil import start_sc_nodes, \
    generate_secrets, generate_vrf_secrets, generate_certificate_proof_info, \
    bootstrap_sidechain_node, generate_next_blocks, launch_bootstrap_tool, cert_proof_keys_paths, \
    csw_proof_keys_paths, generate_csw_proof_info

"""
Demo flow of how to bootstrap SC network and start SC nodes.

Configuration: 2 MC nodes

Test:
    - Bootstrap SC network
    - Start 1 SC node connected to MC
    - Do FT
    - Do sidechain internal transaction
    - Do BT
"""

# set this constant to False for not having an interactive behaviour
INTERACTIVE = True


class Demo(SidechainTestFramework):

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, 2)

    def setup_network(self, split = False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()
        connect_nodes_bi(self.nodes, 0, 1)
        self.sync_all()

    def setup_nodes(self):
        return start_nodes(2, self.options.tmpdir)

    def sc_setup_chain(self):
        return

    def sc_setup_nodes(self):
        return

    def sc_setup_network(self, split = False):
        return

    def run_test(self):
        # Activate Sidechains fork
        mc_node = self.nodes[0]
        mc_node_miner = self.nodes[1]
        mc_node.generate(20)  # Generate first 20 block by main MC node to get some reward coins
        self.sync_all()
        mc_node_miner.generate(400)  # Generate the rest by another node.
        self.sync_all()
        logging.info("MC Node started.")

        mc_height = mc_node.getblockcount()
        logging.info("MC chain height is " + str(mc_height) + ". Sidechains fork activated.\n")

        self.pause()


        # Declare SC creation output tx
        logging.info("\nDeclaring new Sidechain in MC network.")
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )

        creation_amount = 100  # Zen
        withdrawal_epoch_length = 10
        sc_creation_info = SCCreationInfo(mc_node, creation_amount, withdrawal_epoch_length)
        accounts = generate_secrets("seed", 1)
        vrf_keys = generate_vrf_secrets("seed", 1)
        genesis_account = accounts[0]
        vrf_key = vrf_keys[0]
        ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
        if not os.path.isdir(ps_keys_dir):
            os.makedirs(ps_keys_dir)

        cert_keys_paths = cert_proof_keys_paths(ps_keys_dir)
        certificate_proof_info = generate_certificate_proof_info("seed", 7, 5, cert_keys_paths, True)

        csw_keys_paths = csw_proof_keys_paths(ps_keys_dir, sc_creation_info.withdrawal_epoch_length)
        csw_vr_key = generate_csw_proof_info(withdrawal_epoch_length, csw_keys_paths)

        custom_data = vrf_key.publicKey
        fe_certificate_field_configs = [255, 255]

        cmdInput = {
            "version": 0,
            "withdrawalEpochLength": withdrawal_epoch_length,
            "toaddress": genesis_account.publicKey,
            "amount": sc_creation_info.forward_amount,
            "wCertVk": certificate_proof_info.verificationKey,
            "customData": custom_data,
            "constant": certificate_proof_info.genSysConstant,
            "wCeasedVk": csw_vr_key,
            "vFieldElementCertificateFieldConfig": fe_certificate_field_configs
        }
        logging.info("Running sc_create RPC call on MC node:\n" +
              'sc_create {} '.format(json.dumps(cmdInput, indent=4, sort_keys=True)))
        logging.info(
            "where arguments are:\nwithdrawal epoch length - {}\nfirst Forward Transfer receiver address in the Sidechain - {}\nfirst Forward Transfer amount - {} ({} Zen)\nwithdrawal certificate verification key - {}\nfirst ForgerBox VRF publick key - {}\nwithdrawal certificate Snark proof public input - {}\n".format(
                withdrawal_epoch_length, genesis_account.publicKey, sc_creation_info.forward_amount * COIN,  sc_creation_info.forward_amount,
                certificate_proof_info.verificationKey, custom_data, certificate_proof_info.genSysConstant))

        self.pause()

        # Create Tx and Block
        sc_create_res = mc_node.sc_create(cmdInput)

        transaction_id = sc_create_res["txid"]
        logging.info("Sidechain creation transaction Id - {0}".format(transaction_id))

        sidechain_id = sc_create_res["scid"]
        logging.info("Sidechain created with Id -  {0}\n".format(sidechain_id))

        logging.info("Generating Block with sidechain creation transaction...")
        block_id = mc_node.generate(1)[0]
        logging.info("Block id - {}\n".format(block_id))

        self.pause()


        # Declare SC genesis data config info
        logging.info("\nPreparing Sidechain network configuration.")
        logging.info("Running getscgenesisinfo RPC call on MC to get the Sidechain related data for genesis block generation:\n" +
              'getscgenesisinfo "{}"'.format(sidechain_id))

        genesis_info = [mc_node.getscgenesisinfo(sidechain_id), mc_node.getblockcount(), sidechain_id]

        jsonParameters = {
            "secret": genesis_account.secret,
            "vrfSecret": vrf_key.secret,
            "info": genesis_info[0],
            "regtestBlockTimestampRewind": 720*120*5
        }
        jsonNode = launch_bootstrap_tool("genesisinfo", jsonParameters)
        logging.info("\nCalculating Sidechain network genesis data using ScBootstrappingTool command:\n" +
              "genesisinfo {}\n".format(json.dumps(jsonParameters, indent=4, sort_keys=True)) +
              "where arguments are:\ninfo - genesis info retrieved from MC on previous step\nsecret and vrfSecret - private part the corresponds first FT data in sc_create RPC call.\n")

        self.pause()


        # Result of genesis data config info
        logging.info("Result:\n {}".format(json.dumps(jsonNode, indent=4, sort_keys=True)))
        genesis_data = jsonNode

        sidechain_id = genesis_info[2]

        sc_bootstrap_info = SCBootstrapInfo(sidechain_id, genesis_account, sc_creation_info.forward_amount, genesis_info[1],
                               genesis_data["scGenesisBlockHex"], genesis_data["powData"], genesis_data["mcNetwork"],
                               sc_creation_info.withdrawal_epoch_length, vrf_key, certificate_proof_info,
                               genesis_data["initialCumulativeCommTreeHash"], cert_keys_paths, csw_keys_paths)


        bootstrap_sidechain_node(self.options.tmpdir, 0, sc_bootstrap_info, sc_node_configuration)

        self.pause()


        # Start SC info
        logging.info("\nStarting Sidechain node...")
        self.sc_nodes = start_sc_nodes(1, self.options.tmpdir)

        sc_node = self.sc_nodes[0]

        initial_sc_balance = sc_node.wallet_coinsBalance()["result"]
        logging.info("\nInitial SC wallet balance in satoshi: {}".format(json.dumps(initial_sc_balance, indent=4, sort_keys=True)))

        initial_boxes_balances = sc_node.wallet_allBoxes()["result"]
        logging.info("\nInitial SC wallet boxes: {}".format(json.dumps(initial_boxes_balances, indent=4, sort_keys=True)))

        self.pause()

        # MC balance before FT
        logging.info("\n MC total balance before Forward Transfer is {} Zen".format(mc_node.getbalance()))

        self.pause()

        # Do FT
        sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount = 5
        mc_return_address = mc_node.getnewaddress()
        cmdInput = [{'toaddress': sc_address, 'amount': ft_amount, "scid": sc_bootstrap_info.sidechain_id,
                     "mcReturnAddress": mc_return_address}]
        logging.info("\nCreating Forward Transfer with {} satoshi ({} Zen) to Sidechain:\n".format(ft_amount * COIN, ft_amount) +
              'sc_send {}'.format(json.dumps(cmdInput, indent=4, sort_keys=True)))

        self.pause()

        ft_tx_id = mc_node.sc_send(cmdInput)
        logging.info("\nFT transaction id - {}".format(ft_tx_id))


        # Generate MC block and SC block and check that FT appears in SC node wallet
        logging.info("Generating MC Block with Forward Transfer...")
        self.sync_all()
        mcblock_hash1 = mc_node_miner.generate(1)[0]
        self.sync_all()
        logging.info("MC Block id - {}\n".format(mcblock_hash1))
        logging.info("Generating SC Block to include MC Block Forward Transfer...")
        scblock_id1 = generate_next_blocks(sc_node, "first node", 1)[0]

        self.pause()

        # MC balance after FT
        logging.info("\n MC total balance after Forward Transfer is {} Zen".format(mc_node.getbalance()))

        self.pause()

        # Check balance changes
        sc_balance = sc_node.wallet_coinsBalance()["result"]
        logging.info("\nSC wallet balance in satoshi: {}".format(
            json.dumps(sc_balance, indent=4, sort_keys=True)))

        boxes_balances = sc_node.wallet_allBoxes()["result"]
        logging.info("\nSC wallet boxes: {}".format(json.dumps(boxes_balances, indent=4, sort_keys=True)))

        self.pause()


        # Do inchain coins send
        sc_send_amount = 1  # Zen
        logging.info("\nSending {} satoshi ({} Zen) inside sidechain...".format(sc_send_amount * COIN, sc_send_amount))
        sc_address = sc_node.wallet_allPublicKeys()["result"]["propositions"][-1]["publicKey"]
        logging.info(sc_address)
        self.send_coins(sc_node, sc_address, sc_send_amount * COIN, 100)

        logging.info("Generating SC Block with send coins transaction...")
        scblock_id2 = generate_next_blocks(sc_node, "first node", 1)[0]

        self.pause()

        # Check balance changes
        sc_balance = sc_node.wallet_coinsBalance()["result"]
        logging.info("\nSC wallet balance in satoshi: {}".format(
            json.dumps(sc_balance, indent=4, sort_keys=True)))

        boxes_balances = sc_node.wallet_allBoxes()["result"]
        logging.info("\nSC wallet boxes: {}".format(json.dumps(boxes_balances, indent=4, sort_keys=True)))


        # Do BT
        self.pause()
        mc_address = mc_node.getnewaddress()
        bt_amount = 2  # Zen
        withdrawal_request = {"outputs": [
            {"mainchainAddress": mc_address,
             "value": bt_amount * COIN}
        ]
        }

        logging.info("\nCreating Backward Transfer request to withdraw {} satoshi ({} Zen) to the Mainchain...".format(bt_amount * COIN, bt_amount))
        sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))

        logging.info("Generating SC Block with Backward Transfer request transaction...")
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]

        self.pause()

        # Run block generation till epoch end -> automatic block generation
        logging.info("Generating 9 more MC blocks to finish withdrawal epoch for the Sidechain...")
        mc_block_ids = mc_node.generate(9)
        logging.info("MC Block ids - {}\n".format(mc_block_ids))
        logging.info("Generating SC blocks to synchronize MC blocks and automatically start creation of Withdrawal Certificate...")
        sc_block_ids = generate_next_blocks(sc_node, "first node", 4)
        logging.info("\nGenerating Withdrawal Certificate...\n")

        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for withdrawal certificate in MC memory pool...")
            time.sleep(2)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        certHash = mc_node.getrawmempool()[0]
        logging.info("Withdrawal certificate hash - " + certHash)
        cert = mc_node.getrawtransaction(certHash, 1)
        logging.info("Withdrawal certificate - {}".format(json.dumps(cert, indent=4, sort_keys=True, default=str)))

        self.pause()

        # Check MC balance for BT destination address before Certificate inclusion
        mc_balance_before_cert = mc_node.getreceivedbyaddress(mc_address)
        logging.info("\nMC address {} balance before Certificate inclusion is = {:.8f} Zen.".format(mc_address, mc_balance_before_cert))

        self.pause()

        # Generate MC block to include the certificate
        logging.info("\nGenerating 1 more MC block to include Withdrawal certificate in the chain...")
        mc_block_4 = mc_node.generate(1)[0]
        logging.info("MC Block id - {}\n".format(mc_block_4))

        self.pause()

        # Check MC balance for BT destination address before Certificate inclusion
        mc_balance_after_cert = mc_node.getreceivedbyaddress(mc_address)
        logging.info("\nMC address {} balance after Certificate inclusion is = {:.8f} Zen.".format(mc_address, mc_balance_after_cert))

        self.pause()

        # Get SC balances changes
        sc_balance = sc_node.wallet_coinsBalance()["result"]
        logging.info("\nSC wallet balance in satoshi: {}".format(
            json.dumps(sc_balance, indent=4, sort_keys=True)))
        boxes_balances = sc_node.wallet_allBoxes()["result"]
        logging.info("\nSC wallet boxes: {}".format(json.dumps(boxes_balances, indent=4, sort_keys=True)))

        self.pause()

    def pause(self):
        if INTERACTIVE == True:
            input("Press the <ENTER> key to continue...")
        pass

    def send_coins(self, sc_node, receiver, amount, fee):
        j = {"outputs": [ {
                "publicKey": receiver,
                "value": amount
                } ],
            "fee": fee,
            }
        request = json.dumps(j)
        txid = sc_node.transaction_sendCoinsToAddress(request)["result"]["transactionId"]
        return txid

if __name__ == "__main__":
    Demo().main()
