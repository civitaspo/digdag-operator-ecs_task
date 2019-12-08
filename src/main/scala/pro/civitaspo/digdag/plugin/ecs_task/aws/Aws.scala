package pro.civitaspo.digdag.plugin.ecs_task.aws


import com.amazonaws.{ClientConfiguration, Protocol}
import com.amazonaws.auth.{AnonymousAWSCredentials, AWSCredentials, AWSCredentialsProvider, AWSStaticCredentialsProvider, BasicAWSCredentials, BasicSessionCredentials, EC2ContainerCredentialsProviderWrapper, EnvironmentVariableCredentialsProvider, SystemPropertiesCredentialsProvider}
import com.amazonaws.auth.profile.{ProfileCredentialsProvider, ProfilesConfigFile}
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.{DefaultAwsRegionProviderChain, Regions}
import com.amazonaws.services.ecs.{AmazonECS, AmazonECSClientBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.amazonaws.services.s3.transfer.{TransferManager, TransferManagerBuilder}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.google.common.base.Optional
import io.digdag.client.config.ConfigException

import scala.util.Try


case class Aws(conf: AwsConf)
{

    def withS3[R](f: AmazonS3 => R): R =
    {
        val s3: AmazonS3 = buildService(AmazonS3ClientBuilder.standard())
        try f(s3)
        finally s3.shutdown()
    }

    def withTransferManager[R](f: TransferManager => R): R =
    {
        withS3 { s3 =>
            val xfer: TransferManager = TransferManagerBuilder.standard().withS3Client(s3).build()
            try f(xfer)
            finally xfer.shutdownNow(false)
        }
    }

    def withEcs[R](f: AmazonECS => R): R =
    {
        val ecs: AmazonECS = buildService(AmazonECSClientBuilder.standard())
        try f(ecs)
        finally ecs.shutdown()
    }

    private def buildService[S <: AwsClientBuilder[S, T], T](builder: AwsClientBuilder[S, T]): T =
    {
        configureBuilderEndpointConfiguration(builder)
            .withClientConfiguration(clientConfiguration)
            .withCredentials(credentialsProvider)
            .build()
    }

    private def configureBuilderEndpointConfiguration[S <: AwsClientBuilder[S, T], T](builder: AwsClientBuilder[S, T]): AwsClientBuilder[S, T] =
    {
        if (conf.region.isPresent && conf.endpoint.isPresent) {
            val ec = new EndpointConfiguration(conf.endpoint.get(), conf.region.get())
            builder.setEndpointConfiguration(ec)
        }
        else if (conf.region.isPresent && !conf.endpoint.isPresent) {
            builder.setRegion(conf.region.get())
        }
        else if (!conf.region.isPresent && conf.endpoint.isPresent) {
            val r = Try(new DefaultAwsRegionProviderChain().getRegion).getOrElse(Regions.DEFAULT_REGION.getName)
            val ec = new EndpointConfiguration(conf.endpoint.get(), r)
            builder.setEndpointConfiguration(ec)
        }
        builder
    }

    private def credentialsProvider: AWSCredentialsProvider =
    {
        if (!conf.roleArn.isPresent) return standardCredentialsProvider
        assumeRoleCredentialsProvider(standardCredentialsProvider)
    }

    private def standardCredentialsProvider: AWSCredentialsProvider =
    {
        conf.authMethod match {
            case "basic"      => basicAuthMethodAWSCredentialsProvider
            case "env"        => envAuthMethodAWSCredentialsProvider
            case "instance"   => instanceAuthMethodAWSCredentialsProvider
            case "profile"    => profileAuthMethodAWSCredentialsProvider
            case "properties" => propertiesAuthMethodAWSCredentialsProvider
            case "anonymous"  => anonymousAuthMethodAWSCredentialsProvider
            case "session"    => sessionAuthMethodAWSCredentialsProvider
            case _            =>
                throw new ConfigException(
                    s"""auth_method: "$conf.authMethod" is not supported. available `auth_method`s are "basic", "env", "instance", "profile", "properties", "anonymous", or "session"."""
                    )
        }
    }

    private def assumeRoleCredentialsProvider(credentialsProviderToAssumeRole: AWSCredentialsProvider): AWSCredentialsProvider =
    {
        // TODO: require EndpointConfiguration so on ?
        val sts = AWSSecurityTokenServiceClientBuilder
            .standard()
            .withClientConfiguration(clientConfiguration)
            .withCredentials(credentialsProviderToAssumeRole)
            .build()

        val role = sts.assumeRole(
            new AssumeRoleRequest()
                .withRoleArn(conf.roleArn.get())
                .withDurationSeconds(conf.assumeRoleTimeoutDuration.getDuration.getSeconds.toInt)
                .withRoleSessionName(conf.roleSessionName)
            )
        val credentials =
            new BasicSessionCredentials(role.getCredentials.getAccessKeyId, role.getCredentials.getSecretAccessKey, role.getCredentials.getSessionToken)
        new AWSStaticCredentialsProvider(credentials)
    }

    private def basicAuthMethodAWSCredentialsProvider: AWSCredentialsProvider =
    {
        if (!conf.accessKeyId.isPresent) throw new ConfigException(s"""`access_key_id` must be set when `auth_method` is "$conf.authMethod".""")
        if (!conf.secretAccessKey.isPresent) throw new ConfigException(s"""`secret_access_key` must be set when `auth_method` is "$conf.authMethod".""")
        val credentials: AWSCredentials = new BasicAWSCredentials(conf.accessKeyId.get(), conf.secretAccessKey.get())
        new AWSStaticCredentialsProvider(credentials)
    }

    private def envAuthMethodAWSCredentialsProvider: AWSCredentialsProvider =
    {
        if (!conf.isAllowedAuthMethodEnv) throw new ConfigException(s"""auth_method: "$conf.authMethod" is not allowed.""")
        new EnvironmentVariableCredentialsProvider
    }

    private def instanceAuthMethodAWSCredentialsProvider: AWSCredentialsProvider =
    {
        if (!conf.isAllowedAuthMethodInstance) throw new ConfigException(s"""auth_method: "$conf.authMethod" is not allowed.""")
        // NOTE: combination of InstanceProfileCredentialsProvider and ContainerCredentialsProvider
        new EC2ContainerCredentialsProviderWrapper
    }

    private def profileAuthMethodAWSCredentialsProvider: AWSCredentialsProvider =
    {
        if (!conf.isAllowedAuthMethodProfile) throw new ConfigException(s"""auth_method: "$conf.authMethod" is not allowed.""")
        if (!conf.profileFile.isPresent) return new ProfileCredentialsProvider(conf.profileName)
        val pf: ProfilesConfigFile = new ProfilesConfigFile(conf.profileFile.get())
        new ProfileCredentialsProvider(pf, conf.profileName)
    }

    private def propertiesAuthMethodAWSCredentialsProvider: AWSCredentialsProvider =
    {
        if (!conf.isAllowedAuthMethodProperties) throw new ConfigException(s"""auth_method: "$conf.authMethod" is not allowed.""")
        new SystemPropertiesCredentialsProvider()
    }

    private def anonymousAuthMethodAWSCredentialsProvider: AWSCredentialsProvider =
    {
        val credentials: AWSCredentials = new AnonymousAWSCredentials
        new AWSStaticCredentialsProvider(credentials)
    }

    private def sessionAuthMethodAWSCredentialsProvider: AWSCredentialsProvider =
    {
        if (!conf.accessKeyId.isPresent) throw new ConfigException(s"""`access_key_id` must be set when `auth_method` is "$conf.authMethod".""")
        if (!conf.secretAccessKey.isPresent) throw new ConfigException(s"""`secret_access_key` must be set when `auth_method` is "$conf.authMethod".""")
        if (!conf.sessionToken.isPresent) throw new ConfigException(s"""`session_token` must be set when `auth_method` is "$conf.authMethod".""")
        val credentials: AWSCredentials = new BasicSessionCredentials(conf.accessKeyId.get(), conf.secretAccessKey.get(), conf.sessionToken.get())
        new AWSStaticCredentialsProvider(credentials)
    }

    private def clientConfiguration: ClientConfiguration =
    {
        if (!conf.useHttpProxy) return new ClientConfiguration()

        val host: String = conf.httpProxy.getSecret("host")
        val port: Optional[String] = conf.httpProxy.getSecretOptional("port")
        val protocol: Protocol = conf.httpProxy.getSecretOptional("scheme").or("https") match {
            case "http"  => Protocol.HTTP
            case "https" => Protocol.HTTPS
            case _       => throw new ConfigException(s"""`athena.http_proxy.scheme` must be "http" or "https".""")
        }
        val user: Optional[String] = conf.httpProxy.getSecretOptional("user")
        val password: Optional[String] = conf.httpProxy.getSecretOptional("password")

        val cc = new ClientConfiguration()
            .withProxyHost(host)
            .withProtocol(protocol)

        if (port.isPresent) cc.setProxyPort(port.get().toInt)
        if (user.isPresent) cc.setProxyUsername(user.get())
        if (password.isPresent) cc.setProxyPassword(password.get())

        cc
    }

}
