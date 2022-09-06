import pprint

import psutil
import logging
import multiprocessing
import os
import sys
import json
from decimal import Decimal
import jsonschema
from jsonschema.validators import validate
from SidechainTestFramework.sc_boostrap_info import MCConnectionInfo, SCBootstrapInfo, SCNetworkConfiguration, Account, \
    VrfAccount, SchnorrAccount, CertificateProofInfo, SCNodeConfiguration, ProofKeysPaths, LARGE_WITHDRAWAL_EPOCH_LENGTH, \
    DEFAULT_API_KEY
from SidechainTestFramework.sidechainauthproxy import SidechainAuthServiceProxy
import subprocess
import time
import socket
from contextlib import closing
from test_framework.mc_test.mc_test import generate_random_field_element_hex, get_field_element_with_padding
from test_framework.util import initialize_new_sidechain_in_mainchain, get_spendable, swap_bytes, assert_equal

WAIT_CONST = 1

# log levels of the log4j trace system used by java applications
LEVEL_OFF = "off"
LEVEL_FATAL = "fatal"
LEVEL_ERROR = "error"
LEVEL_WARN = "warn"
LEVEL_INFO = "info"
LEVEL_DEBUG = "debug"
LEVEL_TRACE = "trace"
LEVEL_ALL = "all"

# timeout in secs for rest api
DEFAULT_REST_API_TIMEOUT = 5


class TimeoutException(Exception):
    def __init__(self, operation):
        Exception.__init__(self)
        self.operation = operation


class LogInfo(object):
    def __init__(self, logFileLevel=LEVEL_ALL, logConsoleLevel=LEVEL_ERROR):
        self.logFileLevel = logFileLevel
        self.logConsoleLevel = logConsoleLevel


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
            print(counts)
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
            if nodepool == refpool:
                num_match = num_match + 1
        if num_match == len(api_connections):
            break
        time.sleep(WAIT_CONST)


sidechainclient_processes = {}


def launch_bootstrap_tool(command_name, json_parameters):
    json_param = json.dumps(json_parameters)
    java_ps = subprocess.Popen(["java", "-jar",
                                os.getenv("SIDECHAIN_SDK",
                                          "..") + "/tools/sctool/target/sidechains-sdk-scbootstrappingtools-0.5.0-SNAPSHOT.jar",
                                command_name, json_param], stdout=subprocess.PIPE)
    sc_bootstrap_output = java_ps.communicate()[0]
    try:
        jsone_node = json.loads(sc_bootstrap_output)
        return jsone_node
    except ValueError:
        print("Bootstrap tool error occurred for command= {}\nparams: {}\nError: {}\n"
              .format(command_name, json_param, sc_bootstrap_output.decode()))
        raise Exception("Bootstrap tool error occurred")


def launch_db_tool(dirName, command_name, json_parameters):
    '''
    we use "blockchain" postfix for specifying the dataDir (see qa/resources/template.conf:
        dataDir = "%(DIRECTORY)s/sc_node%(NODE_NUMBER)s/blockchain"
    '''
    storagesPath = dirName + "/blockchain"

    json_param = json.dumps(json_parameters)
    java_ps = subprocess.Popen(["java", "-jar",
                                os.getenv("SIDECHAIN_SDK",
                                          "..") + "/tools/dbtool/target/sidechains-sdk-dbtools-0.5.0-SNAPSHOT.jar",
                                storagesPath, command_name, json_param], stdout=subprocess.PIPE)
    db_tool_output = java_ps.communicate()[0]
    try:
        jsone_node = json.loads(db_tool_output)
        return jsone_node
    except ValueError:
        print("DB tool error occurred for command= {}\nparams: {}\nError: {}\n"
              .format(command_name, json_param, db_tool_output.decode()))
        raise Exception("DB tool error occurred")


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
    "initialCumulativeCommTreeHash": xxx
}
"""


def generate_genesis_data(genesis_info, genesis_secret, vrf_secret, block_timestamp_rewind):
    jsonParameters = {"secret": genesis_secret, "vrfSecret": vrf_secret, "info": genesis_info,
                      "regtestBlockTimestampRewind": block_timestamp_rewind}
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


"""
Generate Schnorr keys by calling ScBootstrappingTools with command "generateCertificateSignerKey"
Parameters:
 - seed
 - number_of_accounts: the number of keys to be generated

