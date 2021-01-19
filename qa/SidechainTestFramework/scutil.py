import os
import sys

import json

from SidechainTestFramework.sc_boostrap_info import MCConnectionInfo, SCBootstrapInfo, SCNetworkConfiguration, Account, \
    VrfAccount, CertificateProofInfo, SCNodeConfiguration
from sidechainauthproxy import SidechainAuthServiceProxy
import subprocess
import time
import socket
from contextlib import closing

from test_framework.util import initialize_new_sidechain_in_mainchain

WAIT_CONST = 1


class TimeoutException(Exception):
    def __init__(self, operation):
        Exception.__init__(self)
        self.operation = operation


def sc_p2p_port(n):
    return 8300 + n + os.getpid() % 999


def sc_rpc_port(n):
    return 8200 + n + os.getpid() % 999


# To be removed
def wait_for_next_sc_blocks(node, expected_height, wait_for=25):
    """
    Wait until blockchain height won't reach the expected_height, for wait_for seconds
    """
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise TimeoutException("Waiting blocks")
        height = int(node.block_best()["result"]["height"])
        if height >= expected_height:
            break
        time.sleep(WAIT_CONST)


def wait_for_sc_node_initialization(nodes):
    """
    Wait for SC Nodes to be fully initialized. This is done by pinging a node until its socket will be fully open
    """
    for i in range(len(nodes)):
        rpc_port = sc_rpc_port(i)
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            while not sock.connect_ex(("127.0.0.1", rpc_port)) == 0:
                time.sleep(WAIT_CONST)


def sync_sc_blocks(api_connections, wait_for=25, p=False):
    """
    Wait for maximum wait_for seconds for everybody to have the same block count
    """
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise TimeoutException("Syncing blocks")
        counts = [int(x.block_best()["result"]["height"]) for x in api_connections]
        if p:
            print (counts)
        if counts == [counts[0]] * len(counts):
            break
        time.sleep(WAIT_CONST)


def sync_sc_mempools(api_connections, wait_for=25):
    """
    Wait for maximum wait_for seconds for everybody to have the same transactions in their memory pools
    """
    start = time.time()
    while True:
        refpool = api_connections[0].transaction_allTransactions()["result"]["transactions"]
        if time.time() - start >= wait_for:
            raise TimeoutException("Syncing mempools")
        num_match = 1
        for i in range(1, len(api_connections)):
            nodepool = api_connections[i].transaction_allTransactions()["result"]["transactions"]
            if cmp(nodepool, refpool) == 0:
                num_match = num_match + 1
        if num_match == len(api_connections):
            break
        time.sleep(WAIT_CONST)


sidechainclient_processes = {}



def launch_bootstrap_tool(command_name, json_parameters):
    json_param = json.dumps(json_parameters)
    java_ps = subprocess.Popen(["java", "-jar",
                               "../tools/sctool/target/sidechains-sdk-scbootstrappingtools-0.2.6.jar",
                               command_name, json_param], stdout=subprocess.PIPE)
    sc_bootstrap_output = java_ps.communicate()[0]
    jsone_node = json.loads(sc_bootstrap_output)
    return jsone_node

"""
Generate a genesis info by calling ScBootstrappingTools with command "genesisinfo"
Parameters:
 - genesis_info: genesis info provided by a mainchain node
 - genesis_secret: private key 25519 secret to sign SC block
 - vrf_secret: vrk secret key to check consensus rules SC block
 - block_timestamp_rewind: rewind genesis block timestamp by some value
 
Output: a JSON object to be included in the settings file of the sidechain node nth.
{
    "scId": "id of the sidechain node",
    "scGenesisBlockHex": "some value",
    "powData": "some value",
    "mcBlockHeight": xxx,
    "mcNetwork": regtest|testnet|mainnet
    "withdrawalEpochLength": xxx
}
"""
def generate_genesis_data(genesis_info, genesis_secret, vrf_secret, block_timestamp_rewind):
    jsonParameters = {"secret": genesis_secret, "vrfSecret": vrf_secret, "info": genesis_info, "regtestBlockTimestampRewind": block_timestamp_rewind}
    jsonNode = launch_bootstrap_tool("genesisinfo", jsonParameters)
    return jsonNode


