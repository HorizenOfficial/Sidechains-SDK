package com.horizen.account.event

import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.{Address, Utf8String}

import scala.annotation.meta.getter

case class CaseClassTestEvent1(@(Parameter@getter)(1) @(Indexed@getter) address: Address, @(Parameter@getter)(2) value: Uint256)

case class CaseClassTestEvent2(@(Parameter@getter)(1) address: Address, @(Parameter@getter)(2) @(Indexed@getter) value: Uint256)

class ScalaClassTestEvent1(@(Parameter@getter)(1) @(Indexed@getter) val address: Address, @(Parameter@getter)(2) val value: Uint256) {}

class ScalaClassTestEvent2(var address: Address, var value: Uint256) {}

class ScalaClassTestEvent3(@(Parameter@getter)(1) @(Indexed@getter) val address: Double, @(Parameter@getter)(2) val value: Uint256) {}

class ScalaClassTestEvent4(@(Parameter@getter)(1) @(Indexed@getter) val address: Utf8String, @(Parameter@getter)(2) val value: Uint256) {}

class ScalaClassTestEvent5(@(Parameter@getter)(1) @(Indexed@getter) val address: Address, @(Parameter@getter)(1) val value: Uint256) {}

class ScalaClassTestEvent6() {}

@Anonymous class ScalaClassTestEvent7() {}

class ScalaClassTestEvent8(@Parameter(1) @Indexed val address: Address, @Parameter(2) val value: Uint256) {}
