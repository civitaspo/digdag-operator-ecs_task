package pro.civitaspo.digdag.plugin.ecs_task.result

import java.io.File

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.transfer.Download
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper

import scala.io.Source

class EcsTaskResultOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  val s3Uri: AmazonS3URI = AmazonS3UriWrapper(params.get("_command", classOf[String]))

  override def runTask(): TaskResult = {
    val f: String = workspace.createTempFile("ecs_task.result", ".json")
    aws.withTransferManager { xfer =>
      val download: Download = xfer.download(s3Uri.getBucket, s3Uri.getKey, new File(f))
      download.waitForCompletion()
    }
    val content: String = Source.fromFile(f).getLines.mkString
    val data: Config = cf.fromJsonString(content)

    TaskResult
      .defaultBuilder(cf)
      .subtaskConfig(data.getNestedOrGetEmpty("subtask_config"))
      .exportParams(data.getNestedOrGetEmpty("export_params"))
      .storeParams(data.getNestedOrGetEmpty("store_params"))
      .build
  }
}