"""
Generate secrets by calling ScBootstrappingTools with command "generatekey"
Parameters:
 - seed
 - number_of_accounts: the number of keys to be generated
 
Output: an array of instances of Account (see sc_bootstrap_info.py).
"""
def generate_secrets(seed, number_of_accounts):
    accounts = []
    secrets = []
    for i in range(number_of_accounts):
        jsonParameters = {"seed": "{0}_{1}".format(seed, i + 1)}
        secrets.append(launch_bootstrap_tool("generatekey", jsonParameters))

    for i in range(len(secrets)):
        secret = secrets[i]
        accounts.append(Account(secret["secret"], secret["publicKey"]))
    return accounts


"""
Generate Vrf keys by calling ScBootstrappingTools with command "generateVrfKey"
Parameters:
 - seed
 - number_of_accounts: the number of keys to be generated

Output: an array of instances of VrfKey (see sc_bootstrap_info.py).
"""

def generate_vrf_secrets(seed, number_of_vrf_keys):
    vrf_keys = []
    secrets = []
    for i in range(number_of_vrf_keys):
        jsonParameters = {"seed": "{0}_{1}".format(seed, i + 1)}
        secrets.append(launch_bootstrap_tool("generateVrfKey", jsonParameters))

    for i in range(len(secrets)):
        secret = secrets[i]
        vrf_keys.append(VrfAccount(secret["vrfSecret"], secret["vrfPublicKey"]))
    return vrf_keys

# Maybe should we give the possibility to customize the configuration file by adding more fields ?

"""
Generate withdrawal certificate proof info calling ScBootstrappingTools with command "generateProofInfo"
Parameters:
 - seed
 - number_of_accounts: the number of schnorr keys to be generated

Output: CertificateProofInfo (see sc_bootstrap_info.py).
"""
def generate_certificate_proof_info(seed, number_of_schnorr_keys, threshold):
    jsonParameters = {"seed": seed, "keyCount": number_of_schnorr_keys, "threshold": threshold}
    output = launch_bootstrap_tool("generateProofInfo", jsonParameters)

    threshold = output["threshold"]
    verification_key = output["verificationKey"]
    gen_sys_constant = output["genSysConstant"]
    schnorr_keys = output["schnorrKeys"]

    schnorr_secrets = []
    schnorr_public_keys = []
    for i in range(len(schnorr_keys)):
        keys = schnorr_keys[i]
        schnorr_secrets.append(keys["schnorrSecret"])
        schnorr_public_keys.append(keys["schnorrPublicKey"])


    certificate_proof_info = CertificateProofInfo(threshold, gen_sys_constant, verification_key, schnorr_secrets, schnorr_public_keys)
    return certificate_proof_info

"""
Create directories for each node and configuration files inside them.
For each node put also genesis data in configuration files.

Parameters:
 - dirname: directory name
 - n: sidechain node nth
 - bootstrap_info: an instance of SCBootstrapInfo (see sc_bootstrap_info.py)
 - websocket_config: an instance of MCConnectionInfo (see sc_boostrap_info.py)
"""
def initialize_sc_datadir(dirname, n, bootstrap_info=SCBootstrapInfo, sc_node_config=SCNodeConfiguration()):

    apiAddress = "127.0.0.1"
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    mc0datadir = os.path.join(dirname, "node0")
    websocket_config = sc_node_config.mc_connection_info
    if not os.path.isdir(datadir):
        os.makedirs(datadir)

    with open('./resources/template.conf', 'r') as templateFile:
        tmpConfig = templateFile.read()

    genesis_secrets = []
    if bootstrap_info.genesis_vrf_account is not None:
        genesis_secrets.append(bootstrap_info.genesis_vrf_account.secret)

    if bootstrap_info.genesis_account is not None:
        genesis_secrets.append(bootstrap_info.genesis_account.secret)

    config = tmpConfig % {
        'NODE_NUMBER': n,
        'DIRECTORY': dirname,
        'WALLET_SEED': "sidechain_seed_{0}".format(n),
        'API_ADDRESS': "127.0.0.1",
        'API_PORT': str(apiPort),
        'BIND_PORT': str(bindPort),
        'OFFLINE_GENERATION': "false",
        'GENESIS_SECRETS': json.dumps(genesis_secrets),
        'SIDECHAIN_ID': bootstrap_info.sidechain_id,
        'GENESIS_DATA': bootstrap_info.sidechain_genesis_block_hex,
        'POW_DATA': bootstrap_info.pow_data,
        'BLOCK_HEIGHT': bootstrap_info.mainchain_block_height,
        'NETWORK': bootstrap_info.network,
        'WITHDRAWAL_EPOCH_LENGTH': bootstrap_info.withdrawal_epoch_length,
        'WEBSOCKET_ADDRESS': websocket_config.address,
        'CONNECTION_TIMEOUT': websocket_config.connectionTimeout,
        'RECONNECTION_DELAY': websocket_config.reconnectionDelay,
        'RECONNECTION_MAX_ATTEMPS': websocket_config.reconnectionMaxAttempts,
        "THRESHOLD" : bootstrap_info.certificate_proof_info.threshold,
        "SUBMITTER_CERTIFICATE" : ("true" if sc_node_config.cert_submitter_enabled else "false"),
        "SIGNER_PUBLIC_KEY": json.dumps(bootstrap_info.certificate_proof_info.schnorr_public_keys),
        "SIGNER_PRIVATE_KEY": json.dumps(bootstrap_info.certificate_proof_info.schnorr_secrets)
    }

    configsData.append({
        "name": "node" + str(n),
        "url": "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node" + str(n) + ".conf"), 'w+') as configFile:
        configFile.write(config)

    return configsData

