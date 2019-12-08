package pro.civitaspo.digdag.plugin.ecs_task.command


import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator


class EcsTaskCallInternalOperator(operatorName: String,
                                  context: OperatorContext,
                                  systemConfig: Config,
                                  templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine)
{

    protected val doConfig: Config = params.getNested("_do")

    override def runTask(): TaskResult =
    {
        TaskResult.defaultBuilder(cf).subtaskConfig(doConfig).build()
    }

}
