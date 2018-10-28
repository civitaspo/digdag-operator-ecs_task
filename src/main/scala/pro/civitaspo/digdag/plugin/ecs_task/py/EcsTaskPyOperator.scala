package pro.civitaspo.digdag.plugin.ecs_task.py
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import org.apache.commons.io.FileUtils
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper
import pro.civitaspo.digdag.plugin.ecs_task.command.EcsTaskCommandRunner

import scala.collection.JavaConverters._
import scala.io.Source
import scala.language.reflectiveCalls

class EcsTaskPyOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  private val runnerPyResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/py/runner.py"
  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/py/run.sh"

  val command: String = params.get("_command", classOf[String])
  val workspaceS3UriPrefix: AmazonS3URI = {
    val parent: String = params.get("workspace_s3_uri_prefix", classOf[String])
    if (parent.endsWith("/")) AmazonS3UriWrapper(s"${parent}ecs_task.py.$sessionUuid")
    else AmazonS3UriWrapper(s"$parent/ecs_task.py.$sessionUuid")
  }
  val setupCommands: Seq[String] = params.getListOrEmpty("setup_commands", classOf[String]).asScala
  val image: String = params.get("image", classOf[String])

  val runner: EcsTaskCommandRunner = EcsTaskCommandRunner(params)

  override def runTask(): TaskResult = {
    withTempDir(operatorName) { tempDir: Path =>
      createInFile(tempDir)
      createRunnerPyFile(tempDir)
      createRunShFile(tempDir)
      createWorkspaceDir(tempDir)
      uploadOnS3(tempDir)
    }
    val scriptLocation: AmazonS3URI = AmazonS3UriWrapper(s"${workspaceS3UriPrefix.toString}/run.sh")
    runner.run(image, scriptLocation)
  }

  protected def createInFile(parent: Path): Unit = {
    val inContent: String = templateEngine.template(params.toString, params)
    val inFile: Path = Files.createFile(parent.resolve("in.json"))
    writeFile(file = inFile, content = inContent)
  }

  protected def createRunnerPyFile(parent: Path): Unit = {
    using(classOf[EcsTaskPyOperator].getResourceAsStream(runnerPyResourcePath)) { is =>
      val runnerPyContent: String = Source.fromInputStream(is).mkString
      val runnerPyFile: Path = Files.createFile(parent.resolve("runner.py"))
      writeFile(file = runnerPyFile, content = runnerPyContent)
    }
  }

  protected def createRunShFile(parent: Path): Unit = {
    val dup: Config = params.deepCopy()
    dup.set("ECS_TASK_PY_BUCKET", workspaceS3UriPrefix.getBucket)
    dup.set("ECS_TASK_PY_PREFIX", workspaceS3UriPrefix.getKey)
    dup.set("ECS_TASK_PY_COMMAND", command)

    dup.set("ECS_TASK_PY_SETUP_COMMANDS", "") // set a default value
    if (setupCommands.nonEmpty) {
      logger.warn("`setup_commands` option is an experimental, so please be careful in the plugin update.")
      val cmds: String = setupCommands.map(cmd => s"$cmd 2>> ../stderr.log | tee -a ../stdout.log").mkString("\n")
      dup.set("ECS_TASK_PY_SETUP_COMMANDS", cmds)
    }

    using(classOf[EcsTaskPyOperator].getResourceAsStream(runShResourcePath)) { is =>
      val runShContentTemplate: String = Source.fromInputStream(is).mkString
      val runShContent: String = templateEngine.template(runShContentTemplate, dup)
      val runShFile: Path = Files.createFile(parent.resolve("run.sh"))
      writeFile(file = runShFile, content = runShContent)
    }
  }

  protected def createWorkspaceDir(parent: Path): Unit = {
    val targets: Iterator[Path] = Files.list(workspace.getPath).iterator().asScala.filterNot(_.endsWith(".digdag"))
    val workspacePath: Path = Files.createDirectory(parent.resolve("workspace"))
    targets.foreach { path =>
      logger.info(s"Copy: $path -> $workspacePath")
      if (Files.isDirectory(path)) FileUtils.copyDirectoryToDirectory(path.toFile, workspacePath.toFile)
      else FileUtils.copyFileToDirectory(path.toFile, workspacePath.toFile)
    }
  }

  protected def uploadOnS3(path: Path): Unit = {
    logger.info(s"Recursive Upload: $path -> ${workspaceS3UriPrefix.getURI}")
    aws.withTransferManager { xfer =>
      val upload = xfer.uploadDirectory(
        workspaceS3UriPrefix.getBucket,
        workspaceS3UriPrefix.getKey,
        path.toFile,
        true // includeSubdirectories
      )
      upload.waitForCompletion()
    }
  }

  protected def writeFile(file: Path, content: String): Unit = {
    logger.info(s"Write into ${file.toString}")
    using(workspace.newBufferedWriter(file.toString, UTF_8)) { writer => writer.write(content)
    }
  }

  protected def using[A <: { def close() }, B](resource: A)(f: A => B): B = {
    try f(resource)
    finally resource.close()
  }

  // ref. https://github.com/muga/digdag/blob/aff3dfab0b91aa6787d7921ce34d5b3b21947c20/digdag-plugin-utils/src/main/java/io/digdag/util/Workspace.java#L84-L95
  protected def withTempDir[T](prefix: String)(f: Path => T): T = {
    val dir = workspace.getProjectPath.resolve(".digdag/tmp")
    Files.createDirectories(dir)
    val tempDir: Path = Files.createTempDirectory(dir, prefix)
    try f(tempDir)
    finally FileUtils.deleteDirectory(tempDir.toFile)
  }

}
