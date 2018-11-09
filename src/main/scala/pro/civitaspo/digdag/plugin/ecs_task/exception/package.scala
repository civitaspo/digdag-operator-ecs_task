package pro.civitaspo.digdag.plugin.ecs_task

package object exception {
  class RetryTimeoutException(message: String = "", cause: Throwable = null) extends RuntimeException(message, cause)
}
