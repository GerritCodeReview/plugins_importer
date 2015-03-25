@PLUGIN@ complete-project
=========================

NAME
----
@PLUGIN@ complete-project - Completes a project import

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ complete-project <NAME>
```

DESCRIPTION
-----------
Completes a project import.

ACCESS
------
Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

SCRIPTING
---------
This command is intended to be used in scripts.

EXAMPLES
--------
Complete the import of the myProject project:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ complete-project myProject
```
