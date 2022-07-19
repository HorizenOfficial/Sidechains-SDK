package com.horizen.account.event

import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256

import scala.annotation.meta.getter

class ScalaClassTestEvent1(@(Parameter @getter)(1) @(Indexed @getter) val address: Address, @(Parameter @getter)(2) val value: Uint256){}

class ScalaClassTestEvent2(@(Parameter @getter)(1) @(Indexed @getter) address: Address, @(Parameter @getter)(2) value: Uint256){}

case class CaseClassTestEvent1(@(Parameter @getter)(1) @(Indexed @getter) address: Address, @(Parameter @getter)(2) value: Uint256)

case class CaseClassTestEvent2(@(Parameter @getter)(1) address: Address, @(Parameter @getter)(2) @(Indexed @getter) value: Uint256)


