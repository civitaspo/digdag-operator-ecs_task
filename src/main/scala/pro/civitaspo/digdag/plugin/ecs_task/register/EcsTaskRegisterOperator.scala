package pro.civitaspo.digdag.plugin.ecs_task.register
import com.amazonaws.services.ecs.model.{
  ContainerDefinition,
  Device,
  DockerVolumeConfiguration,
  HealthCheck,
  HostEntry,
  HostVolumeProperties,
  KernelCapabilities,
  KeyValuePair,
  LinuxParameters,
  LogConfiguration,
  MountPoint,
  PortMapping,
  RegisterTaskDefinitionRequest,
  RegisterTaskDefinitionResult,
  RepositoryCredentials,
  Secret,
  SystemControl,
  TaskDefinitionPlacementConstraint,
  Tmpfs,
  Ulimit,
  Volume,
  VolumeFrom
}
import com.google.common.base.Optional
import com.google.common.collect.ImmutableList
import io.digdag.client.config.{Config, ConfigKey}
import io.digdag.spi.{ImmutableTaskResult, OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator

import scala.collection.JavaConverters._

class EcsTaskRegisterOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  protected val config: Config = params.getNested("_command")

  protected def buildRegisterTaskDefinitionRequest(c: Config): RegisterTaskDefinitionRequest = {
    val req: RegisterTaskDefinitionRequest = new RegisterTaskDefinitionRequest()

    val containerDefinitions: Seq[ContainerDefinition] =
      c.parseList("container_definitions", classOf[Config]).asScala.map(configureContainerDefinition).map(_.get)
    val cpu: Optional[String] = c.getOptional("cpu", classOf[String])
    val executionRoleArn: Optional[String] = c.getOptional("execution_role_arn", classOf[String])
    val family: String = c.get("family", classOf[String])
    val ipcMode: Optional[String] = c.getOptional("ipc_mode", classOf[String])
    val memory: Optional[String] = c.getOptional("memory", classOf[String])
    val networkMode: Optional[String] = c.getOptional("network_mode", classOf[String])
    val pidMode: Optional[String] = c.getOptional("pid_mode", classOf[String])

    val placementConstraints: Seq[TaskDefinitionPlacementConstraint] =
      c.parseListOrGetEmpty("placement_constraints", classOf[Config]).asScala.map(configureTaskDefinitionPlacementConstraint).map(_.get)
    val requiresCompatibilities: Seq[String] = c.parseListOrGetEmpty("requires_compatibilities", classOf[String]).asScala // Valid Values: EC2 | FARGATE
    val taskRoleArn: Optional[String] = c.getOptional("task_role_arn", classOf[String])
    val volumes: Seq[Volume] = c.parseListOrGetEmpty("volumes", classOf[Config]).asScala.map(configureVolume).map(_.get)

    req.setContainerDefinitions(containerDefinitions.asJava)
    if (cpu.isPresent) req.setCpu(cpu.get)
    if (executionRoleArn.isPresent) req.setExecutionRoleArn(executionRoleArn.get)
    req.setFamily(family)
    if (ipcMode.isPresent) throw new UnsupportedOperationException("Currently aws-java-sdk does not support ipc_mode.")
    if (memory.isPresent) req.setMemory(memory.get)
    if (networkMode.isPresent) req.setNetworkMode(networkMode.get)
    if (pidMode.isPresent) throw new UnsupportedOperationException("Currently aws-java-sdk does not support pid_mode.")
    if (placementConstraints.nonEmpty) req.setPlacementConstraints(placementConstraints.asJava)
    if (requiresCompatibilities.nonEmpty) req.setRequiresCompatibilities(requiresCompatibilities.asJava)
    if (taskRoleArn.isPresent) req.setTaskRoleArn(taskRoleArn.get)
    if (volumes.nonEmpty) req.setVolumes(volumes.asJava)

    req
  }

  protected def configureContainerDefinition(c: Config): Optional[ContainerDefinition] = {
    if (c.isEmpty) return Optional.absent()

    val command: Seq[String] = c.parseListOrGetEmpty("command", classOf[String]).asScala
    val cpu: Optional[Int] = c.getOptional("cpu", classOf[Int])
    val disableNetworking: Optional[Boolean] = c.getOptional("disable_networking", classOf[Boolean])
    val dnsSearchDomains: Seq[String] = c.parseListOrGetEmpty("dns_search_domains", classOf[String]).asScala
    val dnsServers: Seq[String] = c.parseListOrGetEmpty("dns_servers", classOf[String]).asScala
    val dockerLabels: Map[String, String] = c.getMapOrEmpty("docker_labels", classOf[String], classOf[String]).asScala.toMap
    val dockerSecurityOptions: Seq[String] = c.parseListOrGetEmpty("docker_security_options", classOf[String]).asScala
    val entryPoint: Seq[String] = c.parseListOrGetEmpty("entry_point", classOf[String]).asScala
    val environments: Seq[KeyValuePair] = c
      .getMapOrEmpty("environments", classOf[String], classOf[String])
      .asScala
      .map { case (k: String, v: String) => new KeyValuePair().withName(k).withValue(v) }
      .toSeq // TODO: doc
    val essential: Optional[Boolean] = c.getOptional("essential", classOf[Boolean])
    val extraHosts: Seq[HostEntry] = c
      .getMapOrEmpty("extra_hosts", classOf[String], classOf[String])
      .asScala
      .map { case (host: String, ip: String) => new HostEntry().withHostname(host).withIpAddress(ip) }
      .toSeq // TODO: doc
    val healthCheck: Optional[HealthCheck] = configureHealthCheck(c.parseNestedOrGetEmpty("health_check"))
    val hostname: Optional[String] = c.getOptional("hostname", classOf[String])
    val image: Optional[String] = c.getOptional("image", classOf[String])
    val interactive: Optional[Boolean] = c.getOptional("interactive", classOf[Boolean])
    val links: Seq[String] = c.parseListOrGetEmpty("links", classOf[String]).asScala
    val linuxParameters: Optional[LinuxParameters] = configureLinuxParameters(c.parseNestedOrGetEmpty("linux_parameters"))
    val logConfiguration: Optional[LogConfiguration] = configureLogConfiguration(c.parseNestedOrGetEmpty("log_configuration"))
    val memory: Optional[Int] = c.getOptional("memory", classOf[Int])
    val memoryReservation: Optional[Int] = c.getOptional("memory_reservation", classOf[Int])
    val mountPoints: Seq[MountPoint] = c.parseListOrGetEmpty("mount_points", classOf[Config]).asScala.map(configureMountPoint).map(_.get)
    val name: Optional[String] = c.getOptional("name", classOf[String])
    val portMappings: Seq[PortMapping] = c.parseListOrGetEmpty("port_mappings", classOf[Config]).asScala.map(configurePortMapping).map(_.get)
    val privileged: Optional[Boolean] = c.getOptional("privileged", classOf[Boolean])
    val pseudoTerminal: Optional[Boolean] = c.getOptional("pseudo_terminal", classOf[Boolean])
    val readonlyRootFilesystem: Optional[Boolean] = c.getOptional("readonly_root_filesystem", classOf[Boolean])
    val repositoryCredentials: Optional[RepositoryCredentials] = configureRepositoryCredentials(c.parseNestedOrGetEmpty("repository_credentials"))
    val secrets: Seq[Secret] = c.parseListOrGetEmpty("secrets", classOf[Config]).asScala.map(configureSecrets).map(_.get)
    val systemControls: Seq[SystemControl] = c.parseListOrGetEmpty("system_controls", classOf[Config]).asScala.map(configureSystemControl).map(_.get)
    val ulimits: Seq[Ulimit] = c.parseListOrGetEmpty("ulimits", classOf[Config]).asScala.map(configureUlimit).map(_.get)
    val user: Optional[String] = c.getOptional("user", classOf[String])
    val volumesFrom: Seq[VolumeFrom] = c.parseListOrGetEmpty("volumes_from", classOf[Config]).asScala.map(configureVolumeFrom).map(_.get)
    val workingDirectory: Optional[String] = c.getOptional("working_directory", classOf[String])

    val cd: ContainerDefinition = new ContainerDefinition()
    cd.setCommand(command.asJava)
    if (cpu.isPresent) cd.setCpu(cpu.get)
    if (disableNetworking.isPresent) cd.setDisableNetworking(disableNetworking.get)
    if (dnsSearchDomains.nonEmpty) cd.setDnsSearchDomains(dnsSearchDomains.asJava)
    if (dnsServers.nonEmpty) cd.setDnsServers(dnsServers.asJava)
    if (dockerLabels.nonEmpty) cd.setDockerLabels(dockerLabels.asJava)
    if (dockerSecurityOptions.nonEmpty) cd.setDockerSecurityOptions(dockerSecurityOptions.asJava)
    if (entryPoint.nonEmpty) cd.setEntryPoint(entryPoint.asJava)
    if (environments.nonEmpty) cd.setEnvironment(environments.asJava) // TODO: merge params?
    if (essential.isPresent) cd.setEssential(essential.get)
    if (extraHosts.nonEmpty) cd.setExtraHosts(extraHosts.asJava)
    if (healthCheck.isPresent) cd.setHealthCheck(healthCheck.get)
    if (hostname.isPresent) cd.setHostname(hostname.get)
    if (image.isPresent) cd.setImage(image.get)
    if (interactive.isPresent) cd.setInteractive(interactive.get)
    if (links.nonEmpty) cd.setLinks(links.asJava)
    if (linuxParameters.isPresent) cd.setLinuxParameters(linuxParameters.get)
    if (logConfiguration.isPresent) cd.setLogConfiguration(logConfiguration.get)
    if (memory.isPresent) cd.setMemory(memory.get)
    if (memoryReservation.isPresent) cd.setMemoryReservation(memoryReservation.get)
    if (mountPoints.nonEmpty) cd.setMountPoints(mountPoints.asJava)
    if (name.isPresent) cd.setName(name.get)
    if (portMappings.nonEmpty) cd.setPortMappings(portMappings.asJava)
    if (privileged.isPresent) cd.setPrivileged(privileged.get)
    if (pseudoTerminal.isPresent) cd.setPseudoTerminal(pseudoTerminal.get)
    if (readonlyRootFilesystem.isPresent) cd.setReadonlyRootFilesystem(readonlyRootFilesystem.get)
    if (repositoryCredentials.isPresent) cd.setRepositoryCredentials(repositoryCredentials.get)
    if (secrets.nonEmpty) cd.setSecrets(secrets.asJava)
    if (systemControls.nonEmpty) cd.setSystemControls(systemControls.asJava)
    if (ulimits.nonEmpty) cd.setUlimits(ulimits.asJava)
    if (user.isPresent) cd.setUser(user.get)
    if (volumesFrom.nonEmpty) cd.setVolumesFrom(volumesFrom.asJava)
    if (workingDirectory.isPresent) cd.setWorkingDirectory(workingDirectory.get)

    Optional.of(cd)
  }

  protected def configureHealthCheck(c: Config): Optional[HealthCheck] = {
    if (c.isEmpty) return Optional.absent()

    val command: Seq[String] = params.parseList("command", classOf[String]).asScala
    val interval: Optional[Int] = params.getOptional("interval", classOf[Int])
    val retries: Optional[Int] = params.getOptional("retries", classOf[Int])
    val startPeriod: Optional[Int] = params.getOptional("start_period", classOf[Int])
    val timeout: Optional[Int] = params.getOptional("timeout", classOf[Int])

    val hc: HealthCheck = new HealthCheck()
    hc.setCommand(command.asJava)
    if (interval.isPresent) hc.setInterval(interval.get)
    if (retries.isPresent) hc.setRetries(retries.get)
    if (startPeriod.isPresent) hc.setStartPeriod(startPeriod.get)
    if (timeout.isPresent) hc.setTimeout(timeout.get)

    Optional.of(hc)
  }

  protected def configureLinuxParameters(c: Config): Optional[LinuxParameters] = {
    if (c.isEmpty) return Optional.absent()

    val capabilities: Optional[KernelCapabilities] = configureKernelCapabilities(c.parseNestedOrGetEmpty("capabilities"))
    val devices: Seq[Device] = c.parseListOrGetEmpty("devices", classOf[Config]).asScala.map(configureDevice).map(_.get)
    val initProcessEnabled: Optional[Boolean] = c.getOptional("init_process_enabled", classOf[Boolean])
    val sharedMemorySize: Optional[Int] = c.getOptional("shared_memory_size", classOf[Int])
    val tmpfs: Seq[Tmpfs] = c.parseListOrGetEmpty("tmpfs", classOf[Config]).asScala.map(configureTmpfs).map(_.get)

    val lp: LinuxParameters = new LinuxParameters()
    if (capabilities.isPresent) lp.setCapabilities(capabilities.get)
    if (devices.nonEmpty) lp.setDevices(devices.asJava)
    if (initProcessEnabled.isPresent) lp.setInitProcessEnabled(initProcessEnabled.get)
    if (sharedMemorySize.isPresent) lp.setSharedMemorySize(sharedMemorySize.get)
    if (tmpfs.nonEmpty) lp.setTmpfs(tmpfs.asJava)

    Optional.of(lp)
  }

  protected def configureKernelCapabilities(c: Config): Optional[KernelCapabilities] = {
    if (c.isEmpty) return Optional.absent()

    val add: Seq[String] = c.parseListOrGetEmpty("add", classOf[String]).asScala
    val drop: Seq[String] = c.parseListOrGetEmpty("drop", classOf[String]).asScala

    val kc: KernelCapabilities = new KernelCapabilities()
    if (add.nonEmpty) kc.setAdd(add.asJava)
    if (drop.nonEmpty) kc.setDrop(drop.asJava)

    Optional.of(kc)
  }

  protected def configureDevice(c: Config): Optional[Device] = {
    if (c.isEmpty) return Optional.absent()

    val containerPath: Optional[String] = c.getOptional("container_path", classOf[String])
    val hostPath: String = c.get("host_path", classOf[String])
    val permissions: Seq[String] = c.parseListOrGetEmpty("permissions", classOf[String]).asScala

    val d: Device = new Device()
    if (containerPath.isPresent) d.setContainerPath(containerPath.get)
    d.setHostPath(hostPath)
    if (permissions.nonEmpty) d.setPermissions(permissions.asJava)

    Optional.of(d)
  }

  protected def configureTmpfs(c: Config): Optional[Tmpfs] = {
    if (c.isEmpty) return Optional.absent()

    val containerPath: String = c.get("container_path", classOf[String])
    val mountOptions: Seq[String] = c.parseListOrGetEmpty("mount_options", classOf[String]).asScala
    val size: Int = c.get("size", classOf[Int])

    val tmpfs: Tmpfs = new Tmpfs()
    tmpfs.setContainerPath(containerPath)
    if (mountOptions.nonEmpty) tmpfs.setMountOptions(mountOptions.asJava)
    tmpfs.setSize(size)

    Optional.of(tmpfs)
  }

  protected def configureLogConfiguration(c: Config): Optional[LogConfiguration] = {
    if (c.isEmpty) return Optional.absent()

    val logDriver: String = c.get("log_driver", classOf[String]) // Valid Values: json-file | syslog | journald | gelf | fluentd | awslogs | splunk
    val options: Map[String, String] = c.getMapOrEmpty("options", classOf[String], classOf[String]).asScala.toMap

    val lc: LogConfiguration = new LogConfiguration()
    lc.setLogDriver(logDriver)
    if (options.nonEmpty) lc.setOptions(options.asJava)

    Optional.of(lc)
  }

  protected def configureMountPoint(c: Config): Optional[MountPoint] = {
    if (c.isEmpty) return Optional.absent()

    val containerPath: Optional[String] = c.getOptional("container_path", classOf[String])
    val readOnly: Optional[Boolean] = c.getOptional("read_only", classOf[Boolean])
    val sourceVolume: Optional[String] = c.getOptional("source_volume", classOf[String])

    val mp: MountPoint = new MountPoint()
    if (containerPath.isPresent) mp.setContainerPath(containerPath.get)
    if (readOnly.isPresent) mp.setReadOnly(readOnly.get)
    if (sourceVolume.isPresent) mp.setSourceVolume(sourceVolume.get)

    Optional.of(mp)
  }

  protected def configurePortMapping(c: Config): Optional[PortMapping] = {
    if (c.isEmpty) return Optional.absent()

    val containerPort: Optional[Int] = c.getOptional("container_port", classOf[Int])
    val hostPort: Optional[Int] = c.getOptional("host_port", classOf[Int])
    val protocol: Optional[String] = c.getOptional("protocol", classOf[String])

    val pm: PortMapping = new PortMapping()
    if (containerPort.isPresent) pm.setContainerPort(containerPort.get)
    if (hostPort.isPresent) pm.setHostPort(hostPort.get)
    if (protocol.isPresent) pm.setProtocol(protocol.get)

    Optional.of(pm)
  }

  protected def configureRepositoryCredentials(c: Config): Optional[RepositoryCredentials] = {
    if (c.isEmpty) return Optional.absent()

    val credentialsParameter: String = c.get("credentials_parameter", classOf[String])

    val rc: RepositoryCredentials = new RepositoryCredentials()
    rc.setCredentialsParameter(credentialsParameter)

    Optional.of(rc)
  }

  protected def configureSecrets(c: Config): Optional[Secret] = {
    if (c.isEmpty) return Optional.absent()

    val name: String = c.get("name", classOf[String])
    val valueFrom: String = c.get("value_from", classOf[String])

    val s: Secret = new Secret()
    s.setName(name)
    s.setValueFrom(valueFrom)

    Optional.of(s)
  }

  protected def configureSystemControl(c: Config): Optional[SystemControl] = {
    if (c.isEmpty) return Optional.absent()

    val namespace: Optional[String] = c.getOptional("namespace", classOf[String])
    val value: Optional[String] = c.getOptional("value", classOf[String])

    val sc: SystemControl = new SystemControl()
    if (namespace.isPresent) sc.setNamespace(namespace.get)
    if (value.isPresent) sc.setValue(value.get)

    Optional.of(sc)
  }

  protected def configureUlimit(c: Config): Optional[Ulimit] = {
    if (c.isEmpty) return Optional.absent()

    val hardLimit: Int = c.get("hard_limit", classOf[Int])
    val name: String = c.get("name", classOf[String])
    val softLimit: Int = c.get("soft_limit", classOf[Int])

    val u: Ulimit = new Ulimit()
    u.setHardLimit(hardLimit)
    u.setName(name)
    u.setSoftLimit(softLimit)

    Optional.of(u)
  }

  protected def configureVolumeFrom(c: Config): Optional[VolumeFrom] = {
    if (c.isEmpty) return Optional.absent()

    val readOnly: Optional[Boolean] = c.getOptional("read_only", classOf[Boolean])
    val sourceContainer: Optional[String] = c.getOptional("source_container", classOf[String])

    val vf: VolumeFrom = new VolumeFrom()
    if (readOnly.isPresent) vf.setReadOnly(readOnly.get)
    if (sourceContainer.isPresent) vf.setSourceContainer(sourceContainer.get)

    Optional.of(vf)
  }

  protected def configureTaskDefinitionPlacementConstraint(c: Config): Optional[TaskDefinitionPlacementConstraint] = {
    if (c.isEmpty) return Optional.absent()

    val expression: Optional[String] = c.getOptional("expression", classOf[String])
    val `type`: Optional[String] = c.getOptional("type", classOf[String])

    val tdpc: TaskDefinitionPlacementConstraint = new TaskDefinitionPlacementConstraint()
    if (expression.isPresent) tdpc.setExpression(expression.get)
    if (`type`.isPresent) tdpc.setType(`type`.get)

    Optional.of(tdpc)
  }

  protected def configureVolume(c: Config): Optional[Volume] = {
    if (c.isEmpty) return Optional.absent()

    val dockerVolumeConfiguration: Optional[DockerVolumeConfiguration] = configureDockerVolumeConfiguration(
      c.parseNestedOrGetEmpty("docker_volume_configuration")
    )
    val host: Optional[HostVolumeProperties] = configureHostVolumeProperties(c.parseNestedOrGetEmpty("host"))
    val name: Optional[String] = c.getOptional("name", classOf[String])

    val v: Volume = new Volume()
    if (dockerVolumeConfiguration.isPresent) v.setDockerVolumeConfiguration(dockerVolumeConfiguration.get)
    if (host.isPresent) v.setHost(host.get)
    if (name.isPresent) v.setName(name.get)

    Optional.of(v)
  }

  protected def configureDockerVolumeConfiguration(c: Config): Optional[DockerVolumeConfiguration] = {
    if (c.isEmpty) return Optional.absent()

    val autoprovision: Optional[Boolean] = c.getOptional("autoprovision", classOf[Boolean])
    val driver: Optional[String] = c.getOptional("driver", classOf[String])
    val driverOpts: Map[String, String] = c.getMapOrEmpty("driver_opts", classOf[String], classOf[String]).asScala.toMap
    val labels: Map[String, String] = c.getMapOrEmpty("labels", classOf[String], classOf[String]).asScala.toMap
    val scope: Optional[String] = c.getOptional("scope", classOf[String])

    val dvc: DockerVolumeConfiguration = new DockerVolumeConfiguration()
    if (autoprovision.isPresent) dvc.setAutoprovision(autoprovision.get)
    if (driver.isPresent) dvc.setDriver(driver.get)
    if (driverOpts.nonEmpty) dvc.setDriverOpts(driverOpts.asJava)
    if (labels.nonEmpty) dvc.setLabels(labels.asJava)
    if (scope.isPresent) dvc.setScope(scope.get)

    Optional.of(dvc)
  }

  protected def configureHostVolumeProperties(c: Config): Optional[HostVolumeProperties] = {
    if (c.isEmpty) return Optional.absent()

    val sourcePath: Optional[String] = c.getOptional("source_path", classOf[String])

    val hvp: HostVolumeProperties = new HostVolumeProperties()
    if (sourcePath.isPresent) hvp.setSourcePath(sourcePath.get)

    Optional.of(hvp)
  }

  override def runTask(): TaskResult = {
    val req: RegisterTaskDefinitionRequest = buildRegisterTaskDefinitionRequest(config)
    logger.debug(req.toString)
    val result: RegisterTaskDefinitionResult = aws.withEcs(_.registerTaskDefinition(req))
    logger.debug(result.toString)

    logger.info(s"Registered: ${result.getTaskDefinition.getTaskDefinitionArn}")

    val paramsToStore = cf.create()
    val last_ecs_task_register: Config = paramsToStore.getNestedOrSetEmpty("last_ecs_task_register")
    last_ecs_task_register.set("task_definition_arn", result.getTaskDefinition.getTaskDefinitionArn)
    last_ecs_task_register.set("family", result.getTaskDefinition.getFamily)
    last_ecs_task_register.set("revision", result.getTaskDefinition.getRevision)

    val builder: ImmutableTaskResult.Builder = TaskResult.defaultBuilder(cf)
    builder.resetStoreParams(ImmutableList.of(ConfigKey.of("last_ecs_task_register")))
    builder.storeParams(paramsToStore)

    builder.build()
  }

}
