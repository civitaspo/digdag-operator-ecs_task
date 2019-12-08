package pro.civitaspo.digdag.plugin.ecs_task.wait


import java.util.concurrent.{Executors, ExecutorService}

import com.amazonaws.services.ecs.AmazonECS
import com.amazonaws.services.ecs.model.{DescribeTasksRequest, DescribeTasksResult}
import com.amazonaws.services.ecs.waiters.DescribeTasksFunction
import com.amazonaws.waiters._
import com.amazonaws.waiters.PollingStrategy.DelayStrategy
import io.digdag.client.config.{Config, ConfigException}
import io.digdag.util.DurationParam
import org.slf4j.Logger

import scala.jdk.CollectionConverters._


case class EcsTaskWaiter(
    logger: Logger,
    ecs: AmazonECS,
    executorService: ExecutorService = Executors.newFixedThreadPool(50),
    timeout: DurationParam,
    condition: String,
    status: String,
    pollingStrategy: Config
)
{

    sealed trait IntervalType
    {
        def value: String =
        {
            toString
        }
    }

    object IntervalType
    {
        case object constant
            extends IntervalType
        case object exponential
            extends IntervalType
        private val values = Seq(constant, exponential)

        def from(value: String): IntervalType =
        {
            values.find(_.value == value).getOrElse {
                val message: String = s"""interval_type: \"$value\" is not supported. Available `interval_type`s are \"constant\", \"exponential\"."""
                throw new ConfigException(message)
            }
        }
    }

    val limit: Int = pollingStrategy.get("limit", classOf[Int], Int.MaxValue)
    val interval: Int = pollingStrategy.get("interval", classOf[Int], 1)
    val intervalType: String = pollingStrategy.get("interval_type", classOf[String], IntervalType.constant.value)

    private def delayStrategy: DelayStrategy =
    {
        IntervalType.from(intervalType) match {
            case IntervalType.constant    =>
                new FixedDelayStrategy(interval)
            case IntervalType.exponential =>
                new ExponentialBackoffDelayStrategy(interval)
        }
    }

    def wait(req: DescribeTasksRequest): Unit =
    {
        newWaiter().run(new WaiterParameters[DescribeTasksRequest]().withRequest(req))
    }

    def shutdown(): Unit =
    {
        executorService.shutdown()
    }

    private def newWaiter(): Waiter[DescribeTasksRequest] =
    {
        new WaiterBuilder[DescribeTasksRequest, DescribeTasksResult]
            .withSdkFunction(new DescribeTasksFunction(ecs))
            .withAcceptors(newAcceptor())
            .withDefaultPollingStrategy(newPollingStrategy())
            .withExecutorService(executorService)
            .build()
    }

    private def newAcceptor(): WaiterAcceptor[DescribeTasksResult] =
    {
        val startAt: Long = System.currentTimeMillis()

        new WaiterAcceptor[DescribeTasksResult]
        {
            override def matches(output: DescribeTasksResult): Boolean =
            {
                val waitingMillis: Long = System.currentTimeMillis() - startAt
                logger.info(
                    s"Waiting ${waitingMillis}ms for that $condition tasks [${output.getTasks.asScala.map(t => s"${t.getTaskArn}:${t.getLastStatus}").mkString(",")}] become $status."
                    )
                if (waitingMillis > timeout.getDuration.toMillis) {
                    throw new WaiterTimedOutException(s"Reached timeout ${timeout.getDuration.toMillis}ms without transitioning to the desired state '$status'.")
                }

                condition match {
                    case "all" => output.getTasks.asScala.forall(t => t.getLastStatus.equals(status))
                    case "any" => output.getTasks.asScala.exists(t => t.getLastStatus.equals(status))
                    case _     => throw new ConfigException(s"condition: $condition is unsupported.")
                }
            }

            override def getState: WaiterState =
            {
                WaiterState.SUCCESS
            }
        }
    }

    private def newPollingStrategy(): PollingStrategy =
    {
        new PollingStrategy(new MaxAttemptsRetryStrategy(limit), delayStrategy)
    }

}
