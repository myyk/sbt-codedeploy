package sbt.codedeploy

import java.io.File

case class PermissionMapping(
  objectPath: String,
  mode: String,
  owner: String,
  group: String,
  objectType: Option[String] = None
)

object PermissionMapping {
  def defaultMappings(sourceDirectory: File) = {
    Nil
  }
}
