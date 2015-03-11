package sbt.codedeploy

import java.io.File

case class PermissionMapping(
  objectPath: String,
  mode: String,
  owner: String,
  group: String
)

object PermissionMapping {
  def defaultMappings(sourceDirectory: File) = {
    Nil
  }
}
