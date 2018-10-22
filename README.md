# digdag-operator-ecs_task
[![Jitpack](https://jitpack.io/v/pro.civitaspo/digdag-operator-ecs_task.svg)](https://jitpack.io/#pro.civitaspo/digdag-operator-ecs_task) [![CircleCI](https://circleci.com/gh/civitaspo/digdag-operator-ecs_task.svg?style=shield)](https://circleci.com/gh/civitaspo/digdag-operator-ecs_task) [![Digdag](https://img.shields.io/badge/digdag-v0.9.27-brightgreen.svg)](https://github.com/treasure-data/digdag/releases/tag/v0.9.31)

digdag plugin for AWS ECS Task.

# Overview

- Plugin type: operator

# Usage

```yaml
TBD...
```

# Configuration

## Remarks

- type `DurationParam` is strings matched `\s*(?:(?<days>\d+)\s*d)?\s*(?:(?<hours>\d+)\s*h)?\s*(?:(?<minutes>\d+)\s*m)?\s*(?:(?<seconds>\d+)\s*s)?\s*`.
  - The strings is used as `java.time.Duration`.

## Common Configuration

### System Options

Define the below options on properties (which is indicated by `-c`, `--config`).

- **ecs_task.allow_auth_method_env**: Indicates whether users can use **auth_method** `"env"` (boolean, default: `false`)
- **ecs_task.allow_auth_method_instance**: Indicates whether users can use **auth_method** `"instance"` (boolean, default: `false`)
- **ecs_task.allow_auth_method_profile**: Indicates whether users can use **auth_method** `"profile"` (boolean, default: `false`)
- **ecs_task.allow_auth_method_properties**: Indicates whether users can use **auth_method** `"properties"` (boolean, default: `false`)
- **ecs_task.assume_role_timeout_duration**: Maximum duration which server administer allows when users assume **role_arn**. (`DurationParam`, default: `1h`)

### Secrets

- **ecs_task.access_key_id**: The AWS Access Key ID (optional)
- **ecs_task.secret_access_key**: The AWS Secret Access Key (optional)
- **ecs_task.session_token**: The AWS session token. This is used only **auth_method** is `"session"` (optional)
- **ecs_task.role_arn**: The AWS Role to assume. (optional)
- **ecs_task.role_session_name**: The AWS Role Session Name when assuming the role. (default: `digdag-ecs_task-${session_uuid}`)
- **ecs_task.http_proxy.host**: proxy host (required if **use_http_proxy** is `true`)
- **ecs_task.http_proxy.port** proxy port (optional)
- **ecs_task.http_proxy.scheme** `"https"` or `"http"` (default: `"https"`)
- **ecs_task.http_proxy.user** proxy user (optional)
- **ecs_task.http_proxy.password**: http proxy password (optional)

### Options

- **auth_method**: name of mechanism to authenticate requests (`"basic"`, `"env"`, `"instance"`, `"profile"`, `"properties"`, `"anonymous"`, or `"session"`. default: `"basic"`)
  - `"basic"`: uses access_key_id and secret_access_key to authenticate.
  - `"env"`: uses AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY) and AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY) environment variables.
  - `"instance"`: uses EC2 instance profile.
  - `"profile"`: uses credentials written in a file. Format of the file is as following, where `[...]` is a name of profile.
    - **profile_file**: path to a profiles file. (string, default: given by `AWS_CREDENTIAL_PROFILES_FILE` environment varialbe, or ~/.aws/credentials).
    - **profile_name**: name of a profile. (string, default: `"default"`)
  - `"properties"`: uses aws.accessKeyId and aws.secretKey Java system properties.
  - `"anonymous"`: uses anonymous access. This auth method can access only public files.
  - `"session"`: uses temporary-generated access_key_id, secret_access_key and session_token.
- **use_http_proxy**: Indicate whether using when accessing AWS via http proxy. (boolean, default: `false`)
- **region**: The AWS region. (string, optional)
- **endpoint**: The AWS Service endpoint. (string, optional)

## Configuration for `ecs_task.register>` operator

- **ecs_task.register>**: The configuration is the same as the snake-cased [RegisterTaskDefinition API](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_RegisterTaskDefinition.html) (map, required)

## Configuration for `ecs_task.run>` operator

The configuration is the same as the snake-cased [RunTask API](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_RunTask.html).

In addition, the below configurations exist.

- **def**: The definition for the task. The configuration is the same as `ecs_task.register>`'s one. (map, optional)
  - **NOTE**: **task_definition** is required on the [RunTask API Doc](https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_RunTask.html), but it is not required if the **def** is defined.
- **result_s3_uri_prefix**: The S3 uri prefix for the task result. (string, optional)
  - **NOTE**: This configuration is used by `ecs_task.result>` operator, so the result content must follow the rule.
- **timeout**: Timeout duration for the task. (`DurationParam`, default: `15m`)

## Configuration for `ecs_task.wait>` operator

- **cluster**: The short name or full ARN of the cluster that hosts the tasks. (string, required)
- **tasks**: A list of up to 100 task IDs or full ARN entries. (array of string, required)
- **timeout**: Timeout duration for the tasks. (`DurationParam`, default: `15m`)
- **condition**: The condition of tasks to wait. Available values are `"all"` or `"any"`. (string, default: `"all"`)
- **status**: The status of tasks to wait. Available values are `"PENDING"`, `"RUNNING"`, or `"STOPPED"` (string, default: `"STOPPED"`)
- **ignore_failure**: Ignore even if any tasks exit with the code except 0. (boolean, default: `false`) 

## Configuration for `ecs_task.result>` operator

- **s3_uri**: S3 URI that the result is stored. (string, required)
  - **NOTE**: The result content must follow the below rule.
    - the format is json.
    - the keys are `"subtask_config"`, `"export_params"`, `"store_params"`.
    - the values are string to object map.
      - the usage follows [Digdag Python API](https://docs.digdag.io/python_api.html), [Digdag Ruby API](https://docs.digdag.io/ruby_api.html). 

# Development

## Run an Example

### 1) build

```sh
./gradlew publish
```

Artifacts are build on local repos: `./build/repo`.

### 2) get your aws profile

```sh
aws configure
```

### 3) run an example

```sh
./example/run.sh
```

## (TODO) Run Tests

```sh
./gradlew test
```

# ChangeLog

[CHANGELOG.md](./CHANGELOG.md)

# License

[Apache License 2.0](./LICENSE.txt)

# Author

@civitaspo

