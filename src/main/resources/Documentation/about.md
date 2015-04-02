The @PLUGIN@ plugin allows to import projects and groups from a remote
Gerrit server into the Gerrit server where the plugin is installed.

The imports are done online while both, source and target Gerrit
server, are running.

The user that does the import must be a member of a group that is
granted the 'Import' capability (provided by this plugin) or the
'Administrate Server' capability.

Importing also requires a user/password for the source Gerrit server.
This user must be able to see the entities that should imported.

The data for the import is retrieved by accessing the Gerrit REST API
on the source Gerrit server. Additionally project imports fetch from
the repositories on the source Gerrit server. The git operations are
done over the HTTP protocol.

Imports are done in several steps:

* do initial import
* [optional] resume import as many times as you want
* complete the import

Until an import is completed, the imported entities should not be
modified in the target Gerrit server, otherwise resuming the import may
fail or even override modifications done in the target Gerrit server
(e.g. change approvals will be overridden). But depending on what was
modified, it may also just work.

Imports are logged in 'review\_site/logs/import\_log' so that
administrators can see who imported when which project. Imports do also
send audit events.

<a id="project-import">
### Project Import

The import project functionality allows to import (move) a project from
one Gerrit server to another Gerrit server.

On project import the configured max object size on the target Gerrit
server is ignored.

<a id="project-import-process">
#### Process

A project import would be done in several steps:

* do the initial import of the project
* test on the target Gerrit that everything is okay
* inform the project team about the project move and disallow further
  modifications of the project in source Gerrit server (e.g. by
  permissions or by setting the project state to read-only)
* resume the project import to get all modifications which have been
  done after the initial import
* complete the import and if needed make project in the target Gerrit
  server writable
* inform the project team that they can now work on the project in the
  target Gerrit server
* reconfigure any third-party tools (such as Jenkins) to work against
  the project in the target Gerrit server
