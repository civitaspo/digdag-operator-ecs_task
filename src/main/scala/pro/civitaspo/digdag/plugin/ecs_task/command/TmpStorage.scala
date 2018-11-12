package pro.civitaspo.digdag.plugin.ecs_task.command
import io.digdag.util.Workspace

trait TmpStorage extends AutoCloseable {

  val workspace: Workspace

  def getLocation: String

  def stageFile(fileName: String, content: String)

  def stageWorkspace(): Unit

  def buildTaskCommand(mainScript: String): Seq[String]

  def storeStagedFiles(): Unit

}
