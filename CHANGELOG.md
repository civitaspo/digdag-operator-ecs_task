0.0.4 (2018-11-06)
==================

* [Experimental] Implement `ecs_task.embulk>` operator.
* [Enhancement] Write README for scripting operators.
* [Enhancement] Make family name more configuable for scripting operators.

0.0.3 (2018-10-30)
==================

* [Breaking Change] Do not use enum parameter directory because the enums require upper camel case ( `ecs_task.{py,register,run}>` operator)
* [Enhancement] Rename the configuration key: `additional_containers` to `sidecars` ( `ecs_task.py>` operator)
* [Breaking Change/Enhancement] Rename the configuration key: `environment` to `environments` ( `ecs_task.{py,register,run}>` operator)
* [Enhancement] Rename the output key: `last_ecs_task_py` to `last_ecs_task_command` ( `ecs_task.py>` operator)
* [Fix] Fix example indents
* [Fix] Avoid java.nio.charset.MalformedInputException: Input length = 1
* [Fix] Avoid com.amazonaws.services.ecs.model.ClientException: Family contains invalid characters when the default value is used.
* [Enhancement] Enable to parse json text in configuration
* [Enhancement] Get s3 content more simply
* [Fix] Use unique s3 workspace path
* [Fix] print error in runner.py

0.0.2 (2018-10-29)
==================

* [Experimental] Implement `ecs_task.py>` operator. (No document yet)
* [Fix] Stop correctly after task run to shutdown TransferManager after processing.

0.0.1 (2018-10-23)
==================

* First Release
