0.0.3.pre (2018-10-29)
======================

* [Breaking Change] Do not use enum parameter directory because the enums require upper camel case ( `ecs_task.{py,register,run}>` operator)
* [Enhancement] Rename the configuration key: `additional_containers` to `sidecars` ( `ecs_task.py>` operator)
* [Breaking Change/Enhancement] Rename the configuration key: `environment` to `environments` ( `ecs_task.{py,register,run}>` operator)
* [Enhancement] Rename the output key: `last_ecs_task_py` to `last_ecs_task_command` ( `ecs_task.py>` operator)
* [Fix] Fix example indents

0.0.2 (2018-10-29)
==================

* [Experimental] Implement ecs_task.py> operator. (No document yet)
* [Fix] Stop correctly after task run to shutdown TransferManager after processing.

0.0.1 (2018-10-23)
==================

* First Release
