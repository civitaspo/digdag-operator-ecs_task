package pro.civitaspo.digdag.plugin.ecs_task.embulk
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

import io.digdag.client.config.{Config, ConfigFactory}
import io.digdag.spi.{OperatorContext, TemplateEngine}
import io.digdag.util.Workspace
import org.slf4j.Logger
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.{AmazonS3UriWrapper, Aws}
import pro.civitaspo.digdag.plugin.ecs_task.command.{EcsTaskCommandOperator, TmpStorage}
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.collection.JavaConverters._
import scala.io.Source
import scala.util.Try

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

  override def getOperatorName: String = operatorName
  override def getContext: OperatorContext = context
  override def getConfigFactory: ConfigFactory = cf
  override def getWorkspace: Workspace = workspace
  override def getParams: Config = params
  override def getSessionUuid: String = sessionUuid
  override def getAws: Aws = aws
  override def getLogger: Logger = logger
  override def getMainScriptName: String = "run.sh"

  protected val embulkPlugins: Seq[String] = params.getListOrEmpty("embulk_plugins", classOf[String]).asScala

  override def prepare(tmpStorage: TmpStorage): Unit = {
    tmpStorage.stageFile("config.yml", embulkConfig)
    tmpStorage.stageFile(getMainScriptName, createRunShContent(tmpStorage))
    tmpStorage.stageWorkspace()
    tmpStorage.storeStagedFiles()
  }

 protected def createRunShContent(tmpStorage: TmpStorage): String = {
    val dup: Config = params.deepCopy()
    dup.set("ECS_TASK_EMBULK_BUCKET", AmazonS3UriWrapper(tmpStorage.getLocation).getBucket)
    dup.set("ECS_TASK_EMBULK_PREFIX", AmazonS3UriWrapper(tmpStorage.getLocation).getKey)

    dup.set("ECS_TASK_EMBULK_SETUP_COMMAND", "echo 'no setup command'") // set a default value
    if (embulkPlugins.nonEmpty) {
      logger.warn("`embulk_plugins` option is experimental, so please be careful in the plugin update.")
      val cmd: String = (Seq("embulk", "gem", "install") ++ embulkPlugins).mkString(" ")
      dup.set("ECS_TASK_EMBULK_SETUP_COMMAND", cmd)
    }

    TryWithResource(classOf[EcsTaskEmbulkOperator].getResourceAsStream(runShResourcePath)) { is =>
      val runShContentTemplate: String = Source.fromInputStream(is).mkString
      templateEngine.template(runShContentTemplate, dup)
    }
  }
}
