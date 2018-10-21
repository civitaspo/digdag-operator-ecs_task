package io.digdag.plugin.example

import io.digdag.spi.OperatorFactory
import io.digdag.spi.OperatorProvider
import io.digdag.spi.Plugin
import io.digdag.spi.TemplateEngine
import java.util
import javax.inject.Inject

object ExamplePlugin {

  class ExampleOperatorProvider extends OperatorProvider {
    @Inject protected var templateEngine: TemplateEngine = null

    override def get: util.List[OperatorFactory] =
      util.Arrays.asList(new ExampleOperatorFactory(templateEngine), new HelloOperatorFactory(templateEngine))
  }

}

class ExamplePlugin extends Plugin {
  override def getServiceProvider[T](`type`: Class[T]): Class[_ <: T] =
    if (`type` eq classOf[OperatorProvider])
      classOf[ExamplePlugin.ExampleOperatorProvider].asSubclass(`type`)
    else null
}
