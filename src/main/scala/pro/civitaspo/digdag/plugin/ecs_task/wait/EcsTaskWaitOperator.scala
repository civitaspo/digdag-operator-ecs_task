package pro.civitaspo.digdag.plugin.ecs_task.wait


import com.amazonaws.services.ecs.model.{DescribeTasksRequest, DescribeTasksResult, Failure, StopTaskRequest}
import com.google.common.base.Throwables
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import io.digdag.util.DurationParam
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator

import scala.jdk.CollectionConverters._


class EcsTaskWaitOperator(operatorName: String,
                          context: OperatorContext,
                          systemConfig: Config,
                          templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine)
{

    val cluster: String = params.get("cluster", classOf[String])
    val tasks: Seq[String] = params.parseList("tasks", classOf[String]).asScala.toSeq
    val timeout: DurationParam = params.get("timeout", classOf[DurationParam], DurationParam.parse("15m"))
    val condition: String = params.get("condition", classOf[String], "all")
    val status: String = params.get("status", classOf[String], "STOPPED")
    val ignoreFailure: Boolean = params.get("ignore_failure", classOf[Boolean], false)
    val ignoreExitCode: Boolean = params.get("ignore_exit_code", classOf[Boolean], false)
    val pollingStrategy: Config = params.getNestedOrGetEmpty("polling_strategy")

    override def runTask(): TaskResult =
    {
        val req: DescribeTasksRequest = new DescribeTasksRequest()
            .withCluster(cluster)
            .withTasks(tasks: _*)

        aws.withEcs { ecs =>
            val waiter: EcsTaskWaiter =
                EcsTaskWaiter(logger = logger, ecs = ecs, timeout = timeout, condition = condition, status = status, pollingStrategy = pollingStrategy)
            try {
                waiter.wait(req)
            }
            catch {
                case e: Throwable =>
                    logger.warn(s"Stop tasks: tasks=[${tasks.mkString(",")}] reason=${e.getMessage}")
                    tasks.foreach { t =>
                        try ecs.stopTask(new StopTaskRequest().withCluster(cluster).withTask(t).withReason(e.getMessage))
                        catch {
                            case e: Throwable => logger.warn(s"Failed to stop task: task=${t}, reason=${e.getMessage}")
                        }
                    }
                    throw Throwables.propagate(e)
            }
            finally {
                waiter.shutdown()
            }
        }
        val result: DescribeTasksResult = aws.withEcs(_.describeTasks(req))
        val failures: Seq[Failure] = result.getFailures.asScala.toSeq
        if (failures.nonEmpty) {
            val failureMessages: String = failures.map(_.toString).mkString(", ")
            if (!ignoreFailure) throw new IllegalStateException(s"Some tasks are failed: [$failureMessages]")
            else logger.warn(s"Some tasks are failed but ignore them: $failureMessages")
        }

        val failedMessages = Seq.newBuilder[String]
        result.getTasks.asScala.foreach { task =>
            task.getContainers.asScala.foreach { container =>
                Option(container.getExitCode) match {
                    case Some(code) =>
                        val message = s"[${task.getTaskArn}] ${container.getName} has stopped with exit_code=$code"
                        logger.info(message)
                        if (!code.equals(0)) {
                            if (!ignoreExitCode) failedMessages += message
                            else logger.warn(s"Ignore failures because of ignore_exit_code=true: $message")
                        }
                    case None       =>
                        val message =
                            s"[${task.getTaskArn}] ${container.getName} has stopped without exit_code: reason=${container.getReason}, task_stopped_reason=${task.getStoppedReason}"
                        logger.info(message)
                        failedMessages += message
                }
            }
        }
        if (failedMessages.result().nonEmpty) {
            val message: String = failedMessages.result().mkString(", ")
            if (!ignoreFailure) throw new IllegalStateException(s"Failure messages: $message")
            else logger.warn(s"Some tasks are failed but ignore them: $message")
        }

        TaskResult.empty(cf)
    }
}
