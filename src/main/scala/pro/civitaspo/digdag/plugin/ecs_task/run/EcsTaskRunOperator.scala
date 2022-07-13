package pro.civitaspo.digdag.plugin.ecs_task.run

import com.google.common.base.Optional
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, TaskResult, TemplateEngine}
import io.digdag.util.DurationParam
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator

class EcsTaskRunOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  val cluster: String = params.get("cluster", classOf[String])
  val taskDef: Optional[Config] = params.getOptionalNested("def")
  val resultS3Uri: Optional[String] = params.getOptional("result_s3_uri", classOf[String])
  val timeout: DurationParam = params.get("timeout", classOf[DurationParam], DurationParam.parse("15m"))
  val pollingStrategy: Optional[Config] = params.getOptionalNested("polling_strategy")

  override def runTask(): TaskResult = {
    val subTasks: Config = cf.create()
    if (taskDef.isPresent) subTasks.setNested("+register", ecsTaskRegisterSubTask())
    subTasks.setNested("+run", ecsTaskRunInternalSubTask())
    subTasks.setNested("+wait", ecsTaskWaitSubTask())
    if (resultS3Uri.isPresent) subTasks.setNested("+result", ecsTaskResultSubTask())

    val builder = TaskResult.defaultBuilder(cf)
    builder.subtaskConfig(subTasks)
    builder.build()
  }

  protected def ecsTaskRegisterSubTask(): Config = {
    withDefaultSubTask { subTask =>
      subTask.set("_type", "ecs_task.register")
      subTask.set("_command", taskDef)
    }
  }

  protected def ecsTaskRunInternalSubTask(): Config = {
    val config: Config = params.deepCopy()
    Seq("def", "result_s3_uri_prefix", "timeout").foreach(config.remove)
    if (taskDef.isPresent) {
      if (config.has("last_ecs_task_register")) config.remove("last_ecs_task_register")
      config.set("task_definition", "${last_ecs_task_register.task_definition_arn}")
    }
    withDefaultSubTask { subTask =>
      subTask.set("_type", "ecs_task.run_internal")
      subTask.set("_export", config)
    }
  }

  protected def ecsTaskWaitSubTask(): Config = {
    withDefaultSubTask { subTask =>
      subTask.set("_type", "ecs_task.wait")
      subTask.set("cluster", cluster)
      subTask.set("tasks", "${last_ecs_task_run.task_arns}")
      subTask.set("timeout", timeout.toString)
      if (pollingStrategy.isPresent) subTask.set("polling_strategy", pollingStrategy.get())
    }
  }

  protected def ecsTaskResultSubTask(): Config = {
    withDefaultSubTask { subTask =>
      subTask.set("_type", "ecs_task.result")
      subTask.set("_command", resultS3Uri.get)
    }
  }

  protected def withDefaultSubTask(f: Config => Unit): Config = {
    val subTask: Config = cf.create()

    subTask.set("auth_method", aws.conf.authMethod)
    subTask.set("profile_name", aws.conf.profileName)
    if (aws.conf.profileFile.isPresent) subTask.set("profile_file", aws.conf.profileFile.get())
    subTask.set("use_http_proxy", aws.conf.useHttpProxy)
    if (aws.conf.region.isPresent) subTask.set("region", aws.conf.region.get())
    if (aws.conf.endpoint.isPresent) subTask.set("endpoint", aws.conf.endpoint.get())

    f(subTask)
    subTask
  }
}
