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

def sc_p2p_port(n):
    return 8300 + n + os.getpid()%999
def sc_rpc_port(n):
    return 8200 + n + os.getpid()%999

def sync_sc_blocks(rpc_connections, wait=1, p=False, limit_loop=0):
    """
    Wait until everybody has the same block count or a limit has been exceeded
    """
    pass

def sync_sc_mempools(rpc_connections, wait=1):
    """
    Wait until everybody has the same transactions in their memory
    pools
    """
    pass

sidechainclient_processes = {}

def generateGenesisData(node):
    #TO IMPLEMENT: Create sidechaindeclarationtx, mine a block, call getgenesisdata and return them
    return None

#Maybe should we give the possibility to customize the configuration file by adding more fields ?
def initialize_sc_datadir(dirname, n, genesisData):
    '''Put also genesis data in nodes configuration files. Configuration data must be automatically generated and different from 
    the ones generated for the other nodes.'''
    apiAddress = "127.0.0.1"
    with open('./resources/template.conf','r') as templateFile: 
        tmpConfig = templateFile.read()
    configsData = []
    r = random.randint(0, n+1)
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node"+str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)
    config = tmpConfig % {
        'NODE_NUMBER' : n,
        'DIRECTORY' : dirname,
        #'WALLET_SEED' : ''.join([random.choice(string.ascii_letters + string.digits) for tmp in range(32)]),
        'WALLET_SEED' : ("minerNode" + str(n+1)) if n < 3 else ("node" + str(n+1)),
        'API_ADDRESS' : "127.0.0.1",
        'API_PORT' : str(apiPort),
        'BIND_PORT' : str(bindPort),
        'KNOWN_PEERS' : "" if n == 0 else "\"" + apiAddress + ":" + str(bindPort-1) + "\"" ,
        'OFFLINE_GENERATION' : "false",
        'GENESIS_DATA': "" if genesisData is None else str(genesisData)
        }
    configsData.append({
        "name" : "node" + str(n),
        "genesisAddresses" : r*n,
        "url" : "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node"+str(n)+".conf"), 'w+') as configFile:
        configFile.write(config)
    return configsData

def initialize_sc_chain(test_dir, genesisData):
    """
    Create (or copy from cache) a 200-block-long chain and
    4 wallets.
    zend and zen-cli must be in search path.
    """
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
    bashcmd = 'java -cp ' + binary + " " + (datadir + ('/node%s.conf' % i))
    sidechainclient_processes[i] = subprocess.Popen(bashcmd.split())
    time.sleep(20) #Temporarily
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
    return [ start_sc_node(i, dirname, extra_args[i], rpchost, binary=binary[i]) for i in range(num_nodes) ]

def check_sc_node(i):
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

def connect_sc_nodes(from_connection, node_num):
    pass

def connect_sc_nodes_bi(nodes, a, b):
    connect_sc_nodes(nodes[a], b)
    connect_sc_nodes(nodes[b], a)
    
def connect_to_mc_node(sc_node, mc_node, *kwargs):
    pass
