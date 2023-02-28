package io.horizen.account.state

import com.horizen.state.BaseStateReader

trait BaseStateReaderProvider {
  def getBaseStateReader(): BaseStateReader
}
