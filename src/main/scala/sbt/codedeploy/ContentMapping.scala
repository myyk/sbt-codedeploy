package sbt.codedeploy

import scala.collection.mutable.ArrayBuffer

import java.io.File

import sbt.Attributed
import sbt.AttributeKey
import sbt.Artifact
import sbt.ModuleID
import sbt.Path
import sbt.Path._

case class ContentMapping(
  file: File,
  source: String,
  destination: String
) {
  import ContentMapping._
}

object ContentMapping {
  private[codedeploy] def defaultMappings(
    name: String,
    sourceDirectory: File,
    dependencies: Seq[Attributed[File]],
    packagedArtifact: (Artifact, File),
    organization: String,
    version: String
  ) = {
    val content = sourceDirectory / "content"
    val relativize = Path.relativeTo(content)
    val mappings = ArrayBuffer.empty[ContentMapping]

    (content ***).get.filter(_.isFile).foreach { file =>
      relativize(file) match {
        case None =>
          sys.error(s"failed to relativize ${file} under ${content}")
        case Some(path) => mappings += new ContentMapping(
          file = file,
          source = path,
          destination = new File(path).getParent match {
            case null => name
            case x => x
          }
        )
      }
    }

    dependencies.foreach { entry =>
      println(entry.metadata)
      val file = entry.data
      val artifact = entry.get[Artifact](sbt.Keys.artifact.key).getOrElse {
        sys.error(s"unable to lookup artifact for ${file}")
      }
      val module = entry.get[ModuleID](sbt.Keys.moduleID.key).getOrElse {
        sys.error(s"unable to lookup module for ${file}")
      }
      mappings += new ContentMapping(
        file = file,
        source = (new File("lib") / s"${module.organization}.${module.name}-${module.revision}.${artifact.`type`}").getPath,
        destination = "lib"
      )
    }

    packagedArtifact match { case (artifact, file) =>
      mappings += new ContentMapping(
        file = file,
        source = (new File("lib") / s"${organization}.${artifact.name}-${version}.${artifact.`type`}").getPath,
        destination = "lib"
      )
    }

    mappings
  }
}