"""
Create directories for each node and default configuration files inside them.
For each node put also genesis data in configuration files.
"""
def initialize_default_sc_datadir(dirname, n):

    genesis_secrets = {
        0: "6882a61d8a23a9582c7c7e659466524880953fa25d983f29a8e3aa745ee6de5c0c97174767fd137f1cf2e37f2e48198a11a3de60c4a060211040d7159b769266", \
        1: "905e2e581615ba0eff2bcd9fb666b4f6f6ed99ddd05208ae7918a25dc6ea6179c958724e7f4c44fd196d27f3384d2992a9c42485888862a20dcec670f3c08a4e", \
        2: "80b9a06608fa5dbd11fb72d28b9df49f6ac69f0e951ca1d9e67abd404559606be9b36fb5ae7e74cc50603b161a5c31d26035f6a59e602294d9900740d6c4007f"}

    apiAddress = "127.0.0.1"
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)

    with open('./resources/template_predefined_genesis.conf', 'r') as templateFile:
        tmpConfig = templateFile.read()
    config = tmpConfig % {
        'NODE_NUMBER': n,
        'DIRECTORY': dirname,
        'WALLET_SEED': "sidechain_seed_{0}".format(n),
        'API_ADDRESS': "127.0.0.1",
        'API_PORT': str(apiPort),
        'BIND_PORT': str(bindPort),
        'OFFLINE_GENERATION': "false",
        "SUBMITTER_CERTIFICATE": "false",
        'GENESIS_SECRETS': genesis_secrets[n]
    }

    configsData.append({
        "name": "node" + str(n),
        "url": "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node" + str(n) + ".conf"), 'w+') as configFile:
        configFile.write(config)

    return configsData


def initialize_default_sc_chain_clean(test_dir, num_nodes):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        initialize_default_sc_datadir(test_dir, i)


def initialize_sc_chain_clean(test_dir, num_nodes, genesis_secrets, genesis_info, array_of_MCConnectionInfo=[]):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        sc_node_config = SCNodeConfiguration(get_websocket_configuration(i, array_of_MCConnectionInfo))
        initialize_sc_datadir(test_dir, i, genesis_secrets[i], genesis_info[i], sc_node_config)


def get_websocket_configuration(index, array_of_MCConnectionInfo):
    return array_of_MCConnectionInfo[index] if index < len(array_of_MCConnectionInfo) else MCConnectionInfo()


def start_sc_node(i, dirname, extra_args=None, rpchost=None, timewait=None, binary=None, print_output_to_file=False):
    """
    Start a SC node and returns API connection to it
    """
    # Will we have  extra args for SC too ?
    datadir = os.path.join(dirname, "sc_node" + str(i))
    lib_separator = ":"
    if sys.platform.startswith('win'):
        lib_separator = ";"

    if binary is None:
        binary = "../examples/simpleapp/target/sidechains-sdk-simpleapp-0.2.6.jar" + lib_separator + "../examples/simpleapp/target/lib/* com.horizen.examples.SimpleApp"
    #        else if platform.system() == 'Linux':
    bashcmd = 'java -cp ' + binary + " " + (datadir + ('/node%s.conf' % i))
    if print_output_to_file:
        with open(datadir + "/log_out.txt", "wb") as out, open(datadir + "/log_err.txt", "wb") as err:
            sidechainclient_processes[i] = subprocess.Popen(bashcmd.split(), stdout=out, stderr=err)
    else:
        sidechainclient_processes[i] = subprocess.Popen(bashcmd.split())

    url = "http://rt:rt@%s:%d" % ('127.0.0.1' or rpchost, sc_rpc_port(i))
    proxy = SidechainAuthServiceProxy(url)
    proxy.url = url  # store URL on proxy for info
    return proxy