Output: an array of instances of SchnorrKey (see sc_bootstrap_info.py).
"""
def generate_cert_signer_secrets(seed, number_of_schnorr_keys):
    schnorr_keys = []
    secrets = []
    for i in range(number_of_schnorr_keys):
        jsonParameters = {"seed": "{0}_{1}".format(seed, i + 1)}
        secrets.append(launch_bootstrap_tool("generateCertificateSignerKey", jsonParameters))

    for i in range(len(secrets)):
        secret = secrets[i]
        schnorr_keys.append(SchnorrAccount(secret["signerSecret"], secret["signerPublicKey"]))
    return schnorr_keys


# Maybe should we give the possibility to customize the configuration file by adding more fields ?

"""
Generate withdrawal certificate proof info calling ScBootstrappingTools with command "generateProofInfo"
Parameters:
 - seed
 - number_of_signer_keys: the number of schnorr keys to be generated
 - threshold: the minimum set of the participants required for a valid proof creation
 - keys_paths: instance of ProofKeysPaths. Contains paths to load/generate Coboundary Marlin snark keys
 - isCSWEnabled: if ceased sidechain withdrawal is enabled or not

Output: CertificateProofInfo (see sc_bootstrap_info.py).
"""


def generate_certificate_proof_info(seed, number_of_signer_keys, threshold, keys_paths, is_csw_enabled):
    signer_keys = generate_cert_signer_secrets(seed, number_of_signer_keys)

    signer_secrets = []
    signer_public_keys = []
    for i in range(len(signer_keys)):
        keys = signer_keys[i]
        signer_secrets.append(keys.secret)
        signer_public_keys.append(keys.publicKey)

    json_parameters = {
        "signersPublicKeys": signer_public_keys,
        "threshold": threshold,
        "provingKeyPath": keys_paths.proving_key_path,
        "verificationKeyPath": keys_paths.verification_key_path,
        "isCSWEnabled": is_csw_enabled
    }
    output = launch_bootstrap_tool("generateCertProofInfo", json_parameters)

    threshold = output["threshold"]
    verification_key = output["verificationKey"]
    gen_sys_constant = output["genSysConstant"]

    certificate_proof_info = CertificateProofInfo(threshold, gen_sys_constant, verification_key, signer_secrets,
                                                  signer_public_keys)
    return certificate_proof_info


"""
return a string like '["127.0.0.1:xxxx","127.0.0.1:xxxx"]' to be set as known Peers in the configuration.
Based on a index vector [0, 2, 3] where all peers could be [0,1,2,3,4]

Parameters:
 - known_peers_indexes: indexes of the known peers
"""


def get_known_peers(known_peers_indexes):
    addresses = []
    for index in known_peers_indexes:
        addresses.append("\"" + ("127.0.0.1:" + str(sc_p2p_port(index))) + "\"")
    peers = "[" + ",".join(addresses) + "]"
    return peers


"""
Generate ceased sidechain withdrawal proof info calling ScBootstrappingTools with command "generateCswProofInfo"
Parameters:
 - withdrawalEpochLen
 - keys_paths - instance of ProofKeysPaths. Contains paths to load/generate Coboundary Marlin snark keys

Output: Verification key
"""


def generate_csw_proof_info(withdrawal_epoch_len, keys_paths):
    json_parameters = {
        "withdrawalEpochLen": withdrawal_epoch_len,
        "provingKeyPath": keys_paths.proving_key_path,
        "verificationKeyPath": keys_paths.verification_key_path
    }
    output = launch_bootstrap_tool("generateCswProofInfo", json_parameters)

    verification_key = output["verificationKey"]
    return verification_key


"""
Create directories for each node and configuration files inside them.
For each node put also genesis data in configuration files.

Parameters:
 - dirname: directory name
 - n: sidechain node nth
 - bootstrap_info: an instance of SCBootstrapInfo (see sc_bootstrap_info.py)
 - websocket_config: an instance of MCConnectionInfo (see sc_boostrap_info.py)
