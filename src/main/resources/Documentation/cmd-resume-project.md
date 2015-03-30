@PLUGIN@ resume-project
=======================

NAME
----
@PLUGIN@ resume-project - Resumes project import

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ resume-project \
  --user <USER> | -u <USER> \
  --pass - | <PASS> \
  [--force] \
  [--quiet] \
  <NAME>
```

DESCRIPTION
-----------
Resumes project import.

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

`--pass`
:	Password of remote user.

`--user`
:	User on remote system.

`--force`
:	Whether the resume should be done forcefully. On resume with force
	changes that have the same last modified timestamp in the source
	and target system are resumed, otherwise they will be skipped.

`--quiet`
:	Suppress progress messages.

EXAMPLES
--------
Resume import of the myProject project:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ resume-project \
    --pass myPassword --user myUser myProject
```
