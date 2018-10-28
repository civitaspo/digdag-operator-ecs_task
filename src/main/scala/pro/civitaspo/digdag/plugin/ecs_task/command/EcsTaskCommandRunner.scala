package pro.civitaspo.digdag.plugin.ecs_task.command
import com.amazonaws.services.s3.AmazonS3URI
import com.google.common.base.Optional
import io.digdag.client.config.{Config, ConfigFactory}
import io.digdag.spi.TaskResult
import io.digdag.util.DurationParam

import scala.collection.JavaConverters._

case class EcsTaskCommandRunner(params: Config) {

  val cf: ConfigFactory = params.getFactory

  // For ecs_task.register>  operator (TaskDefinition)
  // NOTE: Use only 1 container
  // val containerDefinitions: Seq[ContainerDefinition] = params.getList("container_definitions", classOf[Config]).asScala.map(configureContainerDefinition).map(_.get)
  val cpu: Optional[String] = params.getOptional("cpu", classOf[String])
  val executionRoleArn: Optional[String] = params.getOptional("execution_role_arn", classOf[String])
  val family: String = params.get("family", classOf[String])
  val memory: Optional[String] = params.getOptional("memory", classOf[String])
  val networkMode: Optional[String] = params.getOptional("network_mode", classOf[String])
  // NOTE: Use `ecs_task.run>`'s one.
  // val placementConstraints: Seq[TaskDefinitionPlacementConstraint] = params.getListOrEmpty("placement_constraints", classOf[Config]).asScala.map(configureTaskDefinitionPlacementConstraint).map(_.get)
  val requiresCompatibilities: Seq[String] = params.getListOrEmpty("requires_compatibilities", classOf[String]).asScala // Valid Values: EC2 | FARGATE
  val taskRoleArn: Optional[String] = params.getOptional("task_role_arn", classOf[String])
  val volumes: Seq[Config] = params.getListOrEmpty("volumes", classOf[Config]).asScala

  // For `ecs_task.register>` operator (ContainerDefinition)
  // NOTE: Set by this plugin
  // val command: Seq[String] = params.getListOrEmpty("command", classOf[String]).asScala
  // NOTE: Set in `ecs_task.register>` TaskDefinition Context.
  // val cpu: Optional[Int] = params.getOptional("cpu", classOf[Int])
  val disableNetworking: Optional[Boolean] = params.getOptional("disable_networking", classOf[Boolean])
  val dnsSearchDomains: Seq[String] = params.getListOrEmpty("dns_search_domains", classOf[String]).asScala
  val dnsServers: Seq[String] = params.getListOrEmpty("dns_servers", classOf[String]).asScala
  // NOTE: Add some labels by this plugin
  val dockerLabels: Map[String, String] = params.getMapOrEmpty("docker_labels", classOf[String], classOf[String]).asScala.toMap
  val dockerSecurityOptions: Seq[String] = params.getListOrEmpty("docker_security_options", classOf[String]).asScala
  val entryPoint: Seq[String] = params.getListOrEmpty("entry_point", classOf[String]).asScala
  // NOTE: Add some envs by this plugin
  val environment: Map[String, String] = params.getMapOrEmpty("environment", classOf[String], classOf[String]).asScala.toMap
  // NOTE: This plugin uses only 1 container so `essential` is always true.
  // val essential: Optional[Boolean] = params.getOptional("essential", classOf[Boolean])
  val extraHosts: Map[String, String] = params.getMapOrEmpty("extra_hosts", classOf[String], classOf[String]).asScala.toMap
  val healthCheck: Optional[Config] = params.getOptionalNested("health_check")
  val hostname: Optional[String] = params.getOptional("hostname", classOf[String])
  val image: Optional[String] = params.getOptional("image", classOf[String])
  val interactive: Optional[Boolean] = params.getOptional("interactive", classOf[Boolean])
  val links: Seq[String] = params.getListOrEmpty("links", classOf[String]).asScala
  val linuxParameters: Optional[Config] = params.getOptionalNested("linux_parameters")
  val logConfiguration: Optional[Config] = params.getOptionalNested("log_configuration")
  // NOTE: Set in `ecs_task.register>` TaskDefinition Context.
  // val memory: Optional[Int] = params.getOptional("memory", classOf[Int])
  val memoryReservation: Optional[Int] = params.getOptional("memory_reservation", classOf[Int])
  val mountPoints: Seq[Config] = params.getListOrEmpty("mount_points", classOf[Config]).asScala
  val name: Optional[String] = params.getOptional("name", classOf[String])
  val portMappings: Seq[Config] = params.getListOrEmpty("port_mappings", classOf[Config]).asScala
  val privileged: Optional[Boolean] = params.getOptional("privileged", classOf[Boolean])
  val pseudoTerminal: Optional[Boolean] = params.getOptional("pseudo_terminal", classOf[Boolean])
  val readonlyRootFilesystem: Optional[Boolean] = params.getOptional("readonly_root_filesystem", classOf[Boolean])
  val repositoryCredentials: Optional[Config] = params.getOptionalNested("repository_credentials")
  val systemControls: Seq[Config] = params.getListOrEmpty("system_controls", classOf[Config]).asScala
  val ulimits: Seq[Config] = params.getListOrEmpty("ulimits", classOf[Config]).asScala
  val user: Optional[String] = params.getOptional("user", classOf[String])
  val volumesFrom: Seq[Config] = params.getListOrEmpty("volumes_from", classOf[Config]).asScala
  val workingDirectory: Optional[String] = params.getOptional("working_directory", classOf[String])

  // For ecs_task.run operator
  val cluster: String = params.get("cluster", classOf[String])
  val count: Optional[Int] = params.getOptional("count", classOf[Int])
  val group: Optional[String] = params.getOptional("group", classOf[String])
  val launchType: Optional[String] = params.getOptional("launch_type", classOf[String])
  val networkConfiguration: Optional[Config] = params.getOptionalNested("network_configuration")
  val overrides: Optional[Config] = params.getOptionalNested("overrides")
  val placementConstraints: Seq[Config] = params.getListOrEmpty("placement_constraints", classOf[Config]).asScala
  val placementStrategy: Seq[Config] = params.getListOrEmpty("placement_strategy", classOf[Config]).asScala
  val platformVersion: Optional[String] = params.getOptional("platform_version", classOf[String])
  val startedBy: Optional[String] = params.getOptional("started_by", classOf[String])
  // NOTE: Generated by ecs_task.register operator
  // val taskDefinition: String = params.get("task_definition", classOf[String])

  // For ecs_task.wait operator
  val timeout: DurationParam = params.get("timeout", classOf[DurationParam], DurationParam.parse("15m"))
  val ignoreFailure: Boolean = params.get("ignore_failure", classOf[Boolean], false)

  def run(scriptLocation: AmazonS3URI): TaskResult = {
    TaskResult.empty(cf)
  }

}
