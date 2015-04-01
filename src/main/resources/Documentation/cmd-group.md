@PLUGIN@ group
================

NAME
----
@PLUGIN@ group - Imports a group

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ group \
  --from <URL> | -f <URL> \
  --user <USER> | -u <USER> \
  --pass - | <PASS> \
  [--import-owner-group | -o] \
  [--import-included-groups | -i] \
  <NAME>
```

DESCRIPTION
-----------
Imports a group.

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
:	URL of the remote system from where the group should be imported.

`--pass`
:	Password of remote user.

`--user`
:	User on remote system.

`--import-owner-group`
:	Whether missing owner groups should be imported automatically.

`--import-included-groups`
:	Whether missing included groups should be imported automatically.

EXAMPLES
--------
Import a group:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ group --from https://some-gerrit-server:8080 \
    --pass myPassword --user myUser aGroupName
```
