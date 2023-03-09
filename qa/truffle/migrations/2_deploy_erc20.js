const TestERC20 = artifacts.require("TestERC20");

module.exports = function(deployer) {
  deployer.deploy(TestERC20);
};
