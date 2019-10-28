import os
import sys

from binascii import hexlify, unhexlify
from base64 import b64encode
from decimal import Decimal, ROUND_DOWN
import json
import random
from sidechainauthproxy import SidechainAuthServiceProxy
import shutil
import subprocess
import time
import random
import string
import socket
from contextlib import closing
import platform

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
        height = int(node.block_getBestBlockInfo()["result"]["height"])
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

"""
Genrate a genesis info by calling ScBootstrappingTools with command "genesisinfo"
Parameters:
 - n: sidechain node nth
 - genesis_info: genesis info provided by a mainchain node
 
Output: a JSON object to be included in the settings file of the sidechain node nth.
{
    "scId": "id of the sidechain node",
    "scGenesisBlockHex": "some value",
    "powData": "some value",
    "mcBlockHeight": xxx,
    "mcNetwork": regtest|testnet|mainnet
}
"""
def generate_genesis_data(n, genesis_info):
    jsonSecret = generate_secret(n, 1)[0]
    jsonParameters = {"secret": jsonSecret["secret"], "info": genesis_info}
    javaPs = subprocess.Popen(["java", "-cp",
                               "../tools/sctool/target/Sidechains-SDK-ScBootstrappingTools-0.1-SNAPSHOT.jar:../tools/sctool/target/lib/*",
                               "com.horizen.ScBootstrappingTool",
                               "genesisinfo", json.dumps(jsonParameters)], stdout=subprocess.PIPE)
    scBootstrapOutput = javaPs.communicate()[0]
    jsonNode = json.loads(scBootstrapOutput)
    return jsonNode

"""
Genrate a secret by calling ScBootstrappingTools with command "generatekey"
Parameters:
 - n: sidechain node nth
 - number_of_accounts: the number of keys to be generated
 
Output: a JSON array of pairs secret-public key.
[
    {
        "secret":"first secret",
        "publicKey":"first public key"
    },
    {
        "secret":"second secret",
        "publicKey":"second public key"
    },
    ...,
    {
        "secret":"nth secret",
        "publicKey":"nth public key"
    }
]
"""
def generate_secret(n, number_of_accounts):
    secrets = []
    for i in range(number_of_accounts):
        jsonParameters = {"seed": "sidechain_seed_{0}_{1}".format(n, i + 1)}
        javaPs = subprocess.Popen(["java", "-cp",
                                   "../tools/sctool/target/Sidechains-SDK-ScBootstrappingTools-0.1-SNAPSHOT.jar:../tools/sctool/target/lib/*",
                                   "com.horizen.ScBootstrappingTool",
                                   "generatekey", json.dumps(jsonParameters)], stdout=subprocess.PIPE)
        scBootstrapOutput = javaPs.communicate()[0]
        secrets.append(json.loads(scBootstrapOutput))
    return secrets


# Maybe should we give the possibility to customize the configuration file by adding more fields ?

