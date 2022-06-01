package wfos.bgrxassembly.events

import csw.params.core.generics.KeyType.BooleanKey
import csw.params.core.generics.{GChoiceKey, Key}
import csw.params.events.{EventKey, EventName, SystemEvent}
import csw.prefix.models.Prefix
import wfos.bgrxassembly.models.{BluegrxPosition, GripperPosition}

abstract class BgrxPositionEvent(grxPrefix: Prefix) {
  val CurrentPositionKey: GChoiceKey
  val DemandPositionKey: GChoiceKey
  val DarkKey: Key[Boolean]               = BooleanKey.make("dark")
  val GripperPositionEventName: EventName = EventName("GripperPosition")
  val GripperPositionEventKey: EventKey   = EventKey(grxPrefix, GripperPositionEventName)

  def make(current: GripperPosition, target: GripperPosition, dark: Boolean): SystemEvent =
    SystemEvent(
      grxPrefix,
      GripperPositionEventName,
      Set(
        CurrentPositionKey.set(current.entryName),
        DemandPositionKey.set(target.entryName),
        DarkKey.set(dark)
      )
    )
}

class BlueGrxPositionEvent(filterPrefix: Prefix) extends BgrxPositionEvent(filterPrefix) {
  override val CurrentPositionKey: GChoiceKey = BluegrxPosition.makeChoiceKey("current")
  override val DemandPositionKey: GChoiceKey  = BluegrxPosition.makeChoiceKey("demand")
}
