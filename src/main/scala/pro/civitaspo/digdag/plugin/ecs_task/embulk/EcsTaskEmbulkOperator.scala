package pro.civitaspo.digdag.plugin.ecs_task.embulk
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TemplateEngine}
import org.apache.commons.io.FileUtils
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper
import pro.civitaspo.digdag.plugin.ecs_task.command.{EcsTaskCommandOperator, EcsTaskCommandRunner}
import pro.civitaspo.digdag.plugin.ecs_task.util.{TryWithResource, WorkspaceWithTempDir}

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.{Random, Try}

class EcsTaskEmbulkOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine)
    with EcsTaskCommandOperator {

  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/embulk/run.sh"

  protected val embulkConfig: String = {
    val t: Try[String] = Try {
      val embulkConfigPath: String = params.get("_command", classOf[String])
      val f: File = workspace.getFile(embulkConfigPath)
      workspace.templateFile(templateEngine, f.getPath, UTF_8, params)
    }
    t.getOrElse {
      val embulkConfig: Config = params.getNested("_command")
      templateEngine.template(embulkConfig.toString, params)
    }
  }
  protected val workspaceS3UriPrefix: AmazonS3URI = {
    val parent: String = params.get("workspace_s3_uri_prefix", classOf[String])
    val random: String = Random.alphanumeric.take(10).mkString
    if (parent.endsWith("/")) AmazonS3UriWrapper(s"$parent$operatorName.$sessionUuid.$random")
    else AmazonS3UriWrapper(s"$parent/$operatorName.$sessionUuid.$random")
  }
  protected val embulkPlugins: Seq[String] = params.getListOrEmpty("embulk_plugins", classOf[String]).asScala

  override def createRunner(): EcsTaskCommandRunner = {
    EcsTaskCommandRunner(
      scriptsLocationPrefix = workspaceS3UriPrefix,
      script = "run.sh",
      params = params,
      environments = additionalEnvironments(),
      awsConf = aws.conf,
      logger = logger
    )
  }

  override def additionalEnvironments(): Map[String, String] = {
    val vars = context.getPrivilegedVariables
    val builder = Map.newBuilder[String, String]
    vars.getKeys.asScala.foreach { k => builder += (k -> vars.get(k))
    }
    builder.result()
  }
  override def prepare(): Unit = {
    WorkspaceWithTempDir(workspace) { tempDir: Path =>
      createConfigFile(tempDir)
      createRunShFile(tempDir)
      createWorkspaceDir(tempDir)
      uploadOnS3(tempDir)
    }
  }

  protected def createConfigFile(parent: Path): Unit = {
    val configFile: Path = Files.createFile(parent.resolve("config.yml"))
    writeFile(file = configFile, content = embulkConfig)
  }

  protected def createRunShFile(parent: Path): Unit = {
    val dup: Config = params.deepCopy()
    dup.set("ECS_TASK_EMBULK_BUCKET", workspaceS3UriPrefix.getBucket)
    dup.set("ECS_TASK_EMBULK_PREFIX", workspaceS3UriPrefix.getKey)

    dup.set("ECS_TASK_EMBULK_SETUP_COMMAND", "echo 'no setup command'") // set a default value
    if (embulkPlugins.nonEmpty) {
      logger.warn("`embulk_plugins` option is experimental, so please be careful in the plugin update.")
      val cmd: String = (Seq("embulk", "gem", "install") ++ embulkPlugins).mkString(" ")
      dup.set("ECS_TASK_EMBULK_SETUP_COMMAND", cmd)
    }

    TryWithResource(classOf[EcsTaskEmbulkOperator].getResourceAsStream(runShResourcePath)) { is =>
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
    TryWithResource(workspace.newBufferedWriter(file.toString, UTF_8)) { writer => writer.write(content)
    }
  }
}
