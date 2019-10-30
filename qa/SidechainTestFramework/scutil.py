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

"""
Generate a genesis info by calling ScBootstrappingTools with command "genesisinfo"
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
    jsonSecret = generate_secrets(n, 1)[0]
    jsonParameters = {"secret": jsonSecret["secret"], "info": genesis_info}
    javaPs = subprocess.Popen(["java", "-cp",
                               "../tools/sctool/target/Sidechains-SDK-ScBootstrappingTools-0.1-SNAPSHOT.jar:../tools/sctool/target/lib/*",
                               "com.horizen.ScBootstrappingTool",
                               "genesisinfo", json.dumps(jsonParameters)], stdout=subprocess.PIPE)
    scBootstrapOutput = javaPs.communicate()[0]
    jsonNode = json.loads(scBootstrapOutput)
    return jsonNode

"""
Generate secrets by calling ScBootstrappingTools with command "generatekey"
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
def generate_secrets(n, number_of_accounts):
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
            
NB: if either account_secrets=None or genesis_info=None, the template_predefined_genesis.conf will be considered
"""
def initialize_sc_datadir(dirname, n, account_secrets=None, genesis_info=None):
    """Create directories for each node and configuration files inside them.
       For each node put also genesis data in configuration files.
       Configuration data must be automatically generated and different from
       the ones generated for the other nodes."""

    apiAddress = "127.0.0.1"
    configsData = []
    config = None
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)

    if(account_secrets!=None and genesis_info != None):
        with open('./resources/template.conf', 'r') as templateFile:
            tmpConfig = templateFile.read()
        genesis_secrets = []
        for i in range(len(account_secrets)):
            genesis_secrets.append(str(account_secrets[i]["secret"]))

        jsonNode = generate_genesis_data(n, genesis_info)
        config = tmpConfig % {
            'NODE_NUMBER': n,
            'DIRECTORY': dirname,
            'WALLET_SEED': "sidechain_seed_{0}".format(n),
            'API_ADDRESS': "127.0.0.1",
            'API_PORT': str(apiPort),
            'BIND_PORT': str(bindPort),
            'OFFLINE_GENERATION': "false",
            'GENESIS_SECRETS': ','.join(['"{0}"'.format(value) for value in genesis_secrets])[1:-1],
            'SIDECHAIN_ID': jsonNode["scId"],
            'GENESIS_DATA': jsonNode["scGenesisBlockHex"],
            'POW_DATA': jsonNode["powData"],
            'BLOCK_HEIGHT': jsonNode["mcBlockHeight"],
            'NETWORK': str(jsonNode["mcNetwork"])
        }
    else:
        with open('./resources/template_predefined_genesis.conf', 'r') as templateFile:
            tmpConfig = templateFile.read()
        genesis_secrets = []
        for i in range(len(account_secrets)):
            genesis_secrets.append(str(account_secrets[i]["secret"]))
        config = tmpConfig % {
            'NODE_NUMBER': n,
            'DIRECTORY': dirname,
            'WALLET_SEED': "sidechain_seed_{0}".format(n),
            'API_ADDRESS': "127.0.0.1",
            'API_PORT': str(apiPort),
            'BIND_PORT': str(bindPort),
            'OFFLINE_GENERATION': "false"
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
