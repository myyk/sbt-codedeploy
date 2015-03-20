package sbt.codedeploy

import scala.collection.mutable.ArrayBuffer

import java.io.File

import sbt.{Attributed,Artifact,ModuleID,Path}
import sbt.Path._

case class ContentMapping(
  file: File,
  source: String,
  destination: String
)

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
    val contentRoot = new File(name) // everything gets copied beneath here
    val lib = new File(contentRoot, "lib") // all jars get copied in here

    (content ***).get.filter(_.isFile).foreach { file =>
      relativize(file) match {
        case None =>
          sys.error(s"failed to relativize ${file} under ${content}")
        case Some(path) => mappings += {
          val source = new File(contentRoot, path)
          new ContentMapping(
            file = file,
            source = source.getPath,
            destination = source.getParent
          )
        }
      }
    }

    dependencies.foreach { entry =>
      val file = entry.data
      val artifact = entry.get[Artifact](sbt.Keys.artifact.key).getOrElse {
        sys.error(s"unable to lookup artifact for ${file}")
      }
      val module = entry.get[ModuleID](sbt.Keys.moduleID.key).getOrElse {
        sys.error(s"unable to lookup module for ${file}")
      }
      mappings += new ContentMapping(
        file = file,
        source = (lib / s"${module.organization}.${module.name}-${module.revision}.${artifact.`type`}").getPath,
        destination = lib.getPath
      )
    }

    packagedArtifact match { case (artifact, file) =>
      mappings += new ContentMapping(
        file = file,
        source = (lib / s"${organization}.${artifact.name}-${version}.${artifact.`type`}").getPath,
        destination = lib.getPath
      )
    }

    mappings
  }
}