"""


def initialize_sc_datadir(dirname, n, bootstrap_info=SCBootstrapInfo, sc_node_config=SCNodeConfiguration(),
                          log_info=LogInfo(), rest_api_timeout=DEFAULT_REST_API_TIMEOUT):
    apiAddress = "127.0.0.1"
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    mc0datadir = os.path.join(dirname, "node0")
    websocket_config = sc_node_config.mc_connection_info
    if not os.path.isdir(datadir):
        os.makedirs(datadir)

    customFileName = './resources/template_' + str(n + 1) + '.conf'
    fileToOpen = './resources/template.conf'
    if os.path.isfile(customFileName):
        fileToOpen = customFileName

    with open(fileToOpen, 'r') as templateFile:
        tmpConfig = templateFile.read()

    genesis_secrets = []
    if bootstrap_info.genesis_vrf_account is not None:
        genesis_secrets.append(bootstrap_info.genesis_vrf_account.secret)

    if bootstrap_info.genesis_account is not None:
        genesis_secrets.append(bootstrap_info.genesis_account.secret)

    all_private_keys = bootstrap_info.certificate_proof_info.schnorr_secrets
    signer_private_keys = [all_private_keys[idx] for idx in sc_node_config.submitter_private_keys_indexes]
    api_key_hash = ""
    if sc_node_config.api_key != "":
        api_key_hash = calculateApiKeyHash(sc_node_config.api_key)
    config = tmpConfig % {
        'NODE_NUMBER': n,
        'DIRECTORY': dirname,
        'LOG_FILE_LEVEL': log_info.logFileLevel,
        'LOG_CONSOLE_LEVEL': log_info.logConsoleLevel,
        'WALLET_SEED': "sidechain_seed_{0}".format(n),
        'API_ADDRESS': "127.0.0.1",
        'API_PORT': str(apiPort),
        'API_KEY_HASH': api_key_hash,
        'API_TIMEOUT': (str(rest_api_timeout) + "s"),
        'BIND_PORT': str(bindPort),
        'MAX_CONNECTIONS': sc_node_config.max_connections,
        'OFFLINE_GENERATION': "false",
        'GENESIS_SECRETS': json.dumps(genesis_secrets),
        'MAX_TX_FEE': sc_node_config.max_fee,
        'MEMPOOL_MAX_SIZE': sc_node_config.mempool_max_size,
        'MEMPOOL_MIN_FEE_RATE': sc_node_config.mempool_min_fee_rate,
        'SIDECHAIN_ID': bootstrap_info.sidechain_id,
        'GENESIS_DATA': bootstrap_info.sidechain_genesis_block_hex,
        'POW_DATA': bootstrap_info.pow_data,
        'BLOCK_HEIGHT': bootstrap_info.mainchain_block_height,
        'NETWORK': bootstrap_info.network,
        'WITHDRAWAL_EPOCH_LENGTH': bootstrap_info.withdrawal_epoch_length,
        'INITIAL_COMM_TREE_CUMULATIVE_HASH': bootstrap_info.initial_cumulative_comm_tree_hash,
        'WEBSOCKET_ADDRESS': websocket_config.address,
        'CONNECTION_TIMEOUT': websocket_config.connectionTimeout,
        'RECONNECTION_DELAY': websocket_config.reconnectionDelay,
        'RECONNECTION_MAX_ATTEMPTS': websocket_config.reconnectionMaxAttempts,
        "THRESHOLD": bootstrap_info.certificate_proof_info.threshold,
        "SUBMITTER_CERTIFICATE": ("true" if sc_node_config.cert_submitter_enabled else "false"),
        "CERTIFICATE_SIGNING": ("true" if sc_node_config.cert_signing_enabled else "false"),
        "SIGNER_PUBLIC_KEY": json.dumps(bootstrap_info.certificate_proof_info.schnorr_public_keys),
        "SIGNER_PRIVATE_KEY": json.dumps(signer_private_keys),
        "MAX_PKS": len(bootstrap_info.certificate_proof_info.schnorr_public_keys),
        "CERT_PROVING_KEY_PATH": bootstrap_info.cert_keys_paths.proving_key_path,
        "CERT_VERIFICATION_KEY_PATH": bootstrap_info.cert_keys_paths.verification_key_path,
        "AUTOMATIC_FEE_COMPUTATION": ("true" if sc_node_config.automatic_fee_computation else "false"),
        "CERTIFICATE_FEE": sc_node_config.certificate_fee,
        "CSW_PROVING_KEY_PATH": bootstrap_info.csw_keys_paths.proving_key_path if bootstrap_info.csw_keys_paths is not None else "",
        "CSW_VERIFICATION_KEY_PATH": bootstrap_info.csw_keys_paths.verification_key_path if bootstrap_info.csw_keys_paths is not None else "",
        "RESTRICT_FORGERS": ("true" if sc_node_config.forger_options.restrict_forgers else "false"),
        "ALLOWED_FORGERS_LIST": sc_node_config.forger_options.allowed_forgers,
        "BLOCK_RATE": sc_node_config.block_rate,
        "LATENCY_SETTINGS": sc_node_config.latency_settings.to_config()
    }
    config = config.replace("'", "")
    config = config.replace("NEW_LINE", "\n")
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
def initialize_default_sc_datadir(dirname, n, api_key):
    apiAddress = "127.0.0.1"
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)

    ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
    if not os.path.isdir(ps_keys_dir):
        os.makedirs(ps_keys_dir)
    cert_keys_paths = cert_proof_keys_paths(ps_keys_dir, cert_threshold_sig_max_keys=7, isCSWEnabled=False)
    csw_keys_paths = csw_proof_keys_paths(ps_keys_dir,
                                          LARGE_WITHDRAWAL_EPOCH_LENGTH)  # withdrawal epoch length taken from the config file.

    with open('./resources/template_predefined_genesis.conf', 'r') as templateFile:
        tmpConfig = templateFile.read()
    api_key_hash = ""
    if api_key != "":
        api_key_hash = calculateApiKeyHash(api_key)
    config = tmpConfig % {
        'NODE_NUMBER': n,
        'DIRECTORY': dirname,
        'WALLET_SEED': "sidechain_seed_{0}".format(n),
        'API_ADDRESS': "127.0.0.1",
        'API_PORT': str(apiPort),
        'API_KEY_HASH': api_key_hash,
        'API_TIMEOUT': "5s",
        'BIND_PORT': str(bindPort),
        'MAX_CONNECTIONS': 100,
        'OFFLINE_GENERATION': "false",
        "SUBMITTER_CERTIFICATE": "false",
        "CERTIFICATE_SIGNING": "false",
        "CERT_PROVING_KEY_PATH": cert_keys_paths.proving_key_path,
        "CERT_VERIFICATION_KEY_PATH": cert_keys_paths.verification_key_path,
        "CSW_PROVING_KEY_PATH": csw_keys_paths.proving_key_path,
        "CSW_VERIFICATION_KEY_PATH": csw_keys_paths.verification_key_path,
        "RESTRICT_FORGERS": "false",
        "ALLOWED_FORGERS_LIST": [],
        "BLOCK_RATE": 120,
        "LATENCY_SETTINGS": SCNodeConfiguration().latency_settings.default_string()
    }

    configsData.append({
        "name": "node" + str(n),
        "url": "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node" + str(n) + ".conf"), 'w+') as configFile:
        configFile.write(config)

    return configsData


def initialize_default_sc_chain_clean(test_dir, num_nodes, api_key=""):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        initialize_default_sc_datadir(test_dir, i, api_key)


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


def start_sc_node(i, dirname, extra_args=None, rpchost=None, timewait=None, binary=None, print_output_to_file=False,
                  auth_api_key=None, use_multiprocessing=False, processor=None):
    if use_multiprocessing:
        p = psutil.Process()
        print(f"Process #{processor}: {p}, affinity {p.cpu_affinity()}", flush=True)
        p.cpu_affinity([processor])
        print(f"Process #{processor}: Set Node{i} affinity to {processor}, Node{i} affinity now {p.cpu_affinity()}", flush=True)
    """
    Start a SC node and returns API connection to it
    """
    # Will we have  extra args for SC too ?
    datadir = os.path.join(dirname, "sc_node" + str(i))
    lib_separator = ":"
    if sys.platform.startswith('win'):
        lib_separator = ";"

    if binary is None:
        binary = "../examples/simpleapp/target/sidechains-sdk-simpleapp-0.5.0-SNAPSHOT.jar" + lib_separator + "../examples/simpleapp/target/lib/* com.horizen.examples.SimpleApp"
    #        else if platform.system() == 'Linux':
    '''
    In order to effectively attach a debugger (e.g IntelliJ) to the simpleapp, it is necessary to start the process
    enabling the debug agent which will act as a server listening on the specified port.
    '''
    dbg_agent_opt = ''
    if (extra_args is not None) and ("-agentlib" in extra_args):
        dbg_agent_opt = ' -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005'

    cfgFileName = datadir + ('/node%s.conf' % i)
    '''
    Some tools and libraries use reflection to access parts of the JDK that are meant for internal use only.
    This illegal reflective access will be disabled in a future release of the JDK.
    Currently, it is permitted by default and a warning is issued.
    The --add-opens VM option remove this warning.
    '''
    bashcmd = 'java --add-opens java.base/java.lang=ALL-UNNAMED ' + dbg_agent_opt + ' -cp ' + binary + " " + cfgFileName
    if print_output_to_file:
        with open(datadir + "/log_out.txt", "wb") as out, open(datadir + "/log_err.txt", "wb") as err:
            sidechainclient_processes[i] = subprocess.Popen(bashcmd.split(), stdout=out, stderr=err)
    else:
        sidechainclient_processes[i] = subprocess.Popen(bashcmd.split())

    url = "http://rt:rt@%s:%d" % ('127.0.0.1' or rpchost, sc_rpc_port(i))
    proxy = SidechainAuthServiceProxy(url, auth_api_key=auth_api_key)
    proxy.url = url  # store URL on proxy for info
    proxy.dataDir = datadir  # store the name of the datadir
    return proxy


def start_sc_nodes_with_multiprocessing(num_nodes, dirname, extra_args=None, rpchost=None, binary=None, print_output_to_file=False,
                   auth_api_key=DEFAULT_API_KEY):
    """
    Start multiple SC clients, return connections to them
    """
    if extra_args is None: extra_args = [None for i in range(num_nodes)]
    if binary is None: binary = [None for i in range(num_nodes)]
    i = 0
    args = []

    with multiprocessing.Pool() as pool:
        # noinspection PyProtectedMember
        workers: int = pool._processes
        print(f"Running pool with {workers} workers")

        while i < num_nodes:
            args.append((i, dirname, extra_args[i], rpchost, None, binary[i], print_output_to_file,auth_api_key, True, i+2))
            i += 1

        nodes = pool.starmap(start_sc_node, args)
        print("...MULTIPROCESSING NODES...")
    wait_for_sc_node_initialization(nodes)
    pprint.pprint(nodes)
    return nodes


def start_sc_nodes(num_nodes, dirname, extra_args=None, rpchost=None, binary=None, print_output_to_file=False,
                   auth_api_key=DEFAULT_API_KEY):
    """
    Start multiple SC clients, return connections to them
    """
    if extra_args is None: extra_args = [None for i in range(num_nodes)]
    if binary is None: binary = [None for i in range(num_nodes)]
    nodes = [
        start_sc_node(i, dirname, extra_args[i], rpchost, binary=binary[i], print_output_to_file=print_output_to_file,
                      auth_api_key=auth_api_key)
        for i in range(num_nodes)]
    wait_for_sc_node_initialization(nodes)
    return nodes


def check_sc_node(i):
    '''
    Check subprocess return code.
    '''
    sidechainclient_processes[i].poll()
    return sidechainclient_processes[i].returncode


def stop_sc_node(node, i):
    node.node_stop()
    if i in sidechainclient_processes:
        sc_proc = sidechainclient_processes[i]
        sc_proc.wait()
        del sidechainclient_processes[i]


def stop_sc_nodes(nodes):
    global sidechainclient_processes
    for idx in range(0, len(nodes)):
        if idx in sidechainclient_processes:
            stop_sc_node(nodes[idx], idx)
    del nodes[:]


def set_sc_node_times(nodes, t):
    pass


def wait_sidechainclients():
    # Wait for all the processes to cleanly exit
    for sidechainclient in sidechainclient_processes.values():
        sidechainclient.wait()
    sidechainclient_processes.clear()


def get_sc_node_pids():
    return [process.pid for process in sidechainclient_processes.values()]


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


def disconnect_sc_nodes(from_connection, node_num):
    """
    Disconnect a SC node, from_connection, to another one, specifying its node_num.
    """
    j = {"host": "127.0.0.1", \
         "port": str(sc_p2p_port(node_num))}
    ip_port = "\"127.0.0.1:" + str(sc_p2p_port(node_num)) + "\""
    print("Disconnecting from " + ip_port)
    from_connection.node_disconnect(json.dumps(j))


def sc_connected_peers(node):
    return node.node_connectedPeers()["result"]["peers"]


def disconnect_sc_nodes_bi(nodes, a, b):
    disconnect_sc_nodes(nodes[a], b)
    disconnect_sc_nodes(nodes[b], a)
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
Verify the wallet coins balance is equal to an expected coins balance.
Note: core coins boxes are: ZenBox and ForgerBox

Parameters:
 - sc_node: a sidechain node
 - expected_wallet_balance
"""


