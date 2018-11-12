package pro.civitaspo.digdag.plugin.ecs_task.sh
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper
import pro.civitaspo.digdag.plugin.ecs_task.command.{AbstractEcsTaskCommandOperator, TmpStorage}
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.io.Source

class EcsTaskShOperotar(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskCommandOperator(operatorName, context, systemConfig, templateEngine) {

  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/sh/run.sh"
  override protected val mainScriptName: String = "run.sh"

  protected val command: String = params.get("_command", classOf[String])

  override def prepare(tmpStorage: TmpStorage): Unit = {
    tmpStorage.stageFile(mainScriptName, createRunShContent(tmpStorage))
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
