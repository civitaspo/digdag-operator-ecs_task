package pro.civitaspo.digdag.plugin.ecs_task.command
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.util.Workspace
import org.apache.commons.io.FileUtils
import org.slf4j.Logger
import pro.civitaspo.digdag.plugin.ecs_task.aws.Aws
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.collection.JavaConverters._
import scala.util.Random

case class S3TmpStorage(shellCommand: String, location: AmazonS3URI, aws: Aws, workspace: Workspace, logger: Logger) extends TmpStorage {

  private lazy val tmpDir: Path = createTmpDir()

  private def createTmpDir(): Path = {
    val dir = workspace.getProjectPath.resolve(".digdag/tmp")
    Files.createDirectories(dir)
    val random: String = Random.alphanumeric.take(10).mkString
    Files.createTempDirectory(dir, random)
  }

  private def writeFile(file: Path, content: String): Unit = {
    logger.info(s"Write into ${file.toString}")
    TryWithResource(workspace.newBufferedWriter(file.toString, UTF_8)) { writer =>
      writer.write(content)
    }
  }

  override def getLocation: String = location.toString

  override def stageFile(fileName: String, content: String): Unit = {
    val file = Files.createFile(tmpDir.resolve(fileName))
    writeFile(file, content)
  }

  override def stageWorkspace(): Unit = {
    val targets: Iterator[Path] = Files.list(workspace.getPath).iterator().asScala.filterNot(_.endsWith(".digdag"))
    val workspacePath: Path = Files.createDirectory(tmpDir.resolve("workspace"))
    targets.foreach { path =>
      logger.info(s"Copy: $path -> $workspacePath")
      if (Files.isDirectory(path)) FileUtils.copyDirectoryToDirectory(path.toFile, workspacePath.toFile)
      else FileUtils.copyFileToDirectory(path.toFile, workspacePath.toFile)
    }
  }

  override def buildTaskCommand(mainScript: String): Seq[String] = {
    Seq(shellCommand, "-c", s"aws s3 cp ${location.toString}/$mainScript ./ && $shellCommand $mainScript")
  }

  override def storeStagedFiles(): Unit = {
    logger.info(s"Recursive Upload: $tmpDir -> ${location.getURI}")
    aws.withTransferManager { xfer =>
      val upload = xfer.uploadDirectory(
        location.getBucket,
        location.getKey,
        tmpDir.toFile,
        true // includeSubdirectories
      )
      upload.waitForCompletion()
    }
  }

  override def close(): Unit = {
    logger.info(s"Remove: $tmpDir")
    FileUtils.deleteDirectory(tmpDir.toFile)
  }
}
