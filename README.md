Importer - Gerrit Plugin to import projects
===========================================

The importer plugin allows to import projects from one Gerrit server
into another Gerrit server.

Projects can be imported while both, source and target Gerrit server,
are online. There is no downtime required.

A project import imports the git repository and all changes of the
project (including approvals and review comments). Historic timestamps
are preserved.

Project imports can be resumed. This means a project team can continue
to work in the source system while the import to the target system is
done. By resuming the import the project in the target system can be
updated with the missing delta.

The importer plugin can also be used to copy a project within one
Gerrit server, and in combination with the
[delete-project](https://gerrit.googlesource.com/plugins/delete-project/+doc/master/src/main/resources/Documentation/about.md)
plugin it can be used for project rename.
