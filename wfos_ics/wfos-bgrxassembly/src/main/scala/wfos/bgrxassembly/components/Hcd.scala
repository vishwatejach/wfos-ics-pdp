package wfos.bgrxassembly.components

import csw.params.commands.{ControlCommand, CommandIssue, Setup}
import csw.params.core.generics.{Parameter}

trait Hcd {
  def validateParameters(setup: Setup): Either[CommandIssue, Parameter[Int]]
  def inRange(parameter: Parameter[Int], minVal: Parameter[Int], maxVal: Parameter[Int]): Either[CommandIssue, Parameter[Int]]
}