def check_wallet_coins_balance(sc_node, expected_wallet_balance):
    response = sc_node.wallet_coinsBalance()
    balance = response["result"]
    assert_equal(expected_wallet_balance * 100000000, int(balance["balance"]), "Unexpected coins balance")


"""
For a given Account verify the number of related boxes by type name (class name) and verify the sum of their balances.

Parameters:
 - sc_node: a sidechain node
 - account: an instance of Account (see sc_bootstrap_info.py)
 - box_class_name: class name of the box or None if want to check all boxes for given account
 - expected_boxes_count: the number of expected boxes for that account
 - expected_balance: expected balance for that account
"""


def check_box_balance(sc_node, account, box_class_name, expected_boxes_count, expected_balance):
    response = sc_node.wallet_allBoxes()
    boxes = response["result"]["boxes"]
    boxes_balance = 0
    boxes_count = 0
    pub_key = account.publicKey
    for box in boxes:
        if box["proposition"]["publicKey"] == pub_key and (box_class_name is None or box["typeName"] == box_class_name):
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
            "withdrawal_epoch_length": 100
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

#If we want to use a different block rate (not 120s) override this block_timestamp_rewind field.
def bootstrap_sidechain_nodes(options, network=SCNetworkConfiguration,
                              block_timestamp_rewind=DefaultBlockTimestampRewind):
    log_info = LogInfo(options.logfilelevel, options.logconsolelevel)
    print(options)
    total_number_of_sidechain_nodes = len(network.sc_nodes_configuration)
    sc_creation_info = network.sc_creation_info
    ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
    if not os.path.isdir(ps_keys_dir):
        os.makedirs(ps_keys_dir)
    cert_keys_paths = cert_proof_keys_paths(ps_keys_dir, sc_creation_info.cert_max_keys, sc_creation_info.csw_enabled)
    if sc_creation_info.csw_enabled:
        csw_keys_paths = csw_proof_keys_paths(ps_keys_dir, sc_creation_info.withdrawal_epoch_length)
    else:
        csw_keys_paths = None

    sc_nodes_bootstrap_info = create_sidechain(sc_creation_info,
                                               block_timestamp_rewind,
                                               cert_keys_paths,
                                               csw_keys_paths)
    sc_nodes_bootstrap_info_empty_account = SCBootstrapInfo(sc_nodes_bootstrap_info.sidechain_id,
                                                            None,
                                                            sc_nodes_bootstrap_info.genesis_account_balance,
                                                            sc_nodes_bootstrap_info.mainchain_block_height,
                                                            sc_nodes_bootstrap_info.sidechain_genesis_block_hex,
                                                            sc_nodes_bootstrap_info.pow_data,
                                                            sc_nodes_bootstrap_info.network,
                                                            sc_nodes_bootstrap_info.withdrawal_epoch_length,
                                                            sc_nodes_bootstrap_info.genesis_vrf_account,
                                                            sc_nodes_bootstrap_info.certificate_proof_info,
                                                            sc_nodes_bootstrap_info.initial_cumulative_comm_tree_hash,
                                                            cert_keys_paths,
                                                            csw_keys_paths)
    for i in range(total_number_of_sidechain_nodes):
        sc_node_conf = network.sc_nodes_configuration[i]
        if i == 0:
            bootstrap_sidechain_node(options.tmpdir, i, sc_nodes_bootstrap_info, sc_node_conf, log_info,
                                     options.restapitimeout)
        else:
            bootstrap_sidechain_node(options.tmpdir, i, sc_nodes_bootstrap_info_empty_account, sc_node_conf, log_info,
                                     options.restapitimeout)
    return sc_nodes_bootstrap_info


