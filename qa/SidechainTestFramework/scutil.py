import os
import sys

from binascii import hexlify, unhexlify
from base64 import b64encode
from decimal import Decimal, ROUND_DOWN
import json
import random
from sidechainauthproxy import SidechainAuthServiceProxy
from util import check_json_precision, bytes_to_hex_str, hex_str_to_bytes, str_to_b64str, _rpchost_to_args, log_filename, \
                 assert_true, assert_false, assert_equal, assert_greater_than, assert_raises
import shutil
import subprocess
import time
import random
import string
import socket
from contextlib import closing

WAIT_CONST = 1

class TimeoutException(Exception):
    def __init__(self, operation):
        Exception.__init__(self)
        self.operation = operation

def sc_p2p_port(n):
    return 8300 + n + os.getpid()%999

def sc_rpc_port(n):
    return 8200 + n + os.getpid()%999

def wait_for_next_sc_blocks(node, expected_height, wait_for = 25):
    """
    Wait until blockchain height won't reach the expected_height, for wait_for seconds
    """
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise TimeoutException("Waiting blocks")
        height = int(node.debug_info()["height"])
        if  height >= expected_height:
            break
        time.sleep(WAIT_CONST)
    print("Elapsed time for block generation: ", time.time() - start)

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
        counts = [ int(x.debug_info()["height"]) for x in api_connections ]
        if p :
            print (counts)
        if counts == [ counts[0] ]*len(counts):
            break
        time.sleep(WAIT_CONST)
    print("Elapsed time for block synchronization: ", time.time() - start)

def sync_sc_mempools(api_connections, wait_for=25):
    """
    Wait for maximum wait_for seconds for everybody to have the same transactions in their memory pools
    """
    start = time.time()
    while True:
        refpool = api_connections[0].nodeView_pool()["transactions"]
        if time.time() - start >= wait_for:
            raise TimeoutException("Syncing mempools")
        num_match = 1
        for i in range(1, len(api_connections)):
            nodepool = api_connections[i].nodeView_pool()["transactions"]
            if cmp(nodepool, refpool) == 0:
                num_match = num_match+1
        if num_match == len(api_connections):
            break
        time.sleep(WAIT_CONST)
    print("Elapsed time for mempool synchronization: ", time.time() - start)
    
sidechainclient_processes = {}

def generate_genesis_data(node):
    #TO IMPLEMENT: Create sidechaindeclarationtx, mine a block, call getgenesisdata and return them
    return None

#Just for hybrid app, will be removed
def getGenesisAddresses(nodeNumber):
    if nodeNumber < 2:
        return 19
    elif nodeNumber == 2:
        return 9
    return 0

#Maybe should we give the possibility to customize the configuration file by adding more fields ?
def initialize_sc_datadir(dirname, n, genesisData):
    """Create directories for each node and configuration files inside them.
       For each node put also genesis data in configuration files. 
       Configuration data must be automatically generated and different from 
       the ones generated for the other nodes."""
    apiAddress = "127.0.0.1"
    with open('./resources/template.conf','r') as templateFile: 
        tmpConfig = templateFile.read()
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node"+str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)
    config = tmpConfig % {
        'NODE_NUMBER' : n,
        'DIRECTORY' : dirname,
        'WALLET_SEED' : ("minerNode" + str(n+1)) if n < 3 else ("node" + str(n+1)),
        'API_ADDRESS' : "127.0.0.1",
        'API_PORT' : str(apiPort),
        'BIND_PORT' : str(bindPort),
        'OFFLINE_GENERATION' : "false",
        'GENESIS_DATA': "" if genesisData is None else str(genesisData)
        }
    configsData.append({
        "name" : "node" + str(n),
        "genesisAddresses" : getGenesisAddresses(n),
        "url" : "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node"+str(n)+".conf"), 'w+') as configFile:
        configFile.write(config)
    return configsData

def initialize_sc_chain(test_dir, genesisData):
    pass

def initialize_sc_chain_clean(test_dir, num_nodes, genesisData):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        initialize_sc_datadir(test_dir, i, genesisData)

def start_sc_node(i, dirname, extra_args=None, rpchost=None, timewait=None, binary = None):
    """
    Start a SC node and returns API connection to it
    """
    #Will we have  extra args for SC too ?
    datadir = os.path.join(dirname, "sc_node"+str(i))
    if binary is None:
        binary = "resources/twinsChain.jar examples.hybrid.HybridApp"
    bashcmd = 'java -cp ' + binary + " " + (datadir + ('/node%s.conf' % i))
    sidechainclient_processes[i] = subprocess.Popen(bashcmd.split())
    url = "http://rt:rt@%s:%d" % ('127.0.0.1' or rpchost, sc_rpc_port(i))
    proxy = SidechainAuthServiceProxy(url)
    proxy.url = url # store URL on proxy for info
    return proxy

def start_sc_nodes(num_nodes, dirname, extra_args=None, rpchost=None, binary = None):
    """
    Start multiple SC clients, return connections to them
    """
    if extra_args is None: extra_args = [ None for i in range(num_nodes) ]
    if binary is None: binary = [ None for i in range(num_nodes) ]
    nodes =  [ start_sc_node(i, dirname, extra_args[i], rpchost, binary=binary[i]) for i in range(num_nodes) ]
    wait_for_sc_node_initialization(nodes)
    return nodes

def check_sc_node(i):
    '''
    Check subprocess return code.
    '''
    sidechainclient_processes[i].poll()
    return sidechainclient_processes[i].returncode

def stop_sc_node(node, i):
    #Must be changed with a sort of .stop() API Call
    sidechainclient_processes[i].kill()
    del sidechainclient_processes[i]

def stop_sc_nodes(nodes):
    #Must be changed with a sort of .stop() API call
    global sidechainclient_processes
    for sc in sidechainclient_processes.values():
        sc.kill()
    del sidechainclient_processes

def set_sc_node_times(nodes, t):
    pass

def wait_sidechainclients():
    # Wait for all the processes to cleanly exit
    for sidechainclient in sidechainclient_processes.values():
        sidechainclient.wait()
    sidechainclient_processes.clear()

def connect_sc_nodes(from_connection, node_num, wait_for = 25):
    """
    Connect a SC node, from_connection, to another one, specifying its node_num. 
    Method will attempt to create the connection for maximum wait_for seconds.
    """
    ip_port = "\"127.0.0.1:"+str(sc_p2p_port(node_num))+"\""
    oldnum = len(from_connection.peers_connected())
    from_connection.peers_connect(ip_port)
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise(TimeoutException("Trying to connect to node{0}".format(node_num)))
        if len(from_connection.peers_connected()) == (oldnum + 1):
            break
        time.sleep(WAIT_CONST)

def connect_sc_nodes_bi(nodes, a, b):
    connect_sc_nodes(nodes[a], b)
    connect_sc_nodes(nodes[b], a)
    
def connect_to_mc_node(sc_node, mc_node, *kwargs):
    pass