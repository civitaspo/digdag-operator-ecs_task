package io.digdag.plugin.example

import java.nio.charset.StandardCharsets.UTF_8
import io.digdag.client.config.Config
import io.digdag.spi.Operator
import io.digdag.spi.OperatorContext
import io.digdag.spi.OperatorFactory
import io.digdag.spi.TaskResult
import io.digdag.spi.TemplateEngine
import io.digdag.util.BaseOperator
import java.io.IOException
import java.nio.file.Files
import com.google.common.base.Throwables

class ExampleOperatorFactory(val templateEngine: TemplateEngine) extends OperatorFactory {
  override def getType = "example"

  override def newOperator(context: OperatorContext) =
    new ExampleOperator(context)

  class ExampleOperator(context: OperatorContext) extends BaseOperator(context) {
    override def runTask: TaskResult = {
      val params = request.getConfig.mergeDefault(request.getConfig.getNestedOrGetEmpty("example"))
      val message =
        workspace.templateCommand(templateEngine, params, "message", UTF_8)
      val path = params.get("path", classOf[String])
      try Files.write(workspace.getPath(path), message.getBytes(UTF_8))
      catch {
        case ex: IOException =>
          throw Throwables.propagate(ex)
      }
      TaskResult.empty(request)
    }
  }

}
