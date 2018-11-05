package pro.civitaspo.digdag.plugin.ecs_task.command
import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper

import scala.util.{Failure, Try}

class EcsTaskCommandResultInternalOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  protected val locationPrefix: AmazonS3URI = AmazonS3UriWrapper(params.get("_command", classOf[String]))

  override def runTask(): TaskResult = {
    logStdoutStderr()

    val out: Config = loadOutJsonContent()
    val statusParams: Config = out.getNested("status_params")
    val exitCode: Int = statusParams.get("exit_code", classOf[Int])

    if (exitCode != 0) {
      val errorMessage: String = statusParams.get("error_message", classOf[String], "")
      val errorStackTrace: String = statusParams.get("error_stacktrace", classOf[String], "")
      val stdout: String = Try(loadStdoutLogContent()).getOrElse("")
      val stderr: String = Try(loadStderrLogContent()).getOrElse("")
      throw new RuntimeException(s"message: '$errorMessage',\nstacktrace: '$errorStackTrace',\nstdout: '$stdout'\nstderr: '$stderr'")
    }

    TaskResult
      .defaultBuilder(cf)
      .subtaskConfig(out.getNestedOrGetEmpty("subtask_config"))
      .exportParams(out.getNestedOrGetEmpty("export_params"))
      .storeParams(
        out
          .getNestedOrGetEmpty("store_params")
          .setNested("last_ecs_task_command", statusParams)
      )
      .build()
  }

  protected def loadOutJsonContent(): Config = {
    val targetUri: AmazonS3URI = AmazonS3UriWrapper(s"$locationPrefix/out.json")
    val content: String = loadS3ObjectContent(targetUri)
    cf.fromJsonString(content)
  }

  protected def logStdoutStderr(): Unit = {
    val t: Try[Unit] = Try { // do nothing if failed
      logger.info(s"stdout: ${loadStdoutLogContent()}")
      logger.info(s"stderr: ${loadStderrLogContent()}")
    }
    t match {
      case Failure(exception) => logger.error(exception.getMessage, exception)
      case _ => // do nothing
    }
  }

  protected def loadStdoutLogContent(): String = {
    val targetUri: AmazonS3URI = AmazonS3UriWrapper(s"$locationPrefix/stdout.log")
    loadS3ObjectContent(targetUri)
  }

  protected def loadStderrLogContent(): String = {
    val targetUri: AmazonS3URI = AmazonS3UriWrapper(s"$locationPrefix/stderr.log")
    loadS3ObjectContent(targetUri)
  }

  protected def loadS3ObjectContent(uri: AmazonS3URI): String = {
    logger.info(s"Load content from: $uri")
    aws.withS3(_.getObjectAsString(uri.getBucket, uri.getKey))
  }
}
