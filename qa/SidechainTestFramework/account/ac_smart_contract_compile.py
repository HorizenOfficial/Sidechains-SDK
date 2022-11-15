import os
import subprocess
import logging

from SidechainTestFramework.account.ac_utils import install_npm_packages, get_cwd

sigHashesGenerated = False


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


if __name__ == '__main__':
    prepare_resources()
