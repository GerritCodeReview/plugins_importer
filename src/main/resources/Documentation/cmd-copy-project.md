@PLUGIN@ copy-project
=====================

NAME
----
@PLUGIN@ copy-project - Copies a project

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ copy-project \
  [--quiet] \
  <NAME> \
  <COPY>
```

DESCRIPTION
-----------
Copies a project.

ACCESS
------
Caller must be a member of a group that is granted the 'Copy'
capability (provided by this plugin) or the 'Administrate Server'
capability.

SCRIPTING
---------
This command is intended to be used in scripts.

OPTIONS
-------

`--quiet`
:	Suppress progress messages.

EXAMPLES
--------
Copy a project:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ copy-project myProject myCopy
```
