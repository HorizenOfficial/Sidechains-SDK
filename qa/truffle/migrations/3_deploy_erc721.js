const TestERC721 = artifacts.require("TestERC721");

module.exports = function(deployer) {
  deployer.deploy(TestERC721, "was 1 token", "W1T", "metadataURI");
};
