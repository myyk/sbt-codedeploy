package sbt.codedeploy

import scala.collection.mutable.ArrayBuffer

import java.io.File

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
  private[codedeploy] val ContentPrefix = "content"

  private[codedeploy] def defaultMappings(
    sourceDirectory: File,
    jars: Seq[File]
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
          source = (new File(ContentPrefix) / path).getPath,
          destination = new File(path).getParent match {
            case null => "." // TODO not sure if this works...
            case x => x
          }
        )
      }
    }

    jars.foreach { file =>
      mappings += new ContentMapping(
        file = file,
        source = (new File(ContentPrefix) / "lib" / file.getName).getPath,
        destination = "lib"
      )
    }

    mappings
  }
}
