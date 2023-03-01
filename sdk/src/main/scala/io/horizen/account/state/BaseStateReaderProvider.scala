package io.horizen.account.state

import io.horizen.state.BaseStateReader

trait BaseStateReaderProvider {
  def getBaseStateReader(): BaseStateReader
}