def start_sc_nodes(num_nodes, dirname, extra_args=None, rpchost=None, binary=None, print_output_to_file=False):
    """
    Start multiple SC clients, return connections to them
    """
    if extra_args is None: extra_args = [None for i in range(num_nodes)]
    if binary is None: binary = [None for i in range(num_nodes)]
    nodes = [start_sc_node(i, dirname, extra_args[i], rpchost, binary=binary[i], print_output_to_file=print_output_to_file) for i in range(num_nodes)]
    wait_for_sc_node_initialization(nodes)
    return nodes


def check_sc_node(i):
    '''
    Check subprocess return code.
    '''
    sidechainclient_processes[i].poll()
    return sidechainclient_processes[i].returncode


def stop_sc_node(node, i):
    # Must be changed with a sort of .stop() API Call
    sidechainclient_processes[i].kill()
    del sidechainclient_processes[i]


def stop_sc_nodes(nodes):
    # Must be changed with a sort of .stop() API call
    global sidechainclient_processes
    for sc in sidechainclient_processes.values():
        sc.kill()
    del nodes[:]


def set_sc_node_times(nodes, t):
    pass


def wait_sidechainclients():
    # Wait for all the processes to cleanly exit
    for sidechainclient in sidechainclient_processes.values():
        sidechainclient.wait()
    sidechainclient_processes.clear()


def connect_sc_nodes(from_connection, node_num, wait_for=25):
    """
    Connect a SC node, from_connection, to another one, specifying its node_num. 
    Method will attempt to create the connection for maximum wait_for seconds.
    """
    j = {"host": "127.0.0.1", \
         "port": str(sc_p2p_port(node_num))}
    ip_port = "\"127.0.0.1:" + str(sc_p2p_port(node_num)) + "\""
    print("Connecting to " + ip_port)
    oldnum = len(from_connection.node_connectedPeers()["result"]["peers"])
    from_connection.node_connect(json.dumps(j))
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise (TimeoutException("Trying to connect to node{0}".format(node_num)))
        newnum = len(from_connection.node_connectedPeers()["result"]["peers"])
        if newnum == (oldnum + 1):
            break
        time.sleep(WAIT_CONST)


def connect_sc_nodes_bi(nodes, a, b):
    connect_sc_nodes(nodes[a], b)
    connect_sc_nodes(nodes[b], a)


def connect_to_mc_node(sc_node, mc_node, *kwargs):
    pass


def assert_equal(expected, actual, message=""):
    if expected != actual:
        if message:
            message = "; %s" % message
        raise AssertionError("(left == right)%s\n  left: <%s>\n right: <%s>" % (message, str(expected), str(actual)))


def assert_true(condition, message=""):
    if not condition:
        raise AssertionError(message)


"""
Verify if a mainchain block data is equal to mainchain block reference info data.

Parameters:
 - mc_block_reference_info: the JSON representation of a mainchain block reference info. See com.horizen.node.util.MainchainBlockReferenceInfo
 - expected_mc_block: the JSON representation of a mainchain block
"""
def check_mainchain_block_reference_info(mc_block_reference_info, expected_mc_block):
    try:

        parent_hash = mc_block_reference_info["blockReferenceInfo"]["parentHash"]
        hash = mc_block_reference_info["blockReferenceInfo"]["hash"]
        height = mc_block_reference_info["blockReferenceInfo"]["height"]

        expected_mc_block_hash = expected_mc_block["hash"]
        expected_mc_block_height = expected_mc_block["height"]

        assert_equal(expected_mc_block_hash, hash)
        assert_equal(expected_mc_block_height, height)

        expected_mc_block_previousblockhash = expected_mc_block["previousblockhash"]

        assert_equal(expected_mc_block_previousblockhash, parent_hash)

        return True
    except Exception:
        return False

"""
Verify if a mainchain block is included in a sidechain block.

Parameters:
 - sc_block: the JSON representation of a sidechain block. See com.horizen.block.SidechainBlock
 - expected_mc_block: the JSON representation of a mainchain block
"""
def is_mainchain_block_included_in_sc_block(sc_block, expected_mc_block):

    mc_block_headers_json = sc_block["mainchainHeaders"]
    is_mac_block_included = False

    for mc_block_header_json in mc_block_headers_json:
        expected_mc_block_merkleroot = expected_mc_block["merkleroot"]

        sc_mc_block_merkleroot = mc_block_header_json["hashMerkleRoot"]

        if expected_mc_block_merkleroot == sc_mc_block_merkleroot:
            is_mac_block_included = True
            break

    return is_mac_block_included

