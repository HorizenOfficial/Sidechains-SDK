import { HardhatUserConfig, task } from "hardhat/config";
import "@nomiclabs/hardhat-ethers";
import fs from "fs";
import "dotenv/config";
task("sighashes", "Creates all the sighashes", async (taskArgs, hre) => {
  const artifactPaths = await hre.artifacts.getArtifactPaths();
  console.log(artifactPaths);
  for (const p of artifactPaths) {
    const filename = p.split("/")[p.split("/").length - 1];
    console.log("Generating sighashes for", filename + "...");
    const content = JSON.parse(fs.readFileSync(p, "utf-8"));
    const interf = new hre.ethers.utils.Interface(content.abi);
    for (const fn of interf.fragments) {
      if (fn.type === "constructor") continue;
      console.log("\tGenerating sighash for", fn.format() + "...");
      if (content.sighashes === undefined) content.sighashes = {};
      content.sighashes[fn.format()] = interf.getSighash(fn);
      fs.writeFileSync(p, JSON.stringify(content, null, 2));
    }
  }
});

const config: HardhatUserConfig = {
  solidity: {
    compilers: [{ version: "0.8.4" }],
  },
};

export default config;
