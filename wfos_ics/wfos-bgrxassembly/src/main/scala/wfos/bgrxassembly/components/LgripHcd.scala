package wfos.bgrxassembly.components

import csw.params.commands.{CommandIssue, Setup}
import csw.params.commands.CommandIssue.{ParameterValueOutOfRangeIssue, MissingKeyIssue}
import csw.params.core.generics.{Parameter}
import wfos.bgrxassembly.config.LgripInfo

class LgripHcd extends Hcd {
  override def validateParameters(setup: Setup): Either[CommandIssue, Parameter[Int]] = {
    val issueOraccepted = for {
      targetPosition <- setup.get(LgripInfo.targetPositionKey).toRight(MissingKeyIssue("Target Position key not found"))
      _              <- inRange(targetPosition, LgripInfo.minTargetPosition, LgripInfo.maxTargetPosition)
    } yield targetPosition
    issueOraccepted
  }

  override def inRange(parameter: Parameter[Int], minVal: Parameter[Int], maxVal: Parameter[Int]): Either[CommandIssue, Parameter[Int]] = {
    if (parameter.head >= minVal.head & parameter.head <= maxVal.head) Right(parameter)
    else Left(ParameterValueOutOfRangeIssue(s"${parameter.keyName} should be in range of $minVal and $maxVal"))
  }
}
