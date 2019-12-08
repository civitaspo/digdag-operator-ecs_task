package pro.civitaspo.digdag.plugin.ecs_task.embulk
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper
import pro.civitaspo.digdag.plugin.ecs_task.command.{AbstractEcsTaskCommandOperator, TmpStorage}
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.jdk.CollectionConverters._
import scala.io.Source
import scala.util.Try

class EcsTaskEmbulkOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskCommandOperator(operatorName, context, systemConfig, templateEngine) {

  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/embulk/run.sh"
  override protected val mainScriptName: String = "run.sh"

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
  protected val embulkPlugins: Seq[String] = params.getListOrEmpty("embulk_plugins", classOf[String]).asScala.toSeq

  override def prepare(tmpStorage: TmpStorage): Unit = {
    tmpStorage.stageFile("config.yml", embulkConfig)
    tmpStorage.stageFile(mainScriptName, createRunShContent(tmpStorage))
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
