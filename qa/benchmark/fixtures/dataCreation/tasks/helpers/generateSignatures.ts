import {HardhatRuntimeEnvironment} from "hardhat/types";
import * as fs from "fs";

interface CompiledContract {
    _format?:string,
    contractName:string,
    sourceName:string,
    abi:Array<any>,
    bytecode?:string,
    deployedBytecode?:string,
    linkReferences?:any,
    deployedLinkReferences?:any,

}

const compiledContracts: Array<CompiledContract> = [];

// Generate the signature hashes of given contracts for deployment
export default async function (hre: HardhatRuntimeEnvironment, name: string, write?: boolean): Promise<Array<CompiledContract>> {
    const artifactPaths = await hre.artifacts.getArtifactPaths();
    for (const artifact of artifactPaths) {
        if (artifact.indexOf(name) === -1) continue;

        const filename = artifact.split("/")[artifact.split("/").length - 1];
        console.log(`Generating signature hashes for ${filename} ...`);

        const contract = JSON.parse(fs.readFileSync(artifact, "utf-8"));
        const contractInterface = new hre.ethers.utils.Interface(contract.abi);

        for (const fragment of contractInterface.fragments) {
            if (fragment.type === "constructor") continue;

            console.log(`Generating signature hash for ${fragment.format()} ...`);
            if (contract.sighashes === undefined) contract.sighashes = {};
            contract.sighashes[fragment.format()] = contractInterface.getSighash(fragment);

            if (write) {
                fs.writeFileSync(artifact, JSON.stringify(contract, null, 2));
            }
        }

        compiledContracts.push(contract);
    }

    return compiledContracts;
}