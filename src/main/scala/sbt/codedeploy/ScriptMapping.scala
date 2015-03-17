package sbt.codedeploy

import java.io.File

import sbt.Path
import sbt.Path._

case class ScriptMapping(
  file: File,
  section: String,
  location: String,
  timeout: Int,
  runas: String
) {
  import ScriptMapping._

  require(
    ValidSections.contains(section),
    s"Section must be one of ${ValidSections.mkString("[", ", ", "]")}, not ${section}"
  )
}

object ScriptMapping {
  private[codedeploy] val ValidSections = Array(
    "AfterInstall",
    "ApplicationStart",
    "ApplicationStop",
    "BeforeInstall",
    "ValidateService"
  )

  private[codedeploy] def defaultMappings(sourceDirectory: File) = {
    val scripts = sourceDirectory / "scripts"
    generateMappingsInDirectory(scripts)
  }

  def generateMappingsInDirectory(scripts: File) = {
    val relativize = Path.relativeTo(scripts)
    (scripts ***).get.filter(_.isFile).map { file =>
      relativize(file) match {
        case None =>
          sys.error(s"failed to relativize ${file} under ${scripts}")
        case Some(path) =>
          val section = path.split(Path.sep).head
          new ScriptMapping(
            file = file,
            section = section,
            location = path,
            timeout = 300,
            runas = "root"
          )
      }
    }
  }
}
