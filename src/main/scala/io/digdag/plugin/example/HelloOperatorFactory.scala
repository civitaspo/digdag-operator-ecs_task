package io.digdag.plugin.example

import io.digdag.client.config.Config
import io.digdag.spi.Operator
import io.digdag.spi.OperatorContext
import io.digdag.spi.OperatorFactory
import io.digdag.spi.TaskResult
import io.digdag.spi.TemplateEngine
import io.digdag.util.BaseOperator

class HelloOperatorFactory(val templateEngine: TemplateEngine) extends OperatorFactory {
  override def getType = "hello"

  override def newOperator(context: OperatorContext) =
    new HelloOperator(context)

  class HelloOperator private[example] (context: OperatorContext) extends BaseOperator(context) {
    override def runTask: TaskResult = { //Config params = request.getConfig();
      val params = request.getConfig.mergeDefault(request.getConfig.getNestedOrGetEmpty("hello"))
      var message = params.get("_command", classOf[String])
      message += params.get("message", classOf[String])
      System.out.println(message)
      TaskResult.empty(request)
    }
  }

}
