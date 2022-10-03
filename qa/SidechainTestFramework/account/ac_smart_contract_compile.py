import os
import subprocess
import logging

nodeModulesInstalled = False
sigHashesGenerated = False
cwd = None


def get_cwd():
    global cwd
    if cwd is None:
        cwd = os.environ.get("SIDECHAIN_SDK") + "/qa/SidechainTestFramework/account/smart_contract_resources"
    return cwd


def install_npm_packages():
    global nodeModulesInstalled
    if not nodeModulesInstalled:
        logging.info("Installing node packages...")
        logging.info("The first time this runs on your machine, it can take a few minutes...")
        proc = subprocess.run(
            ["yarn", "install", "--quiet", "--non-interactive", "--frozen-lockfile"],
            cwd=get_cwd(),
            stdout=subprocess.PIPE)
        proc.check_returncode()
        logging.info("Done!")
        nodeModulesInstalled = True


def compile_smart_contracts():
    global sigHashesGenerated
    if not sigHashesGenerated:
        os.environ["TS_NODE_TRANSPILE_ONLY"] = '1'
        logging.info("Compiling smart contracts...")
        proc = subprocess.run(
            ["npx", "hardhat", "compile"],
            cwd=get_cwd(),
            # capture_output=True
        )
        proc.check_returncode()
        logging.info("Generating signature hashes...")
        proc = subprocess.run(
            ["npx", "hardhat", "sighashes"],
            cwd=get_cwd(),
            capture_output=True)
        proc.check_returncode()
        logging.info("Done!")
        sigHashesGenerated = True


def prepare_resources():
    install_npm_packages()
    compile_smart_contracts()


# def load_contract_abi(contractPath: str)


if __name__ == '__main__':
    prepare_resources()
