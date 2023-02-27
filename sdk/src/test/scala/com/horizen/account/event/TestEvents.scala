package com.horizen.account.event

import com.horizen.account.state.events.annotation.{Anonymous, Indexed, Parameter}
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.{Utf8String, Address => AbiAddress}

import scala.annotation.meta.getter

case class CaseClassTestEvent1(
    @(Parameter @getter)(1) @(Indexed @getter) address: AbiAddress,
    @(Parameter @getter)(2) value: Uint256
)

case class CaseClassTestEvent2(
    @(Parameter @getter)(1) address: AbiAddress,
    @(Parameter @getter)(2) @(Indexed @getter) value: Uint256
)

class ScalaClassTestEvent1(
    @(Parameter @getter)(1) @(Indexed @getter) val address: AbiAddress,
    @(Parameter @getter)(2) val value: Uint256
) {}

class ScalaClassTestEvent2(
    var address: AbiAddress,
    var value: Uint256
) {}

class ScalaClassTestEvent3(
    @(Parameter @getter)(1) @(Indexed @getter) val address: Double,
    @(Parameter @getter)(2) val value: Uint256
) {}

class ScalaClassTestEvent4(
    @(Parameter @getter)(1) @(Indexed @getter) val address: Utf8String,
    @(Parameter @getter)(2) val value: Uint256
) {}

class ScalaClassTestEvent5(
    @(Parameter @getter)(1) @(Indexed @getter) val address: AbiAddress,
    @(Parameter @getter)(1) val value: Uint256
) {}

class ScalaClassTestEvent6() {}

@Anonymous class ScalaClassTestEvent7() {}

class ScalaClassTestEvent8(
    @Parameter(1) @Indexed val address: AbiAddress,
    @Parameter(2) val value: Uint256
) {}
