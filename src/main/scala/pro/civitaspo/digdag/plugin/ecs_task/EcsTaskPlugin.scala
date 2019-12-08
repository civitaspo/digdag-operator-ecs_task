package pro.civitaspo.digdag.plugin.ecs_task


import java.lang.reflect.Constructor
import java.util.{Arrays => JArrays, List => JList}

import io.digdag.client.config.{Config, ConfigException}
import io.digdag.spi.{Operator, OperatorContext, OperatorFactory, OperatorProvider, Plugin, TemplateEngine}
import javax.inject.Inject
import pro.civitaspo.digdag.plugin.ecs_task.command.{EcsTaskCallInternalOperator, EcsTaskCommandResultInternalOperator}
import pro.civitaspo.digdag.plugin.ecs_task.embulk.EcsTaskEmbulkOperator
import pro.civitaspo.digdag.plugin.ecs_task.py.EcsTaskPyOperator
import pro.civitaspo.digdag.plugin.ecs_task.rb.EcsTaskRbOperator
import pro.civitaspo.digdag.plugin.ecs_task.register.EcsTaskRegisterOperator
import pro.civitaspo.digdag.plugin.ecs_task.result.EcsTaskResultOperator
import pro.civitaspo.digdag.plugin.ecs_task.run.{EcsTaskRunInternalOperator, EcsTaskRunOperator}
import pro.civitaspo.digdag.plugin.ecs_task.sh.EcsTaskShOperatar
import pro.civitaspo.digdag.plugin.ecs_task.wait.EcsTaskWaitOperator


object EcsTaskPlugin
{

    class EcsTaskOperatorProvider
        extends OperatorProvider
    {

        @Inject protected var systemConfig: Config = null
        @Inject protected var templateEngine: TemplateEngine = null

        override def get(): JList[OperatorFactory] =
        {
            JArrays.asList(
                operatorFactory("ecs_task.embulk", classOf[EcsTaskEmbulkOperator]),
                operatorFactory("ecs_task.py", classOf[EcsTaskPyOperator]),
                operatorFactory("ecs_task.rb", classOf[EcsTaskRbOperator]),
                operatorFactory("ecs_task.sh", classOf[EcsTaskShOperatar]),
                operatorFactory("ecs_task.call_internal", classOf[EcsTaskCallInternalOperator]),
                operatorFactory("ecs_task.command_result_internal", classOf[EcsTaskCommandResultInternalOperator]),
                operatorFactory("ecs_task.register", classOf[EcsTaskRegisterOperator]),
                operatorFactory("ecs_task.result", classOf[EcsTaskResultOperator]),
                operatorFactory("ecs_task.run", classOf[EcsTaskRunOperator]),
                operatorFactory("ecs_task.run_internal", classOf[EcsTaskRunInternalOperator]),
                operatorFactory("ecs_task.wait", classOf[EcsTaskWaitOperator])
                )
        }

        private def operatorFactory[T <: AbstractEcsTaskOperator](operatorName: String,
                                                                  klass: Class[T]): OperatorFactory =
        {
            new OperatorFactory
            {
                override def getType: String =
                {
                    operatorName
                }

                override def newOperator(context: OperatorContext): Operator =
                {
                    val constructor: Constructor[T] = klass.getConstructor(classOf[String], classOf[OperatorContext], classOf[Config], classOf[TemplateEngine])
                    try {
                        constructor.newInstance(operatorName, context, systemConfig, templateEngine)
                    }
                    catch {
                        case e: Throwable => throw new ConfigException(e)
                    }
                }
            }
        }
    }
}

class EcsTaskPlugin
    extends Plugin
{
    override def getServiceProvider[T](`type`: Class[T]): Class[_ <: T] =
    {
        if (`type` ne classOf[OperatorProvider]) return null
        classOf[EcsTaskPlugin.EcsTaskOperatorProvider].asSubclass(`type`)
    }
}
