@PLUGIN@ resume-copy
====================

NAME
----
@PLUGIN@ resume-copy - Resumes project copy

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ resume-copy \
  [--quiet] \
  <NAME>
```

DESCRIPTION
-----------
Resumes project copy.

ACCESS
------
Caller must be a member of a group that is granted the 'Copy Project'
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
Resume copy of the myProject project:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ resume-copy myProject
```
