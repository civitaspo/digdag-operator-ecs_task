package pro.civitaspo.digdag.plugin.ecs_task.command
import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.spi.TaskResult

trait EcsTaskCommandOperator {

  def createRunner(): EcsTaskCommandRunner

  def additionalEnvironments(): Map[String, String]

  def prepare(): Unit

  def runTask(): TaskResult = {
    prepare()
    createRunner().run()
  }

}
