package wfos.bgrxassembly.components

import csw.params.commands.{CommandIssue, Setup}
import csw.params.commands.CommandIssue.{ParameterValueOutOfRangeIssue, MissingKeyIssue}
import csw.params.core.generics.{Parameter}
import wfos.rgriphcd.RgripInfo

class RgripHcd extends Hcd {
  override def validateParameters(setup: Setup): Either[CommandIssue, Parameter[Int]] = {
    val issueOraccepted = for {
      bgid        <- setup.get(RgripInfo.gratingModeKey).toRight(MissingKeyIssue("bgid not found"))
      targetAngle <- setup.get(RgripInfo.targetAngleKey).toRight(MissingKeyIssue("targetAngle not found"))
      cw          <- setup.get(RgripInfo.cwKey).toRight(MissingKeyIssue("CW not found"))
      // _           <- setup.get(GratingModeKey).toRight(CommandIssue.WrongParameterTypeIssue("GratingMode not found"))
      _     <- inRange(cw, RgripInfo.minCW, RgripInfo.maxCW)
      _     <- inRange(targetAngle, RgripInfo.minTargetAngle, RgripInfo.maxTargetAngle)
      param <- validateGratingMode(bgid, targetAngle)
    } yield param
    issueOraccepted
  }

  override def inRange(parameter: Parameter[Int], minVal: Parameter[Int], maxVal: Parameter[Int]) = {
    if (parameter.head >= minVal.head & parameter.head <= maxVal.head) Right(parameter)
    else Left(ParameterValueOutOfRangeIssue(s"${parameter.keyName} should be in range of $minVal and $maxVal"))
  }

  private def validateGratingMode(bgid: Parameter[String], targetAngle: Parameter[Int]) = {
    bgid.head match {
      case "bgid1" =>
        if (targetAngle.values.head == 0) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid2" =>
        if (targetAngle.values.head == 15) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid3" =>
        if (targetAngle.values.head >= 25 & targetAngle.values.head <= 35) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid4" =>
        if (targetAngle.values.head == 45) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case "bgid5" =>
        if (targetAngle.values.head == 45) Right(targetAngle)
        else Left(ParameterValueOutOfRangeIssue(s"TargetAngle should be in the given grating mode's range"))
      case _ => Left(CommandIssue.WrongParameterTypeIssue(s"Wrong Grating Mode"))
    }
  }
}
