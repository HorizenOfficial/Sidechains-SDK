package io.horizen.fork

trait OptionalSidechainFork {
  def validate(forks: Seq[OptionalSidechainFork]): Unit = ()
}
