@PLUGIN@ project
================

NAME
----
@PLUGIN@ project - Imports a project

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ project \
  --from <URL> | -f <URL> \
  [--name <NAME>] \
  --user <USER> | -u <USER> \
  --pass - | <PASS> \
  [--parent <NAME>] \
  [--quiet] \
  <NAME>
```

DESCRIPTION
-----------
Imports a project.

ACCESS
------
Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

SCRIPTING
---------
This command is intended to be used in scripts.

OPTIONS
-------

`--from`
:	URL of the remote system from where the project should be imported.

`--name`
:	Name of the project in the source system.
	If not specified it is assumed to be the same name as in the target system.

`--pass`
:	Password of remote user.

`--user`
:	User on remote system.

`--parent`
:	Name of the parent project in the target system.
	The imported project will be created under this parent project.

`--quiet`
:	Suppress progress messages.

EXAMPLES
--------
Import a project:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ project --from https://some-gerrit-server:8080 \
    --pass myPassword --user myUser myProject
```