def cert_proof_keys_paths(dirname, cert_threshold_sig_max_keys=7, isCSWEnabled=False):
    # use replace for Windows OS to be able to parse the path to the keys in the config file
    pk = "cert_marlin_snark_pk"
    vk = "cert_marlin_snark_vk"
    if isCSWEnabled is False:
        pk = "cert_marlin_snark_pk_csw_disabled"
        vk = "cert_marlin_snark_vk_csw_disabled"
    return ProofKeysPaths(
        os.path.join(dirname, pk + str(cert_threshold_sig_max_keys)).replace("\\", "/"),
        os.path.join(dirname, vk + str(cert_threshold_sig_max_keys)).replace("\\", "/"))


def csw_proof_keys_paths(dirname, withdrawal_epoch_length):
    # use replace for Windows OS to be able to parse the path to the keys in the config file
    return ProofKeysPaths(
        os.path.join(dirname, "csw_marlin_snark_pk_" + str(withdrawal_epoch_length)).replace("\\", "/"),
        os.path.join(dirname, "csw_marlin_snark_vk_" + str(withdrawal_epoch_length)).replace("\\", "/"))


"""
Create a sidechain transaction inside a mainchain node.

Parameters:
 - sc_creation_info: an instance of SCCreationInfo (see sc_boostrap_info.py)
 
 Output:
  - an instance of SCBootstrapInfo (see sc_boostrap_info.py)
"""


