package pro.civitaspo.digdag.plugin.ecs_task.result
import java.io.File

import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.transfer.{Download, TransferManager, TransferManagerBuilder}
import com.fasterxml.jackson.databind.ObjectMapper
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator

import scala.io.Source

class EcsTaskResultOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine, objectMapper: ObjectMapper)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine, objectMapper) {

  object AmazonS3URI {
    def apply(path: String): AmazonS3URI = new AmazonS3URI(path, false)
  }

  val s3Uri: AmazonS3URI = AmazonS3URI(params.get("_command", classOf[String]))

  override def runTask(): TaskResult = {
    val f: String = workspace.createTempFile("ecs_task.result", ".json")
    aws.withS3 { s3 =>
      val xfer: TransferManager = TransferManagerBuilder.standard().withS3Client(s3).build()
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
