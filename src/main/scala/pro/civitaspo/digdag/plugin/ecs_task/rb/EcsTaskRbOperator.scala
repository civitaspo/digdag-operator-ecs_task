package pro.civitaspo.digdag.plugin.ecs_task.rb
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper
import pro.civitaspo.digdag.plugin.ecs_task.command.{AbstractEcsTaskCommandOperator, TmpStorage}
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.collection.JavaConverters._
import scala.io.Source

class EcsTaskRbOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskCommandOperator(operatorName, context, systemConfig, templateEngine) {

  private val runnerRbResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/rb/runner.rb"
  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/rb/run.sh"
  override protected val mainScriptName: String = "run.sh"

  protected val command: String = params.get("_command", classOf[String])
  protected val gemInstall: Seq[String] = params.getListOrEmpty("gem_install", classOf[String]).asScala
  protected val requirePath: String = params.get("require", classOf[String])

  override def prepare(tmpStorage: TmpStorage): Unit = {
    tmpStorage.stageFile("in.json", createInJsonContent())
    tmpStorage.stageFile("runner.rb", createRunnerRbContent())
    tmpStorage.stageFile(mainScriptName, createRunShContent(tmpStorage))
    tmpStorage.stageWorkspace()
    tmpStorage.storeStagedFiles()
  }

  protected def createInJsonContent(): String = {
    templateEngine.template(cf.create.set("params", params).toString, params)
  }

  protected def createRunnerRbContent(): String = {
    TryWithResource(classOf[EcsTaskRbOperator].getResourceAsStream(runnerRbResourcePath)) { is =>
      Source.fromInputStream(is).mkString
    }
  }

  protected def createRunShContent(tmpStorage: TmpStorage): String = {
    val dup: Config = params.deepCopy()
    dup.set("ECS_TASK_RB_BUCKET", AmazonS3UriWrapper(tmpStorage.getLocation).getBucket)
    dup.set("ECS_TASK_RB_PREFIX", AmazonS3UriWrapper(tmpStorage.getLocation).getKey)
    dup.set("ECS_TASK_RB_REQUIRE", requirePath)
    dup.set("ECS_TASK_RB_COMMAND", command)

    dup.set("ECS_TASK_RB_SETUP_COMMAND", "echo 'no setup command'") // set a default value
    if (gemInstall.nonEmpty) {
      logger.warn("`gem_install` option is experimental, so please be careful in the plugin update.")
      val cmd: String = (Seq("gem", "install") ++ gemInstall).mkString(" ")
      dup.set("ECS_TASK_RB_SETUP_COMMAND", cmd)
    }

    TryWithResource(classOf[EcsTaskRbOperator].getResourceAsStream(runShResourcePath)) { is =>
      val runShContentTemplate: String = Source.fromInputStream(is).mkString
      templateEngine.template(runShContentTemplate, dup)
    }
  }
}