def create_sidechain(sc_creation_info, block_timestamp_rewind, cert_keys_paths, csw_keys_paths):
    accounts = generate_secrets("seed", 1)
    vrf_keys = generate_vrf_secrets("seed", 1)
    genesis_account = accounts[0]
    vrf_key = vrf_keys[0]
    certificate_proof_info = generate_certificate_proof_info("seed", sc_creation_info.cert_max_keys,
                                                             sc_creation_info.cert_sig_threshold, cert_keys_paths,
                                                             sc_creation_info.csw_enabled)
    if csw_keys_paths is None:
        csw_verification_key = ""
    else:
        csw_verification_key = generate_csw_proof_info(sc_creation_info.withdrawal_epoch_length, csw_keys_paths)

    genesis_info = initialize_new_sidechain_in_mainchain(
        sc_creation_info.mc_node,
        sc_creation_info.withdrawal_epoch_length,
        genesis_account.publicKey,
        sc_creation_info.forward_amount,
        vrf_key.publicKey,
        certificate_proof_info.genSysConstant,
        certificate_proof_info.verificationKey,
        csw_verification_key,
        sc_creation_info.btr_data_length,
        sc_creation_info.sc_creation_version,
        sc_creation_info.csw_enabled)

    genesis_data = generate_genesis_data(genesis_info[0], genesis_account.secret, vrf_key.secret,
                                         block_timestamp_rewind)
    sidechain_id = genesis_info[2]

    return SCBootstrapInfo(sidechain_id, genesis_account, sc_creation_info.forward_amount, genesis_info[1],
                           genesis_data["scGenesisBlockHex"], genesis_data["powData"], genesis_data["mcNetwork"],
                           sc_creation_info.withdrawal_epoch_length, vrf_key, certificate_proof_info,
                           genesis_data["initialCumulativeCommTreeHash"], cert_keys_paths, csw_keys_paths)


