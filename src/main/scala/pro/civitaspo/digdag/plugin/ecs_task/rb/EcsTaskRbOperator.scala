package pro.civitaspo.digdag.plugin.ecs_task.rb
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

class EcsTaskRbOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
  extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine)
    with EcsTaskCommandOperator {

  private val runnerRbResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/rb/runner.rb"
  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/rb/run.sh"

  protected val command: String = params.get("_command", classOf[String])
  protected val gemInstall: Seq[String] = params.getListOrEmpty("gem_install", classOf[String]).asScala
  protected val requirePath: String = params.get("require", classOf[String])

  override def getOperatorName: String = operatorName
  override def getContext: OperatorContext = context
  override def getSessionUuid: String = sessionUuid
  override def getConfigFactory: ConfigFactory = cf
  override def getWorkspace: Workspace = workspace
  override def getParams: Config = params
  override def getAws: Aws = aws
  override def getLogger: Logger = logger
  override def getMainScriptName: String = "run.sh"


  override def prepare(tmpStorage: TmpStorage): Unit = {
    tmpStorage.stageFile("in.json", createInJsonContent())
    tmpStorage.stageFile("runner.rb", createRunnerRbContent())
    tmpStorage.stageFile(getMainScriptName, createRunShContent(tmpStorage))
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