"""
Create directories for each node and configuration files inside them.
For each node put also genesis data in configuration files.
Configuration data must be automatically generated and different from the ones generated for the other nodes.

Parameters:
 - dirname: directory name
 - n: sidechain node nth
 - account_secrets: a JSON array of secrets, in the following form:
            [
                {
                    "secret":"first secret",
                    "publicKey":"first public key"
                },
                {
                    "secret":"second secret",
                    "publicKey":"second public key"
                },
                ...,
                {
                    "secret":"nth secret",
                    "publicKey":"nth public key"
                }
            ]
 - genesis_info: a JSON object, to be included inside configuration file of the sidechain node nth, in the following form:
             {
                "scId": "id of the sidechain node",
                "scGenesisBlockHex": "some value",
                "powData": "some value",
                "mcBlockHeight": xxx,
                "mcNetwork": regtest|testnet|mainnet
            }
"""
def initialize_sc_datadir(dirname, n, account_secrets=None, genesis_info=None):
    """Create directories for each node and configuration files inside them.
       For each node put also genesis data in configuration files.
       Configuration data must be automatically generated and different from
       the ones generated for the other nodes."""
    jsonNode = generate_genesis_data(n, genesis_info) if genesis_info != None else {
        "scId": "0000000000000000000000000000000000000000000000000000000000000001",
        "scGenesisBlockHex": "000000000000000000000000000000000000000000000000000000000000000090e2c0da0bfe0a02f80ae2020000002059594653464ae2f7ca0f61550a84319727b6148faa97d3f1f223fd5a61e38c09f79532fa43d0b37e577019aa7c3a4279a5093ceee601414ec998f1f561ecb218a92d8f0ed96e906cba10bcad2d4c6bfacbb17dc23b25b90c96f58ec23c8b468a3718a85d030f0f205b00f1ec130abec63ba3b87fef3776d9d5e5da7d3ad74cbf385ad29b46d9000024007b537a44aeeb37b828579bd3e44a95618b00b5c9aeb2546d410519e1c6d972a79a170c8a079c19b53cc2c9feffe768d9ed0cc8c9cd364609cb7f23d5743f910d43827f1efc000000005da8183700000199089201da01da01da01030100000000000000000000000000000000000000000000000000000000000000e803000051f0fded38e0cfe424e2f9617c0439535bcef19e291f1387494c1dc3ea9b4825000000000100e40b54020000006692769b15d740102160a0c460dea3118a19482e7fe3f21c7f13fd674717970c010000000000000000000000000000000000000000000000000000000000000051f0fded38e0cfe424e2f9617c0439535bcef19e291f1387494c1dc3ea9b4825000000010100e40b54020000004e8ac0f370c6ce0da26288888524c4a992294d38f3276d19fd444c7f4e7258c9010000000000000000000000000000000000000000000000000000000000000051f0fded38e0cfe424e2f9617c0439535bcef19e291f1387494c1dc3ea9b4825000000020100e40b54020000007f00c4d6400790d99422609ea5f63560d2315c1a163b6050cc747eaeb56fb3e9010000000000000000000000000000000000000000000000000000000000000051f0fded38e0cfe424e2f9617c0439535bcef19e291f1387494c1dc3ea9b482500000003800100000000000000000000000000000000000000000000000000000000000000019c19b53cc2c9feffe768d9ed0cc8c9cd364609cb7f23d5743f910d43827f1efc0200637ea289f8740c5f3f94baafd7ca6cf458cb6f811b36316d360a5cf20e408b29bb607fd059792d11efedb232c7fde205f7f3dda6c9b950bb1cbd9004f40967b474360a19a8bbea52435c1b6fc420da8aad29c9a15d02149d94dd9dce5ac2fb8b",
        "powData": "a117a85d030f0f20a117a85d030f0f20a117a85d030f0f20a017a85d030f0f20a017a85d030f0f20a017a85d030f0f20a017a85d030f0f20a017a85d030f0f20a017a85d030f0f209f17a85d030f0f209f17a85d030f0f209f17a85d030f0f209f17a85d030f0f209f17a85d030f0f209f17a85d030f0f209e17a85d030f0f209e17a85d040f0f209e17a85d040f0f209e17a85d040f0f209e17a85d040f0f209e17a85d040f0f209d17a85d040f0f209d17a85d040f0f209d17a85d040f0f209d17a85d040f0f209d17a85d040f0f209d17a85d040f0f209c17a85d040f0f20",
        "mcBlockHeight": 221,
        "mcNetwork": "regtest"
    }

    genesis_secrets = None

    if(account_secrets==None):
        genesis_secrets = {0 : "6882a61d8a23a9582c7c7e659466524880953fa25d983f29a8e3aa745ee6de5c0c97174767fd137f1cf2e37f2e48198a11a3de60c4a060211040d7159b769266", \
                           1 : "905e2e581615ba0eff2bcd9fb666b4f6f6ed99ddd05208ae7918a25dc6ea6179c958724e7f4c44fd196d27f3384d2992a9c42485888862a20dcec670f3c08a4e", \
                           2 : "80b9a06608fa5dbd11fb72d28b9df49f6ac69f0e951ca1d9e67abd404559606be9b36fb5ae7e74cc50603b161a5c31d26035f6a59e602294d9900740d6c4007f"}
    else:
        genesis_secrets = []
        for i in range(len(account_secrets)):
            genesis_secrets.append(str(account_secrets[i]["secret"]))

    apiAddress = "127.0.0.1"
    with open('./resources/template.conf', 'r') as templateFile:
        tmpConfig = templateFile.read()
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)
    config = tmpConfig % {
        'NODE_NUMBER': n,
        'DIRECTORY': dirname,
        'WALLET_SEED': "sidechain_seed_{0}".format(n),
        'API_ADDRESS': "127.0.0.1",
        'API_PORT': str(apiPort),
        'BIND_PORT': str(bindPort),
        'OFFLINE_GENERATION': "false",
        'GENESIS_SECRETS': genesis_secrets[n] if account_secrets==None else ','.join(['"{0}"'.format(value) for value in genesis_secrets])[1:-1],
        'SIDECHAIN_ID': jsonNode["scId"],
        'GENESIS_DATA': jsonNode["scGenesisBlockHex"],
        'POW_DATA': jsonNode["powData"],
        'BLOCK_HEIGHT': jsonNode["mcBlockHeight"],
        'NETWORK': str(jsonNode["mcNetwork"])
    }
    configsData.append({
        "name": "node" + str(n),
        "url": "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node" + str(n) + ".conf"), 'w+') as configFile:
        configFile.write(config)
    return configsData


def sc_generate_genesis_data(self):
    return generate_genesis_data(self.nodes[0])  # Maybe other parameters in future


def initialize_sc_chain(test_dir, genesisData):
    pass


def initialize_sc_chain_clean(test_dir, num_nodes, account_secrets=None, genesis_info=None):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        initialize_sc_datadir(test_dir, i, account_secrets, genesis_info)


def start_sc_node(i, dirname, extra_args=None, rpchost=None, timewait=None, binary=None):
    """
    Start a SC node and returns API connection to it
    """
    # Will we have  extra args for SC too ?
    datadir = os.path.join(dirname, "sc_node" + str(i))

    if binary is None:
        binary = "../examples/simpleapp/target/Sidechains-SDK-simpleapp-0.1-SNAPSHOT.jar:../examples/simpleapp/target/lib/* com.horizen.examples.SimpleApp"
    #        else if platform.system() == 'Linux':
    bashcmd = 'java -cp ' + binary + " " + (datadir + ('/node%s.conf' % i))
    sidechainclient_processes[i] = subprocess.Popen(bashcmd.split())
    url = "http://rt:rt@%s:%d" % ('127.0.0.1' or rpchost, sc_rpc_port(i))
    proxy = SidechainAuthServiceProxy(url)
    proxy.url = url  # store URL on proxy for info
    return proxy


def start_sc_nodes(num_nodes, dirname, extra_args=None, rpchost=None, binary=None):
    """
    Start multiple SC clients, return connections to them
    """
    if extra_args is None: extra_args = [None for i in range(num_nodes)]
    if binary is None: binary = [None for i in range(num_nodes)]
    nodes = [start_sc_node(i, dirname, extra_args[i], rpchost, binary=binary[i]) for i in range(num_nodes)]
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