def calculateApiKeyHash(auth_api_key):
    json_parameters = {
        "string": auth_api_key
    }
    return launch_bootstrap_tool("encodeString", json_parameters)["encodedString"]


"""
Bootstrap one sidechain node: create directory and configuration file for the node.

Parameters:
 - n: sidechain node nth: used to create directory "sc_node_n"
 - bootstrap_info: an instance of SCBootstrapInfo (see sc_boostrap_info.py)
 - sc_node_configuration: an instance of SCNodeConfiguration (see sc_boostrap_info.py)
 - log_info: optional, an instance of LogInfo with log file name and levels for the log file and console
 - rest_api_timeout: optional, SC node api timeout, 5 seconds by default.
 
"""


def bootstrap_sidechain_node(dirname, n, bootstrap_info, sc_node_configuration,
                             log_info=LogInfo(), rest_api_timeout=DEFAULT_REST_API_TIMEOUT):
    initialize_sc_datadir(dirname, n, bootstrap_info, sc_node_configuration, log_info, rest_api_timeout)


def generate_forging_request(epoch, slot):
    return json.dumps({"epochNumber": epoch, "slotNumber": slot})


def get_next_epoch_slot(epoch, slot, slots_in_epoch, force_switch_to_next_epoch=False):
    next_slot = slot + 1
    next_epoch = epoch

    if next_slot > slots_in_epoch or force_switch_to_next_epoch:
        next_slot = 1
        next_epoch += 1
    return next_epoch, next_slot


def generate_next_block(node, node_name, force_switch_to_next_epoch=False, verbose=True):
    forging_info = node.block_forgingInfo()["result"]
    slots_in_epoch = forging_info["consensusSlotsInEpoch"]
    best_slot = forging_info["bestSlotNumber"]
    best_epoch = forging_info["bestEpochNumber"]

    next_epoch, next_slot = get_next_epoch_slot(best_epoch, best_slot, slots_in_epoch, force_switch_to_next_epoch)

    forge_result = node.block_generate(generate_forging_request(next_epoch, next_slot))

    # "while" will break if whole epoch no generated block, due changed error code
    while "error" in forge_result and forge_result["error"]["code"] == "0105":
        if ("no forging stake" in forge_result["error"]["description"]):
            raise AssertionError("No forging stake for the epoch")
        print("Skip block generation for epoch {epochNumber} slot {slotNumber}".format(epochNumber=next_epoch,
                                                                                       slotNumber=next_slot))
        next_epoch, next_slot = get_next_epoch_slot(next_epoch, next_slot, slots_in_epoch)
        forge_result = node.block_generate(generate_forging_request(next_epoch, next_slot))

    assert_true("result" in forge_result, "Error during block generation for SC {0}".format(node_name))
    block_id = forge_result["result"]["blockId"]
    if verbose == True:
        print("Successfully forged block with id {blockId}".format(blockId=block_id))
    return forge_result["result"]["blockId"]


def generate_next_blocks(node, node_name, blocks_count, verbose=True):
    blocks_ids = []
    for i in range(blocks_count):
        blocks_ids.append(generate_next_block(node, node_name, force_switch_to_next_epoch=False, verbose=verbose))
    return blocks_ids


# Check if the CSW proofs for the required boxes were finished (or absent if was not able to create a proof)
def if_csws_were_generated(sc_node, csw_box_ids, allow_absent=False):
    for box_id in csw_box_ids:
        req = json.dumps({"boxId": box_id})
        status = sc_node.csw_cswInfo(req)["result"]["cswInfo"]["proofInfo"]["status"]
        if status == "Absent" and allow_absent:
            continue
        elif status != "Generated":
            return False
    return True