"""
Verify the wallet balance is equal to an expected balance.

Parameters:
 - sc_node: a sidechain node
 - expected_wallet_balance
"""
def check_wallet_balance(sc_node, expected_wallet_balance):
    response = sc_node.wallet_balance()
    balance = response["result"]
    assert_equal(expected_wallet_balance * 100000000, int(balance["balance"]), "Unexpected balance")


"""
For a given Account verify the number of related boxes by type and verify the sum of their balances.

Parameters:
 - sc_node: a sidechain node
 - account: an instance of Account (see sc_bootstrap_info.py)
 - expected_boxes_count: the number of expected boxes for that account
 - expected_balance: expected balance for that account
"""
def check_box_balance(sc_node, account, box_type, expected_boxes_count, expected_balance):
    response = sc_node.wallet_allBoxes()
    boxes = response["result"]["boxes"]
    boxes_balance = 0
    boxes_count = 0
    pub_key = account.publicKey
    for box in boxes:
        if box["proposition"]["publicKey"] == pub_key and (box["typeId"] == box_type or box_type == 0):
            box_value = box["value"]
            assert_true(box_value > 0,
                        "Non positive value for box: {0} with public key: {1}".format(box["id"], pub_key))
            boxes_balance += box_value
            boxes_count += 1

    assert_equal(expected_boxes_count, boxes_count,
                "Unexpected number of boxes for public key {0}. Expected {1} but found {2}."
                .format(pub_key, expected_boxes_count, boxes_count))
    assert_equal(expected_balance * 100000000, boxes_balance,
                "Unexpected sum of balances for public key {0}. Expected {1} but found {2}."
                .format(pub_key, expected_balance * 100000000, boxes_balance))

# In STF we need to create SC genesis block with a timestamp in the past to be able to forge next block
# without receiving "block in future" error. By default we rewind half of the consensus epoch.
DefaultBlockTimestampRewind = 720 * 120 / 2

"""
Bootstrap a network of sidechain nodes.

Parameters:
 - network: an instance of SCNetworkConfiguration (see sc_boostrap_info.py)
                
Example: 2 mainchain nodes and 3 sidechain nodes (with default websocket configuration) bootstrapped, respectively, from mainchain node first, first, and third.
The JSON representation is only for documentation.
{
network: {
    "sc_creation_info":{
            "mainchain_node": mc_node_1,
            "sc_id": "id_1"
            "forward_amout": 200
            "withdrawal_epoch_length": 1000
        },
        [
            sidechain_1_configuration: {
                "mc_connection_info":{
                    "address": "ws://mc_node_1_hostname:mc_node_1_ws_port"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            },
            sidechain_2_configuration: {
                "mc_connection_info":{
                    "address": "ws://mc_node_1_hostname:mc_node_1_ws_port"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            
            },
            sidechain_3_configuration: {
                "mc_connection_info":{
                    "address": "ws://mc_node_2_hostname:mc_node_2_ws_port"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            }
        ]
    }
}
 
 Output:
 - bootstrap information of the sidechain nodes. An instance of SCBootstrapInfo (see sc_boostrap_info.py)    
"""
def bootstrap_sidechain_nodes(dirname, network=SCNetworkConfiguration, block_timestamp_rewind=DefaultBlockTimestampRewind):
    total_number_of_sidechain_nodes = len(network.sc_nodes_configuration)
    sc_creation_info = network.sc_creation_info
    sc_nodes_bootstrap_info = create_sidechain(sc_creation_info, block_timestamp_rewind)
    sc_nodes_bootstrap_info_empty_account = SCBootstrapInfo(sc_nodes_bootstrap_info.sidechain_id,
                                                            None,
                                                            sc_nodes_bootstrap_info.genesis_account_balance,
                                                            sc_nodes_bootstrap_info.mainchain_block_height,
                                                            sc_nodes_bootstrap_info.sidechain_genesis_block_hex,
                                                            sc_nodes_bootstrap_info.pow_data,
                                                            sc_nodes_bootstrap_info.network,
                                                            sc_nodes_bootstrap_info.withdrawal_epoch_length,
                                                            sc_nodes_bootstrap_info.genesis_vrf_account,
                                                            sc_nodes_bootstrap_info.certificate_proof_info)
    for i in range(total_number_of_sidechain_nodes):
        sc_node_conf = network.sc_nodes_configuration[i]
        if i == 0:
            bootstrap_sidechain_node(dirname, i, sc_nodes_bootstrap_info, sc_node_conf)
        else:
            bootstrap_sidechain_node(dirname, i, sc_nodes_bootstrap_info_empty_account, sc_node_conf)

    return sc_nodes_bootstrap_info