* [optionally] delete the project in the source Gerrit server using the
  [delete-project](https://gerrit.googlesource.com/plugins/delete-project/+doc/master/src/main/resources/Documentation/about.md)
  plugin

Doing an initial import first and resuming the import later has the
advantage that the downtime for the project team can be kept minimal.
The initial project import may take some time, but the resume should be
fast since it only needs to transfer the delta since the initial (last)
import.

<a id="project-import-preconditions">
#### Preconditions

Preconditions for a project import:

* The version of the source Gerrit server is 2.11 or newer.
* The parent project of the imported project must already exist in the
  target Gerrit server.
* User accounts must have a username set.
* User accounts must exist in the target Gerrit server unless auth type
  is 'LDAP', 'HTTP\_LDAP' or 'CLIENT\_SSL\_CERT\_LDAP'.

For auth type 'LDAP', 'HTTP\_LDAP' or 'CLIENT\_SSL\_CERT\_LDAP' missing
user accounts are automatically created. The public SSH keys of a user
are automatically retrieved from the source Gerrit server and added to
the new account in the target Gerrit server.

Gerrit internal users (e.g. service users) are never automatically
created but must be created in the target Gerrit server before the
import.

<a id="project-import-commands">
#### Commands

Importing a project can be done via

* [REST](rest-api-config.html#import-project)
* [SSH](cmd-project.html) and
* UI from menu 'Projects' > 'Import Project'

Resuming a project import can be done via

* [REST](rest-api-config.html#resume-project-import)
* [SSH](cmd-resume-project.html)
* UI from the list imports screen (menu 'Projects' > 'List Imports') and
* UI from the project info screen ('Resume Import...' action)

Completing the import can be done via

* [REST](rest-api-config.html#complete-project-import)
* [SSH](cmd-complete-project.html)
* UI from the list imports screen (menu 'Projects' > 'List Imports') and
* UI from the project info screen ('Complete Import' action)

When doing a project import the project in the target Gerrit server can
be created with a new name or under another parent project.

<a id="project-import-steps">
#### How the project import works

The project import is implemented in such a way that it replays the
actions that have been done in the source Gerrit server with preserving
the original timestamps.

A project import consists of the following steps:

* creation of the Git repository and project in the target Gerrit server
* fetch all refs from the source Gerrit server under the
  'refs/imports/' namespace
* create the refs for all branches and tags
* [optional] reparent project in the target Gerrit server
* replay all changes (changes in the target Gerrit server get new
  numeric ID's)
* import of groups for access rights on this project if they are
  missing in the target Gerrit server

Replaying a change is done by:

* replay all revisions (create the change refs and insert the patch sets)
* replay inline comments
* replay change messages
* add approvals (approvals for unknown labels are ignored)
* add hashtags (hashtags are only applied if the importing user has
  permissions to edit hashtags, e.g. if the
  [Edit Hashtags](../../../Documentation/access-control.html#category_edit_hashtags)
  global capability is assigned)
* add link to original change as a new change message

<a id="import-file">
#### Import File

At a point in time a project can be imported only by a single process.
To protect a running import from other processes the import creates an
import file
'review\_site/data/@PLUGIN@/\<target-project-name\>.$importstatus' and
locks this file. In the import file an
[ImportProjectInfo](rest-api-config.html#import-project-info) entity is
persisted that stores the input parameters and records the past
imports. The import file is kept after the import is done so that the
input parameters do not need to be specified again when the import is
resumed.

<a id="resume-project-import">
#### Resume Project Import

Once a project was imported, the project import can be resumed to
import modifications that happened in the source Gerrit server after
the initial/last import to the target Gerrit server has been done.

The resume of an import is only guaranteed to work if none of the
imported entities has been modified in the target Gerrit server,
otherwise resuming the import may fail or even override modifications
done in the target Gerrit server (e.g. change approvals will be
overridden). But depending on what was modified, it may also just work.

On resume changes that have the same last modified timestamp in the
source and target Gerrit server are skipped, unless the force option is
set.

The force option is useful if an import finished with warnings (in the
error log) and the import should be resumed after fixing the issues,
e.g.:

* approvals for unknown labels have been skipped and the labels have
  now been configured on the target Gerrit server
* hashtags couldn't be set due to missing permissions, but the
  permissions have been granted now

On resume approvals, hashtags and change topic are always reapplied.
This means that any modification of these properties in the target
Gerrit server is overridden if the import of a change is resumed.

<a id="complete-project-import">
#### Complete Project Import

Completing the project import deletes all refs under the
'refs/imports/' namespace. In addition the [import file](#import-file)
for the project is deleted. Afterwards it's not possible to resume the
project import anymore. Also the project doesn't appear in the list of
imported projects anymore.

<a id="project-copy">
### Project Copy

Project copy is a special case of project import, where a project from
the same Gerrit server is imported under a new name.

To be able to copy a project the user must be a member of a group that
is granted the 'Copy Project' capability (provided by this plugin) or
the 'Administrate Server' capability.

<a id="project-copy-commands">
#### Commands

Copying a project can be done via

* [REST](rest-api-projects.html#copy-project)
* [SSH](cmd-copy-project.html) and
* UI from the project info screen ('Copy...' action)

Resuming a project copy can be done via

* [REST](rest-api-projects.html#resume-copy-import)
* [SSH](cmd-resume-project.html)
* UI from the list imports screen (menu 'Projects' > 'List Imports') and
* UI from the project info screen ('Resume Copy...' action)

Completing the copy can be done via

* [REST](rest-api-config.html#complete-project-import)
* [SSH](cmd-complete-project.html)
* UI from the list imports screen (menu 'Projects' > 'List Imports') and
* UI from the project info screen ('Complete Copy' action)

When doing a project copy the project *cannot* be put under another
parent project. But you can reparent the project copy after the copy is
done.

<a id="project-rename">
### Project Rename

By doing a [project copy](#project-copy) and then using the
[delete-project](https://gerrit.googlesource.com/plugins/delete-project/+doc/master/src/main/resources/Documentation/about.md)
plugin to delete the source project, a project can be renamed.

<a id="group-import">
### Group Import

The import group functionality allows to import a Gerrit group from one
Gerrit server to another Gerrit server.

The flag whether a group is visible to all registered users is *not*
preserved on import, but the default that is configured for this option
on the target Gerrit server is applied to each imported group.

<a id="group-import-preconditions">
#### Preconditions

Preconditions for a group import:

* Member accounts must have a username set.
* Member accounts must exist in the target Gerrit server unless auth
  type is 'LDAP', 'HTTP\_LDAP' or 'CLIENT\_SSL\_CERT\_LDAP'.

For auth type 'LDAP', 'HTTP\_LDAP' or 'CLIENT\_SSL\_CERT\_LDAP' missing
member accounts are automatically created. The public SSH keys of a
member are automatically retrieved from the source Gerrit server and
added to the new account in the target Gerrit server.

Gerrit internal users (e.g. service users) are never automatically
created but must be created in the target Gerrit server before the
import.

Missing owner groups and missing included groups can be automatically
imported into the target Gerrit server.

<a id="group-import-commands">
#### Commands

Importing a group can be done via

* [REST](rest-api-config.html#import-group)
* [SSH](cmd-group.html) and
* UI from menu 'People' > 'Import Group'
