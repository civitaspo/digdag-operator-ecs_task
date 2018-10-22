package pro.civitaspo.digdag.plugin.ecs_task.result
import com.amazonaws.services.s3.AmazonS3URI
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.fasterxml.jackson.databind.ObjectMapper
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator

class EcsTaskResultOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine, objectMapper: ObjectMapper)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine, objectMapper) {

  object AmazonS3URI {
    def apply(path: String): AmazonS3URI = new AmazonS3URI(path, false)
  }

  val s3Uri: AmazonS3URI = AmazonS3URI(params.get("s3_uri", classOf[String]))

  override def runTask(): TaskResult = {
    val content: S3ObjectInputStream = aws.withS3(_.getObject(s3Uri.getBucket, s3Uri.getKey)).getObjectContent
    val data: Config = objectMapper.readValue(content, classOf[Config])
    import io.digdag.spi.TaskResult
    TaskResult
      .defaultBuilder(cf)
      .subtaskConfig(data.getNestedOrGetEmpty("subtask_config"))
      .exportParams(data.getNestedOrGetEmpty("export_params"))
      .storeParams(data.getNestedOrGetEmpty("store_params"))
      .build
  }
}
