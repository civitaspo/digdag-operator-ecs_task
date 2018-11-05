package pro.civitaspo.digdag.plugin.ecs_task.util
import java.nio.file.{Files, Path}

import io.digdag.util.Workspace
import org.apache.commons.io.FileUtils

import scala.util.Random

// ref. https://github.com/muga/digdag/blob/aff3dfab0b91aa6787d7921ce34d5b3b21947c20/digdag-plugin-utils/src/main/java/io/digdag/util/Workspace.java#L84-L95
object WorkspaceWithTempDir {

  def apply[T](workspace: Workspace)(f: Path => T): T = {
    val dir = workspace.getProjectPath.resolve(".digdag/tmp")
    Files.createDirectories(dir)
    val random: String = Random.alphanumeric.take(10).mkString
    val tempDir: Path = Files.createTempDirectory(dir, random)
    try f(tempDir)
    finally FileUtils.deleteDirectory(tempDir.toFile)
  }

}
