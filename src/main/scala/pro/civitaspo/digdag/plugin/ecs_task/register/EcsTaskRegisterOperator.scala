package pro.civitaspo.digdag.plugin.ecs_task.register
import com.amazonaws.services.ecs.model._
import com.google.common.collect.ImmutableList
import io.digdag.client.config.{Config, ConfigKey}
import io.digdag.spi.{ImmutableTaskResult, OperatorContext, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.util.DigdagConfig

import scala.collection.JavaConverters._

class EcsTaskRegisterOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  protected val config: DigdagConfig = DigdagConfig(params).getNested("_command")

  protected def buildRegisterTaskDefinitionRequest(c: DigdagConfig): RegisterTaskDefinitionRequest = {
    val req: RegisterTaskDefinitionRequest = new RegisterTaskDefinitionRequest()

    req.setFamily(c.get("family"))

    c.parseNestedOptSeq("container_definitions").map(_.map(configureContainerDefinition).asJava).foreach(req.setContainerDefinitions)
    c.getOpt("cpu").foreach(req.setCpu)
    c.getOpt("execution_role_arn").foreach(req.setExecutionRoleArn)
    c.getOpt("ipc_mode").foreach(throw new UnsupportedOperationException("Currently aws-java-sdk does not support ipc_mode."))
    c.getOpt("memory").foreach(req.setMemory)
    c.getOpt[String]("network_mode").foreach(req.setNetworkMode)
    c.getOpt("pid_mode").foreach(throw new UnsupportedOperationException("Currently aws-java-sdk does not support ipc_mode."))
    c.getOpt("task_role_arn").foreach(req.setTaskRoleArn)
    c.parseNestedOptSeq("placement_constraints")
      .map(_.map(configureTaskDefinitionPlacementConstraint).asJava)
      .foreach(req.setPlacementConstraints)
    c.parseSeqOpt[String]("requires_compatibilities")
      .map(_.asJava)
      .foreach(req.setRequiresCompatibilities)
    c.getMapOpt[String, String]("tags")
      .map(_.map(tagMap => new Tag().withKey(tagMap._1).withValue(tagMap._2)).toSeq.asJava)
      .foreach(req.setTags)
    c.parseNestedOptSeq("volumes")
      .map(_.map(configureVolume).asJava)
      .foreach(req.setVolumes)

    req
  }

  protected def configureContainerDefinition(c: DigdagConfig): ContainerDefinition = {

    val cd: ContainerDefinition = new ContainerDefinition()
    cd.setCommand(c.parseSeq("command").asJava)
    c.getOpt("cpu").foreach(cd.setCpu)
    c.getOpt("disable_networking").foreach(cd.setDisableNetworking)
    c.parseSeqOpt[String]("dns_search_domains").map(_.asJava).foreach(cd.setDnsSearchDomains)
    c.parseSeqOpt[String]("dns_servers").map(_.asJava).foreach(cd.setDnsServers)
    c.getMapOpt[String, String]("docker_labels").map(_.asJava).foreach(cd.setDockerLabels)
    c.parseSeqOpt[String]("docker_security_options").map(_.asJava).foreach(cd.setDockerSecurityOptions)
    c.parseSeqOpt[String]("entry_point").map(_.asJava).foreach(cd.setEntryPoint)
    c.getMapOpt[String, String]("environments")
      .map(_.map(envMap => new KeyValuePair().withName(envMap._1).withValue(envMap._2)).toSeq.asJava)
      .foreach(cd.setEnvironment) // TODO: merge params?
    c.getOpt("essential").foreach(cd.setEssential)
    c.getMapOpt[String, String]("extra_hosts")
      .map(_.map(hostMap => new HostEntry().withHostname(hostMap._1).withIpAddress(hostMap._2)).toSeq.asJava)
      .foreach(cd.setExtraHosts)
    c.parseNestedOpt("health_check").map(configureHealthCheck).foreach(cd.setHealthCheck)
    c.getOpt("hostname").foreach(cd.setHostname)
    c.getOpt("image").foreach(cd.setImage)
    c.getOpt("interactive").foreach(cd.setInteractive)
    c.parseSeqOpt[String]("links").map(_.asJava).foreach(cd.setLinks)
    c.parseNestedOpt("linux_parameters").map(configureLinuxParameters).foreach(cd.setLinuxParameters)
    c.parseNestedOpt("log_configuration").map(configureLogConfiguration).foreach(cd.setLogConfiguration)
    c.getOpt("memory").foreach(cd.setMemory)
    c.getOpt("memory_reservation").foreach(cd.setMemoryReservation)
    c.parseNestedOptSeq("mount_points").map(_.map(configureMountPoint).asJava).foreach(cd.setMountPoints)
    c.getOpt("name").foreach(cd.setName)
    c.parseNestedOptSeq("port_mappings").map(_.map(configurePortMapping).asJava).foreach(cd.setPortMappings)
    c.getOpt("privileged").foreach(cd.setPrivileged)
    c.getOpt("pseudo_terminal").foreach(cd.setPseudoTerminal)
    c.getOpt("readonly_root_filesystem").foreach(cd.setReadonlyRootFilesystem)
    c.parseNestedOpt("repository_credentials").map(configureRepositoryCredentials).foreach(cd.setRepositoryCredentials)
    c.parseNestedOptSeq("secrets").map(_.map(configureSecrets).asJava).foreach(cd.setSecrets)
    c.parseNestedOptSeq("system_controls").map(_.map(configureSystemControl).asJava).foreach(cd.setSystemControls)
    c.parseNestedOptSeq("ulimits").map(_.map(configureUlimit).asJava).foreach(cd.setUlimits)
    c.getOpt("user").foreach(cd.setUser)
    c.parseNestedOptSeq("ulimits").map(_.map(configureVolumeFrom).asJava).foreach(cd.setVolumesFrom)
    c.getOpt("working_directory").foreach(cd.setWorkingDirectory)

    cd
  }

  protected def configureHealthCheck(c: DigdagConfig): HealthCheck = {
    val hc: HealthCheck = new HealthCheck()
    hc.setCommand(c.parseSeq("command").asJava)
    c.getOpt("interval").foreach(hc.setInterval)
    c.getOpt("retries").foreach(hc.setRetries)
    c.getOpt("start_period").foreach(hc.setStartPeriod)
    c.getOpt("timeout").foreach(hc.setTimeout)
    hc
  }

  protected def configureLinuxParameters(c: DigdagConfig): LinuxParameters = {
    val lp: LinuxParameters = new LinuxParameters()
    c.parseNestedOpt("capabilities").map(configureKernelCapabilities).foreach(lp.setCapabilities)
    c.parseSeqOpt[DigdagConfig]("devices").map(_.map(configureDevice).asJava).foreach(lp.setDevices)
    c.getOpt("init_process_enabled").foreach(lp.setInitProcessEnabled)
    c.getOpt("shared_memory_size").foreach(lp.setSharedMemorySize)
    c.parseSeqOpt[DigdagConfig]("tmpfs").map(_.map(configureTmpfs).asJava).foreach(lp.setTmpfs)

    lp
  }

  protected def configureKernelCapabilities(c: DigdagConfig): KernelCapabilities = {
    val kc: KernelCapabilities = new KernelCapabilities()
    c.parseSeqOpt[String]("add").map(_.asJava).foreach(kc.setAdd)
    c.parseSeqOpt[String]("drop").map(_.asJava).foreach(kc.setDrop)
    kc
  }

  protected def configureDevice(c: DigdagConfig): Device = {
    val d: Device = new Device()
    d.setHostPath(c.get("host_path"))
    c.getOpt("container_path").foreach(d.setContainerPath)
    c.parseSeqOpt[String]("permissions").map(_.asJava).foreach(d.setPermissions)
    d
  }

  protected def configureTmpfs(c: DigdagConfig): Tmpfs = {
    val tmpfs: Tmpfs = new Tmpfs()
    tmpfs.setContainerPath(c.get("container_path"))
    tmpfs.setSize(c.get("size"))
    c.parseSeqOpt[String]("mount_options").map(_.asJava).foreach(tmpfs.setMountOptions)
    tmpfs
  }

  protected def configureLogConfiguration(c: DigdagConfig): LogConfiguration = {
    val lc: LogConfiguration = new LogConfiguration()
    lc.setLogDriver(c.get[String]("log_driver")) // Valid Values: json-file | syslog | journald | gelf | fluentd | awslogs | splunk
    c.getMapOpt[String, String]("options").map(_.asJava).foreach(lc.setOptions)
    lc
  }

  protected def configureMountPoint(c: DigdagConfig): MountPoint = {
    val mp: MountPoint = new MountPoint()
    c.getOpt("container_path").foreach(mp.setContainerPath)
    c.getOpt("read_only").foreach(mp.setReadOnly)
    c.getOpt("source_volume").foreach(mp.setSourceVolume)
    mp
  }

  protected def configurePortMapping(c: DigdagConfig): PortMapping = {
    val pm: PortMapping = new PortMapping()
    c.getOpt("container_port").foreach(pm.setContainerPort)
    c.getOpt("host_port").foreach(pm.setHostPort)
    c.getOpt[String]("protocol").foreach(pm.setProtocol)
    pm
  }

  protected def configureRepositoryCredentials(c: DigdagConfig): RepositoryCredentials = {
    val rc: RepositoryCredentials = new RepositoryCredentials()
    rc.setCredentialsParameter(c.get("credentials_parameter"))
    rc
  }

  protected def configureSecrets(c: DigdagConfig): Secret = {
    val s: Secret = new Secret()
    s.setName(c.get("name"))
    s.setValueFrom(c.get("value_from"))
    s
  }

  protected def configureSystemControl(c: DigdagConfig): SystemControl = {
    val sc: SystemControl = new SystemControl()
    c.getOpt("namespace").foreach(sc.setNamespace)
    c.getOpt("value").foreach(sc.setValue)
    sc
  }

  protected def configureUlimit(c: DigdagConfig): Ulimit = {
    val u: Ulimit = new Ulimit()
    u.setHardLimit(c.get("hard_limit"))
    u.setName(c.get[String]("name"))
    u.setSoftLimit(c.get("soft_limit"))
    u
  }

  protected def configureVolumeFrom(c: DigdagConfig): VolumeFrom = {
    val vf: VolumeFrom = new VolumeFrom()
    c.getOpt("read_only").foreach(vf.setReadOnly)
    c.getOpt("source_container").foreach(vf.setSourceContainer)
    vf
  }

  protected def configureTaskDefinitionPlacementConstraint(c: DigdagConfig): TaskDefinitionPlacementConstraint = {
    val tdpc = new TaskDefinitionPlacementConstraint()
    c.getOpt("expression").foreach(tdpc.setExpression)
    c.getOpt[String]("type").foreach(tdpc.setType)
    tdpc
  }

  protected def configureVolume(c: DigdagConfig): Volume = {
    val v: Volume = new Volume()
    c.parseNestedOpt("docker_volume_configuration").map(configureDockerVolumeConfiguration).foreach(v.setDockerVolumeConfiguration)
    c.parseNestedOpt("host").map(configureHostVolumeProperties).foreach(v.setHost)
    c.getOpt("name").foreach(v.setName)
    v
  }

  protected def configureDockerVolumeConfiguration(c: DigdagConfig): DockerVolumeConfiguration = {
    val dvc: DockerVolumeConfiguration = new DockerVolumeConfiguration()
    c.getOpt("autoprovision").foreach(dvc.setAutoprovision)
    c.getOpt("driver").foreach(dvc.setDriver)
    c.getMapOpt[String, String]("driver_opts").map(_.asJava).foreach(dvc.setDriverOpts)
    c.getMapOpt[String, String]("labels").map(_.asJava).foreach(dvc.setLabels)
    c.getOpt("scope").foreach(dvc.setScope)
    dvc
  }

  protected def configureHostVolumeProperties(c: DigdagConfig): HostVolumeProperties = {
    val hvp: HostVolumeProperties = new HostVolumeProperties()
    c.getOpt("source_path").foreach(hvp.setSourcePath)
    hvp
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