"""
Create a sidechain transaction inside a mainchain node.

Parameters:
 - sc_creation_info: an instance of SCCreationInfo (see sc_boostrap_info.py)
 
 Output:
  - an instance of SCBootstrapInfo (see sc_boostrap_info.py)
"""
def create_sidechain(sc_creation_info, block_timestamp_rewind):
    accounts = generate_secrets("seed", 1)
    vrf_keys = generate_vrf_secrets("seed", 1)
    genesis_account = accounts[0]
    vrf_key = vrf_keys[0]
    certificate_proof_info = generate_certificate_proof_info("seed", 7, 5)
    genesis_info = initialize_new_sidechain_in_mainchain(
                                    sc_creation_info.mc_node,
                                    sc_creation_info.withdrawal_epoch_length,
                                    genesis_account.publicKey,
                                    sc_creation_info.forward_amount,
                                    vrf_key.publicKey,
                                    certificate_proof_info.genSysConstant,
                                    certificate_proof_info.verificationKey)

    genesis_data = generate_genesis_data(genesis_info[0], genesis_account.secret, vrf_key.secret, block_timestamp_rewind)
    sidechain_id = genesis_info[2]

    return SCBootstrapInfo(sidechain_id, genesis_account, sc_creation_info.forward_amount, genesis_info[1],
                           genesis_data["scGenesisBlockHex"], genesis_data["powData"], genesis_data["mcNetwork"],
                           sc_creation_info.withdrawal_epoch_length, vrf_key, certificate_proof_info)

"""
Bootstrap one sidechain node: create directory and configuration file for the node.

Parameters:
 - n: sidechain node nth: used to create directory "sc_node_n"
 - bootstrap_info: an instance of SCBootstrapInfo (see sc_boostrap_info.py)
 - sc_node_configuration: an instance of SCNodeConfiguration (see sc_boostrap_info.py)
 
"""
def bootstrap_sidechain_node(dirname, n, bootstrap_info, sc_node_configuration):
    initialize_sc_datadir(dirname, n, bootstrap_info, sc_node_configuration)

def generate_forging_request(epoch, slot):
    return json.dumps({"epochNumber": epoch, "slotNumber": slot})


def get_next_epoch_slot(epoch, slot, slots_in_epoch, force_switch_to_next_epoch=False):
    next_slot = slot + 1
    next_epoch = epoch

    if next_slot > slots_in_epoch or force_switch_to_next_epoch:
        next_slot = 1
        next_epoch += 1
    return next_epoch, next_slot


def generate_next_block(node, node_name, force_switch_to_next_epoch=False):
    forging_info = node.block_forgingInfo()["result"]
    slots_in_epoch = forging_info["consensusSlotsInEpoch"]
    best_slot = forging_info["bestSlotNumber"]
    best_epoch = forging_info["bestEpochNumber"]

    next_epoch, next_slot = get_next_epoch_slot(best_epoch, best_slot, slots_in_epoch, force_switch_to_next_epoch)

    forge_result = node.block_generate(generate_forging_request(next_epoch, next_slot))

    #"while" will break if whole epoch no generated block, due changed error code
    while forge_result.has_key("error") and forge_result["error"]["code"] == "0105":
        if("no forging stake" in forge_result["error"]["description"]):
            raise AssertionError("No forging stake for the epoch")
        print("Skip block generation for {epochNumber} epoch and {slotNumber} slot".format(epochNumber = next_epoch, slotNumber = next_slot))
        next_epoch, next_slot = get_next_epoch_slot(next_epoch, next_slot, slots_in_epoch)
        forge_result = node.block_generate(generate_forging_request(next_epoch, next_slot))

    assert_true(forge_result.has_key("result"), "Error during block generation for SC {0}".format(node_name))
    block_id = forge_result["result"]["blockId"]
    print("Successfully forged block with id {blockId}".format(blockId = block_id))
    return forge_result["result"]["blockId"]


def generate_next_blocks(node, node_name, blocks_count):
    blocks_ids = []
    for i in range(blocks_count):
        blocks_ids.append(generate_next_block(node, node_name))
    return blocks_ids
