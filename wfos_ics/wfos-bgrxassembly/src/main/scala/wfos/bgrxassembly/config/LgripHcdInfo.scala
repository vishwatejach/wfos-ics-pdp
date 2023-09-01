package wfos.bgrxassembly.config

// import csw.params.commands.CommandName
import csw.params.core.generics.{Key, KeyType, Parameter}
import csw.params.core.models.{ObsId}

object LgripInfo {
  // rgripHcd configurations
  val exchangePositionKey: Key[Int]    = KeyType.IntKey.make("exchangePosition")
  val exchangePosition: Parameter[Int] = exchangePositionKey.set(100)

  val homePositionKey: Key[Int]    = KeyType.IntKey.make("homePosition")
  val homePosition: Parameter[Int] = homePositionKey.set(0)

//   val currentPositionKey: Key[Int]    = KeyType.IntKey.make("currentPosition")
//   var currentPosition: Parameter[Int] = currentPositionKey.set(35)

  val targetPositionKey: Key[Int] = KeyType.IntKey.make("targetPosition")
  val gratingModeKey: Key[String] = KeyType.StringKey.make("gratingMode")
  val cwKey: Key[Int]             = KeyType.IntKey.make("cw")

  // ranges of targetPosition
  val minTargetPositionKey: Key[Int]    = KeyType.IntKey.make("minTargetPosition")
  val minTargetPosition: Parameter[Int] = minTargetPositionKey.set(0)

  val maxTargetPositionKey: Key[Int]    = KeyType.IntKey.make("maxTargetPosition")
  val maxTargetPosition: Parameter[Int] = maxTargetPositionKey.set(100)

  val obsId: ObsId = ObsId("2023A-001-123")
}
