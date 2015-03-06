package sbt.codedeploy

import java.io.File

import sbt.Path
import sbt.Path._

case class ScriptMapping(
  file: File,
  section: String,
  timeout: Int,
  runas: String
) {
  import ScriptMapping._

  require(
    ValidSections.contains(section),
    s"Section must be one of ${ValidSections.mkString("[", ", ", "]")}, not ${section}"
  )

  def location: String = s"${ScriptsPrefix}/${section}/${file.getName}"
}

object ScriptMapping {
  private[codedeploy] val ValidSections = Array(
    "ApplicationStart",
    "ApplicationStop",
    "ValidateService"
  )

  private[codedeploy] val ScriptsPrefix = "scripts"

  private[codedeploy] def defaultMappings(sourceDirectory: File) = {
    val scripts = sourceDirectory / "scripts"
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
            timeout = 300,
            runas = "root"
          )
      }
    }
  }
}
