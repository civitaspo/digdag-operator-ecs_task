package pro.civitaspo.digdag.plugin.ecs_task.command
import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.spi.TaskResult

trait EcsTaskCommandOperator {

  val runner: EcsTaskCommandRunner

  def uploadScript(): AmazonS3URI

  def runTask(): TaskResult = {
    runner.run(scriptLocation = uploadScript())
  }

}
