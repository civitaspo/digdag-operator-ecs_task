package pro.civitaspo.digdag.plugin.ecs_task.command
import com.amazonaws.services.s3.AmazonS3URI
import io.digdag.client.config.{Config, ConfigFactory}
import io.digdag.spi.{OperatorContext, PrivilegedVariables, TaskResult}
import io.digdag.util.Workspace
import org.slf4j.Logger
import pro.civitaspo.digdag.plugin.ecs_task.aws.{AmazonS3UriWrapper, Aws}
import pro.civitaspo.digdag.plugin.ecs_task.util.TryWithResource

import scala.collection.JavaConverters._
import scala.util.Random

trait EcsTaskCommandOperator {

  protected def getOperatorName: String
  protected def getContext: OperatorContext
  protected def getSessionUuid: String
  protected def getConfigFactory: ConfigFactory
  protected def getWorkspace: Workspace
  protected def getParams: Config
  protected def getAws: Aws
  protected def getLogger: Logger

  protected def getMainScriptName: String

  private lazy val tmpStorageConfig: Config = {
    if (getParams.has("workspace_s3_uri_prefix") && !getParams.has("tmp_storage")) {
      getLogger.warn("[Deprecated] Use `tmp_storage` instead of `workspace_s3_uri_prefix`")
      buildTmpStorageConfigFromWorkspaceS3UriPrefix()
    }
    else {
      if (getParams.has("workspace_s3_uri_prefix")) getLogger.info("Use `tmp_storage`, not `workspace_s3_uri_prefix`")
      getParams.getNested("tmp_storage")
    }
  }

  @deprecated
  private def buildTmpStorageConfigFromWorkspaceS3UriPrefix(): Config = {
    getConfigFactory
      .create()
      .set("type", "s3")
      .set("uri", getParams.get("workspace_s3_uri_prefix", classOf[String]))
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
      if (uriString.endsWith("/")) AmazonS3UriWrapper(s"$uriString$getOperatorName.$getSessionUuid.$random")
      else AmazonS3UriWrapper(s"$uriString/$getOperatorName.$getSessionUuid.$random")

    S3TmpStorage(location = uri, aws = getAws, workspace = getWorkspace, logger = getLogger)
  }

  protected def collectEnvironments(): Map[String, String] = {
    val vars: PrivilegedVariables = getContext.getPrivilegedVariables
    vars.getKeys.asScala.foldLeft(Map.empty[String, String]) { (env, key) => env ++ Map(key -> vars.get(key))
    }
  }

  protected def createCommandRunner(tmpStorage: TmpStorage): EcsTaskCommandRunner = {
    EcsTaskCommandRunner(
      tmpStorage = tmpStorage,
      mainScript = getMainScriptName,
      params = getParams,
      environments = collectEnvironments(),
      awsConf = getAws.conf,
      logger = getLogger
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
