@PLUGIN@ list-projects
======================

NAME
----
@PLUGIN@ list-projects - List project imports

SYNOPSIS
--------
```
ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ list-projects \
  [--verbose | -v] \
  <MATCH>
```

DESCRIPTION
-----------
Lists project imports.

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

`--verbose`
: Print detailed info for each project import

EXAMPLES
--------
List all project imports, names only:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ list-projects
```

List all project imports for projects contaning `plugin` in their name including
details for every import:

```
  $ ssh -p @SSH_PORT@ @SSH_HOST@ @PLUGIN@ list-projects -v plugin
```
