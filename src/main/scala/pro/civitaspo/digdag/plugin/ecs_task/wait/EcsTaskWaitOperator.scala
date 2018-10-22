package pro.civitaspo.digdag.plugin.ecs_task.wait
import com.amazonaws.services.ecs.model.DescribeTasksRequest
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import io.digdag.util.DurationParam
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator

import scala.collection.JavaConverters._

class EcsTaskWaitOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  val cluster: String = params.get("cluster", classOf[String])
  val tasks: Seq[String] = params.getListOrEmpty("tasks", classOf[String]).asScala
  val timeout: DurationParam = params.get("timeout", classOf[DurationParam], DurationParam.parse("15m"))
  val condition: String = params.get("condition", classOf[String], "all")
  val status: String = params.get("status", classOf[String], "STOPPED")
  val ignoreFailure: Boolean = params.get("ignore_failure", classOf[Boolean], false)

  override def runTask(): TaskResult = {
    val req: DescribeTasksRequest = new DescribeTasksRequest().withCluster(cluster)
      .withTasks(tasks: _*)

    aws.withEcs { ecs =>
      val waiter: EcsTaskWaiter = EcsTaskWaiter(ecs = ecs, timeout = timeout, condition = condition, status = status, ignoreFailure = ignoreFailure)
      try waiter.wait(req)
      finally waiter.shutdown()
    }
    TaskResult.empty(cf)
  }
}