def get_scinfo_data(scid, mc_node):
    ret = mc_node.getscinfo(scid)['items'][0]
    sc_creating_height = ret['createdAtBlockHeight']
    sc_version = ret['version']
    sc_constant = ret['constant']
    sc_customData = ret['customData']
    epochLen = ret['withdrawalEpochLength']
    current_height = mc_node.getblockcount()
    epoch_number = (current_height - sc_creating_height + 1) // epochLen - 1
    end_epoch_block_hash = mc_node.getblockhash(sc_creating_height - 1 + ((epoch_number + 1) * epochLen))
    epoch_cum_tree_hash = mc_node.getblock(end_epoch_block_hash)['scCumTreeHash']
    return epoch_number, epoch_cum_tree_hash, sc_version, sc_constant, sc_customData


def create_alien_sidechain(mcTest, mc_node, scVersion, epochLength, customHexTag, feCfgList=[]):
    sc_tag = customHexTag
    vk = mcTest.generate_params(sc_tag)
    constant = generate_random_field_element_hex()
    # we use this field to store sc_tag, used by mcTool when creating certificates
    customData = sc_tag
    cswVk = ""
    cmtCfg = []

    cmdInput = {
        "version": scVersion,
        "withdrawalEpochLength": epochLength,
        "toaddress": "dada",
        "amount": 0.1,
        "wCertVk": vk,
        "constant": constant,
        'customData': customData,
        'wCeasedVk': cswVk,
        'vFieldElementCertificateFieldConfig': feCfgList,
        'vBitVectorCertificateFieldConfig': cmtCfg
    }
    try:
        ret = mc_node.sc_create(cmdInput)
    except Exception as e:
        print(e)
        assert_true(False)
    # self.sync_all()
    # time.sleep(1) # if we have one node the sync_all won't sleep
    assert_equal(True, ret['txid'] in mc_node.getrawmempool())

    return ret


def create_certificate_for_alien_sc(mcTest, scid, mc_node, fePatternArray):
    epoch_number_1, epoch_cum_tree_hash_1, sc_version, constant, sc_tag = get_scinfo_data(scid, mc_node)

    print("sc_tag[{}]".format(sc_tag))
    print("constant={}".format(constant))
    print("sc_version={}".format(sc_version))

    vCfe = fePatternArray
    vCmt = []

    MBTR_SC_FEE = 0.0
    FT_SC_FEE = 0.0
    CERT_FEE = Decimal('0.0001')

    # get a UTXO
    utx, change = get_spendable(mc_node, CERT_FEE)

    inputs = [{'txid': utx['txid'], 'vout': utx['vout']}]
    outputs = {mc_node.getnewaddress(): change}

    # serialized fe for the proof has 32 byte size
    feList = []
    for pattern in fePatternArray:
        feList.append(get_field_element_with_padding(pattern, sc_version))
    scid_swapped = str(swap_bytes(scid))

    scProof = mcTest.create_test_proof(
        sc_tag, scid_swapped, epoch_number_1, quality=10,
        btr_fee=MBTR_SC_FEE, ft_min_amount=FT_SC_FEE,
        end_cum_comm_tree_root=epoch_cum_tree_hash_1, constant=constant,
        pks=[], amounts=[], custom_fields=feList)

    print("cum =", epoch_cum_tree_hash_1)
    params = {
        'scid': scid,
        'quality': 10,
        'endEpochCumScTxCommTreeRoot': epoch_cum_tree_hash_1,
        'scProof': scProof,
        'withdrawalEpochNumber': epoch_number_1,
        'vFieldElementCertificateField': vCfe,
        'vBitVectorCertificateField': vCmt
    }

    try:
        rawcert = mc_node.createrawcertificate(inputs, outputs, [], params)
        signed_cert = mc_node.signrawtransaction(rawcert)
        cert = mc_node.sendrawtransaction(signed_cert['hex'])
    except Exception as e:
        print("Send certificate failed with reason {}".format(e))
        assert (False)

    assert_equal(True, cert in mc_node.getrawmempool())
    return cert


def deserialize_perf_test_json(json_file):
    with open(json_file) as f:
        json_data = json.load(f)
    with open('./performance/perf_test_schema.json', 'r') as schema_file:
        schema = json.load(schema_file)
    try:
        validate(instance=json_data, schema=schema)
    except jsonschema.exceptions.ValidationError as error:
        logging.error("Failed to validate perf_test.json {}".format(error))
        raise error

    return json_data
