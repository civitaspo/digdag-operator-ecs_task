package pro.civitaspo.digdag.plugin.ecs_task.py
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

class EcsTaskPyOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine)
    with EcsTaskCommandOperator {

  private val runnerPyResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/py/runner.py"
  private val runShResourcePath: String = "/pro/civitaspo/digdag/plugin/ecs_task/py/run.sh"

  protected val command: String = params.get("_command", classOf[String])
  protected val pipInstall: Seq[String] = params.getListOrEmpty("pip_install", classOf[String]).asScala

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
    tmpStorage.stageFile(getMainScriptName, createRunShContent(tmpStorage))
    tmpStorage.stageFile("runner.py", createRunnerPyContent())
    tmpStorage.stageWorkspace()
    tmpStorage.storeStagedFiles()
  }

  protected def createInJsonContent(): String = {
    templateEngine.template(cf.create.set("params", params).toString, params)
  }

  protected def createRunnerPyContent(): String = {
    TryWithResource(classOf[EcsTaskPyOperator].getResourceAsStream(runnerPyResourcePath)) { is =>
      Source.fromInputStream(is).mkString
    }
  }

  protected def createRunShContent(tmpStorage: TmpStorage): String = {
    val dup: Config = params.deepCopy()
    dup.set("ECS_TASK_PY_BUCKET", AmazonS3UriWrapper(tmpStorage.getLocation).getBucket)
    dup.set("ECS_TASK_PY_PREFIX", AmazonS3UriWrapper(tmpStorage.getLocation).getKey)
    dup.set("ECS_TASK_PY_COMMAND", command)

    dup.set("ECS_TASK_PY_SETUP_COMMAND", "echo 'no setup command'") // set a default value
    if (pipInstall.nonEmpty) {
      logger.warn("`pip_install` option is experimental, so please be careful in the plugin update.")
      val cmd: String = (Seq("pip", "install") ++ pipInstall).mkString(" ")
      dup.set("ECS_TASK_PY_SETUP_COMMAND", cmd)
    }

    TryWithResource(classOf[EcsTaskPyOperator].getResourceAsStream(runShResourcePath)) { is =>
      val runShContentTemplate: String = Source.fromInputStream(is).mkString
      templateEngine.template(runShContentTemplate, dup)
    }
  }
}
