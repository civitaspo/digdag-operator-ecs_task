package pro.civitaspo.digdag.plugin.ecs_task.sh
import io.digdag.client.config.{Config, ConfigFactory}
import io.digdag.spi.{OperatorContext, TemplateEngine}
import io.digdag.util.Workspace
import org.slf4j.Logger
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.{AmazonS3UriWrapper, Aws}
import pro.civitaspo.digdag.plugin.ecs_task.command.{EcsTaskCommandOperator, TmpStorage}
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.io.Source

class EcsTaskShOperotar(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine)
    with EcsTaskCommandOperator {

  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/sh/run.sh"

  protected val command: String = params.get("_command", classOf[String])

  override def getOperatorName: String = operatorName
  override def getContext: OperatorContext = context
  override def getConfigFactory: ConfigFactory = cf
  override def getWorkspace: Workspace = workspace
  override def getParams: Config = params
  override def getSessionUuid: String = sessionUuid
  override def getAws: Aws = aws
  override def getLogger: Logger = logger
  override def getMainScriptName: String = "run.sh"

  override def prepare(tmpStorage: TmpStorage): Unit = {
    tmpStorage.stageFile(getMainScriptName, createRunShContent(tmpStorage))
    tmpStorage.stageWorkspace()
    tmpStorage.storeStagedFiles()
  }

  protected def createRunShContent(tmpStorage: TmpStorage): String = {
    val dup: Config = params.deepCopy()
    dup.set("ECS_TASK_SH_BUCKET", AmazonS3UriWrapper(tmpStorage.getLocation).getBucket)
    dup.set("ECS_TASK_SH_PREFIX", AmazonS3UriWrapper(tmpStorage.getLocation).getKey)
    dup.set("ECS_TASK_SH_COMMAND", command)

    TryWithResource(classOf[EcsTaskShOperotar].getResourceAsStream(runShResourcePath)) { is =>
      val runShContentTemplate: String = Source.fromInputStream(is).mkString
      templateEngine.template(runShContentTemplate, dup)
    }
  }
}
