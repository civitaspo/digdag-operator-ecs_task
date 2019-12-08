package pro.civitaspo.digdag.plugin.ecs_task
import io.digdag.client.config.{Config, ConfigFactory}
import io.digdag.spi.{OperatorContext, SecretProvider, TemplateEngine}
import io.digdag.util.{BaseOperator, DurationParam}
import org.slf4j.{Logger, LoggerFactory}
import pro.civitaspo.digdag.plugin.ecs_task.aws.{Aws, AwsConf}

abstract class AbstractEcsTaskOperator(operatorName: String, context: OperatorContext, systemConfig: Config, templateEngine: TemplateEngine)
    extends BaseOperator(context) {

  protected val logger: Logger = LoggerFactory.getLogger(operatorName)
  protected val cf: ConfigFactory = request.getConfig.getFactory
  protected val params: Config = {
    val elems: Seq[String] = operatorName.split("\\.").toSeq
    elems.indices.foldLeft(request.getConfig) { (p: Config, idx: Int) =>
      p.mergeDefault((0 to idx).foldLeft(request.getConfig) { (nestedParam: Config, keyIdx: Int) =>
        nestedParam.getNestedOrGetEmpty(elems(keyIdx))
      })
    }
  }
  protected val secrets: SecretProvider = context.getSecrets.getSecrets("ecs_task")
  protected val sessionUuid: String = params.get("session_uuid", classOf[String])

  protected val aws: Aws = Aws(
    AwsConf(
      isAllowedAuthMethodEnv = systemConfig.get("ecs_task.allow_auth_method_env", classOf[Boolean], false),
      isAllowedAuthMethodInstance = systemConfig.get("ecs_task.allow_auth_method_instance", classOf[Boolean], false),
      isAllowedAuthMethodProfile = systemConfig.get("ecs_task.allow_auth_method_profile", classOf[Boolean], false),
      isAllowedAuthMethodProperties = systemConfig.get("ecs_task.allow_auth_method_properties", classOf[Boolean], false),
      assumeRoleTimeoutDuration = systemConfig.get("ecs_task.assume_role_timeout_duration", classOf[DurationParam], DurationParam.parse("1h")),
      accessKeyId = secrets.getSecretOptional("access_key_id"),
      secretAccessKey = secrets.getSecretOptional("secret_access_key"),
      sessionToken = secrets.getSecretOptional("session_token"),
      roleArn = secrets.getSecretOptional("role_arn"),
      roleSessionName = secrets.getSecretOptional("role_session_name").or(s"digdag-ecs_task-$sessionUuid"),
      httpProxy = secrets.getSecrets("http_proxy"),
      authMethod = params.get("auth_method", classOf[String], "basic"),
      profileName = params.get("profile_name", classOf[String], "default"),
      profileFile = params.getOptional("profile_file", classOf[String]),
      useHttpProxy = params.get("use_http_proxy", classOf[Boolean], false),
      region = params.getOptional("region", classOf[String]),
      endpoint = params.getOptional("endpoint", classOf[String])
    )
  )

}
