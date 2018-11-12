package pro.civitaspo.digdag.plugin.ecs_task.command
import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.client.config.Config
import io.digdag.spi.{OperatorContext, PrivilegedVariables, TaskResult, TemplateEngine}
import pro.civitaspo.digdag.plugin.ecs_task.AbstractEcsTaskOperator
import pro.civitaspo.digdag.plugin.ecs_task.aws.AmazonS3UriWrapper
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.collection.JavaConverters._
import scala.util.Random

abstract class AbstractEcsTaskCommandOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends AbstractEcsTaskOperator(operatorName, context, systemConfig, templateEngine) {

  protected val mainScriptName: String

  private lazy val tmpStorageConfig: Config = {
    if (params.has("workspace_s3_uri_prefix") && !params.has("tmp_storage")) {
      logger.warn("[Deprecated] Use `tmp_storage` instead of `workspace_s3_uri_prefix`")
      buildTmpStorageConfigFromWorkspaceS3UriPrefix()
    }
    else {
      if (params.has("workspace_s3_uri_prefix")) logger.info("Use `tmp_storage`, not `workspace_s3_uri_prefix`")
      params.getNested("tmp_storage")
    }
  }

  @deprecated
  private def buildTmpStorageConfigFromWorkspaceS3UriPrefix(): Config = {
    cf.create()
      .set("type", "s3")
      .set("uri", params.get("workspace_s3_uri_prefix", classOf[String]))
  }

  private def buildTmpStorage(): TmpStorage = {
    val storageType: String = tmpStorageConfig.get("type", classOf[String])
    storageType match {
      case "s3" => buildS3TmpStorage()
      case _ => throw new UnsupportedOperationException("tmp_storage.type supports only s3")
    }
  }

  private def buildS3TmpStorage(): S3TmpStorage = {
    val uriString: String = tmpStorageConfig.get("uri", classOf[String])
    val random: String = Random.alphanumeric.take(10).mkString
    val uri: AmazonS3URI =
      if (uriString.endsWith("/")) AmazonS3UriWrapper(s"$uriString$operatorName.$sessionUuid.$random")
      else AmazonS3UriWrapper(s"$uriString/$operatorName.$sessionUuid.$random")

    S3TmpStorage(location = uri, aws = aws, workspace = workspace, logger = logger)
  }

  protected def collectEnvironments(): Map[String, String] = {
    val vars: PrivilegedVariables = context.getPrivilegedVariables
    vars.getKeys.asScala.foldLeft(Map.empty[String, String]) { (env, key) =>
      env ++ Map(key -> vars.get(key))
    }
  }

  protected def createCommandRunner(tmpStorage: TmpStorage): EcsTaskCommandRunner = {
    EcsTaskCommandRunner(
      tmpStorage = tmpStorage,
      mainScript = mainScriptName,
      params = params,
      environments = collectEnvironments(),
      awsConf = aws.conf,
      logger = logger
    )
  }

  protected def prepare(tmpStorage: TmpStorage): Unit

  def runTask(): TaskResult = {
    TryWithResource(buildTmpStorage()) { tmpStorage: TmpStorage =>
      prepare(tmpStorage)
      createCommandRunner(tmpStorage).run()
    }
  }

}
