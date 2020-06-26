#!/usr/bin/env python2
import json
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, \
    MCConnectionInfo, SCBootstrapInfo
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import start_sc_nodes, \
    generate_secrets, generate_vrf_secrets, generate_withdrawal_certificate_data, \
    bootstrap_sidechain_node, generate_next_blocks, launch_bootstrap_tool

"""
Demo flow of how to bootstrap SC network and start SC nodes.

Configuration: 1 MC node

Test:
    - Bootstrap SC network
    - Start 1 SC node connected to MC
    - Do FT
    - Do sidechain internal transaction
    - Do BT
"""
class Demo(SidechainTestFramework):

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_chain(self):
        return

    def sc_setup_nodes(self):
        return

    def sc_setup_network(self, split = False):
        return

    def run_test(self):
        # Activate Sidechains fork
        mc_node = self.nodes[0]
        mc_node.generate(220)
        print("MC Node started.")

        mc_height = mc_node.getblockcount()
        print("MC chain height is " + str(mc_height) + ". Sidechains fork activated.\n")

        self.pause()


        # Declare SC creation output tx
        print("\nDeclaring new Sidechain in MC network.")
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
        withdrawal_certificate_data = generate_withdrawal_certificate_data("seed", 7, 5)

        custom_data = vrf_key.publicKey
        print("Running sc_create RPC call on MC node:\n" +
              'sc_create {} "{}" {} "{}" "{}" "{}"'.format(withdrawal_epoch_length,
                                                           genesis_account.publicKey,
                                                           sc_creation_info.forward_amount,
                                                           withdrawal_certificate_data.verificationKey,
                                                           custom_data,
                                                           withdrawal_certificate_data.genSysConstant))
        print(
            "where arguments are:\nwithdrawal epoch length - {}\nfirst Forward Transfer receiver address in the Sidechain - {}\nfirst Forward Transfer amount in Zen - {}\nwithdrawal certificate verification key - {}\nfirst ForgerBox VRF publick key - {}\nwithdrawal certificate Snark proof public input - {}\n".format(
                withdrawal_epoch_length, genesis_account.publicKey, sc_creation_info.forward_amount,
                withdrawal_certificate_data.verificationKey, custom_data, withdrawal_certificate_data.genSysConstant))

        self.pause()

        # Create Tx and Block
        transaction_id = mc_node.sc_create(withdrawal_epoch_length,
                                           genesis_account.publicKey,
                                           sc_creation_info.forward_amount,
                                           withdrawal_certificate_data.verificationKey,
                                           custom_data,
                                           withdrawal_certificate_data.genSysConstant)

        decoded_tx = mc_node.getrawtransaction(transaction_id, 1)
        print "Sidechain creation transaction Id - {0}".format(transaction_id)

        sidechain_id = decoded_tx['vsc_ccout'][0]['scid']
        print "Sidechain created with Id -  {0}\n".format(sidechain_id)

        print "Generating Block with sidechain creation transaction..."
        block_id = mc_node.generate(1)[0]
        print "Block id - {}\n".format(block_id)

        self.pause()


        # Declare SC genesis data config info
        print("\nPreparing Sidechain network configuration.")
        print("Running getscgenesisinfo RPC call on MC to get the Sidechain related data for genesis block generation:\n" +
              'getscgenesisinfo "{}"'.format(sidechain_id))

        genesis_info = [mc_node.getscgenesisinfo(sidechain_id), mc_node.getblockcount(), sidechain_id]

        jsonParameters = {"secret": genesis_account.secret, "vrfSecret": vrf_key.secret, "info": genesis_info[0]}
        jsonNode = launch_bootstrap_tool("genesisinfo", jsonParameters)
        print("\nCalculating Sidechain network genesis data using ScBootstrappingTool command:\n" +
              "genesisinfo {}\n".format(json.dumps(jsonParameters, indent=4, sort_keys=True)) +
              "where arguments are:\ninfo - genesis info retrieved from MC on previous step\nsecret and vrfSecret - private part the corresponds first FT data in sc_create RPC call.\n")

        self.pause()


        # Result of genesis data config info
        print("Result:\n {}".format(json.dumps(jsonNode, indent=4, sort_keys=True)))
        genesis_data = jsonNode

        sidechain_id = genesis_info[2]

        sc_bootstrap_info = SCBootstrapInfo(sidechain_id, genesis_account, sc_creation_info.forward_amount, genesis_info[1],
                               genesis_data["scGenesisBlockHex"], genesis_data["powData"], genesis_data["mcNetwork"],
                               sc_creation_info.withdrawal_epoch_length, vrf_key, withdrawal_certificate_data)


        bootstrap_sidechain_node(self.options.tmpdir, 0, sc_bootstrap_info, sc_node_configuration)

        self.pause()


        # Start SC info
        print("\nStarting Sidechain node...")
        self.sc_nodes = start_sc_nodes(1, self.options.tmpdir, print_output_to_file=True)

        sc_node = self.sc_nodes[0]

        initial_sc_balance = sc_node.wallet_balance()["result"]
        print("\nInitial SC wallet balance in satoshi: {}".format(json.dumps(initial_sc_balance, indent=4, sort_keys=True)))

        initial_boxes_balances = sc_node.wallet_allBoxes()["result"]
        print("\nInitial SC wallet boxes: {}".format(json.dumps(initial_boxes_balances, indent=4, sort_keys=True)))

        self.pause()


        # Do FT
        sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount = 5
        print("\nCreating Forward Transfer with {} Zen to Sidechain:\n".format(ft_amount) +
              'sc_send "{}" {} "{}"'.format(sc_address, ft_amount, sc_bootstrap_info.sidechain_id))

        self.pause()

        ft_tx_id = mc_node.sc_send(sc_address, ft_amount, sc_bootstrap_info.sidechain_id)
        print("\nFT transaction id - {}".format(ft_tx_id))

        # Generate MC block and SC block and check that FT appears in SC node wallet
        print "Generating MC Block with Forward Transfer..."
        mcblock_hash1 = mc_node.generate(1)[0]
        print "MC Block id - {}\n".format(mcblock_hash1)
        print "Generating SC Block to include MC Block Forward Transfer..."
        scblock_id1 = generate_next_blocks(sc_node, "first node", 1)[0]

        self.pause()


        # Check balance changes
        sc_balance = sc_node.wallet_balance()["result"]
        print("\nSC wallet balance in satoshi: {}".format(
            json.dumps(sc_balance, indent=4, sort_keys=True)))

        boxes_balances = sc_node.wallet_allBoxes()["result"]
        print("\nSC wallet boxes: {}".format(json.dumps(boxes_balances, indent=4, sort_keys=True)))

        self.pause()


        # Do inchain coins send
        coin = 100000000
        sc_send_amount = 1  # Zen
        print("\nSending {} Zen inside sidechain...".format(sc_send_amount))
        sc_address = sc_node.wallet_allPublicKeys()["result"]["propositions"][-1]["publicKey"]
        print(sc_address)
        self.send_coins(sc_node, sc_address, sc_send_amount * coin, 100)

        print "Generating SC Block with send coins transaction..."
        scblock_id2 = generate_next_blocks(sc_node, "first node", 1)[0]

        self.pause()

        # Check balance changes
        sc_balance = sc_node.wallet_balance()["result"]
        print("\nSC wallet balance in satoshi: {}".format(
            json.dumps(sc_balance, indent=4, sort_keys=True)))

        boxes_balances = sc_node.wallet_allBoxes()["result"]
        print("\nSC wallet boxes: {}".format(json.dumps(boxes_balances, indent=4, sort_keys=True)))


        # Do BT
        self.pause()
        mc_address = self.nodes[0].getnewaddress("", True)
        bt_amount = 2  # Zen
        withdrawal_request = {"outputs": [
            {"publicKey": mc_address,
             "value": bt_amount * coin}
        ]
        }

        print("\nCreating Backward Transfer request to withdraw {} Zen to the Mainchain...".format(bt_amount))
        sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))

        print "Generating SC Block with Backward Transfer request transaction..."
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]

        self.pause()

        # Run block generation till epoch end -> automatic block generation
        print("Generating 9 more MC blocks to finish withdrawal epoch for the Sidechain...")
        mc_block_ids = mc_node.generate(9)
        print "MC Block ids - {}\n".format(mc_block_ids)
        print("Generating SC blocks to synchronize MC blocks and automatically start creation of Withdrawal Certificate...")
        sc_block_ids = generate_next_blocks(sc_node, "first node", 4)
        print("\nGenerating Withdrawal Certificate...\n")

        attempts = 20
        while mc_node.getmempoolinfo()["size"] == 0 and attempts > 0:
            if attempts % 4 == 0:
                print("Wait for withdrawal certificate in MC memory pool...")
            time.sleep(10)
            attempts -= 1
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mmepool.")

        certHash = mc_node.getrawmempool()[0]
        print("Withdrawal certificate hash - " + certHash)
        cert = mc_node.getrawcertificate(certHash, 1)
        print("Withdrawal certificate - {}".format(json.dumps(cert, indent=4, sort_keys=True, default=str)))
        # TODO: check mc balances before and after block inclusion

        self.pause()


        # Gen SC block to sync back certificate
        print("\nGenerating 1 more MC block to include Withdrawal certificate in the chain...")
        mc_block_4 = mc_node.generate(1)[0]
        print "MC Block id - {}\n".format(mc_block_4)

        self.pause()

        # Get SC balances changes
        sc_balance = sc_node.wallet_balance()["result"]
        print("\nSC wallet balance in satoshi: {}".format(
            json.dumps(sc_balance, indent=4, sort_keys=True)))
        boxes_balances = sc_node.wallet_allBoxes()["result"]
        print("\nSC wallet boxes: {}".format(json.dumps(boxes_balances, indent=4, sort_keys=True)))

    def pause(self):
        raw_input("Press the <ENTER> key to continue...")

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
