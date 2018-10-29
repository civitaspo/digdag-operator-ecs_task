package pro.civitaspo.digdag.plugin.ecs_task.command
import java.io.File

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.transfer.Download
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper

import scala.io.Source
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
      val errorMessage: String = statusParams.get("error_message", classOf[String])
      val errorStackTrace: String = statusParams.get("error_stacktrace", classOf[String])
      throw new RuntimeException(s"message: $errorMessage, stacktrace: $errorStackTrace")
    }

    TaskResult
      .defaultBuilder(cf)
      .subtaskConfig(out.getNestedOrGetEmpty("subtask_config"))
      .exportParams(out.getNestedOrGetEmpty("export_params"))
      .storeParams(
        out
          .getNestedOrGetEmpty("store_params")
          .setNested("last_ecs_task_py", statusParams)
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
    val f: String = workspace.createTempFile("ecs_task.command_result_internal", ".txt")
    logger.info(s"Download: $uri -> $f")
    aws.withTransferManager { xfer =>
      val download: Download = xfer.download(uri.getBucket, uri.getKey, new File(f))
      download.waitForCompletion()
    }
    Source.fromFile(f).getLines.mkString
  }

}
