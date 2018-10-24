package pro.civitaspo.digdag.plugin.ecs_task.py
import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper

class EcsTaskPyOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  val command: String = params.get("_command", classOf[String])
  val workspaceS3UriPrefix: AmazonS3URI = AmazonS3UriWrapper(params.get("workspace_s3_uri_prefix", classOf[String]))

  override def runTask(): TaskResult = {
    null
  }

}
