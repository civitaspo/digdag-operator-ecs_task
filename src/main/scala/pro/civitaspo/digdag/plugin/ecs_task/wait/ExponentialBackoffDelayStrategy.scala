package pro.civitaspo.digdag.plugin.ecs_task.wait


import com.amazonaws.waiters.PollingStrategy.DelayStrategy
import com.amazonaws.waiters.PollingStrategyContext


class ExponentialBackoffDelayStrategy(interval: Int)
    extends DelayStrategy
{

    override def delayBeforeNextRetry(pollingStrategyContext: PollingStrategyContext): Unit =
    {
        val nextDurationSec =
            interval * Math.pow(2, pollingStrategyContext.getRetriesAttempted)

        Thread.sleep(nextDurationSec.toLong * 1000)
    }
}
