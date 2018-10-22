package pro.civitaspo.digdag.plugin.ecs_task.wait
import com.amazonaws.services.ecs.model.{DescribeTasksRequest, DescribeTasksResult, Failure}
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import io.digdag.util.DurationParam
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator

import scala.collection.JavaConverters._

class EcsTaskWaitOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  val cluster: String = params.get("cluster", classOf[String])
  val tasks: Seq[String] = params.parseList("tasks", classOf[String]).asScala
  val timeout: DurationParam = params.get("timeout", classOf[DurationParam], DurationParam.parse("15m"))
  val condition: String = params.get("condition", classOf[String], "all")
  val status: String = params.get("status", classOf[String], "STOPPED")
  val ignoreFailure: Boolean = params.get("ignore_failure", classOf[Boolean], false)

  override def runTask(): TaskResult = {
    val req: DescribeTasksRequest = new DescribeTasksRequest()
      .withCluster(cluster)
      .withTasks(tasks: _*)

    aws.withEcs { ecs =>
      val waiter: EcsTaskWaiter = EcsTaskWaiter(logger = logger, ecs = ecs, timeout = timeout, condition = condition, status = status)
      try waiter.wait(req)
      finally waiter.shutdown()
    }
    if (!ignoreFailure) {
      val result: DescribeTasksResult = aws.withEcs(_.describeTasks(req))
      val failures: Seq[Failure] = result.getFailures.asScala
      if (failures.nonEmpty) {
        throw new IllegalStateException(s"Some tasks are failed: [${failures.map(_.toString).mkString(", ")}]")
      }

      val failedMessages = Seq.newBuilder[String]
      result.getTasks.asScala.foreach { task =>
        task.getContainers.asScala.foreach { container =>
          Option(container.getExitCode) match {
            case Some(code) =>
              val msg = s"[${task.getTaskArn}] ${container.getName} has stopped with exit_code=$code"
              logger.info(msg)
              if (!code.equals(0)) failedMessages += msg
            case None =>
              val msg = s"[${task.getTaskArn}] ${container.getName} has stopped without exit_code: reason=${container.getReason}"
              logger.info(msg)
              failedMessages += msg
          }
        }
      }
      if (failedMessages.result().nonEmpty) throw new IllegalStateException(s"Failure messages: ${failedMessages.result().mkString(",")}")
    }

    TaskResult.empty(cf)
  }
}
