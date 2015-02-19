@PLUGIN@ - /config/ REST API
============================

This page describes the REST endpoints that are added by the @PLUGIN@
plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

<a id="importer-endpoints"> Importer Endpoints
---------------------------------------------

### <a id="import-project"> Import Project
_POST /config/server/@PLUGIN@~project_

Imports a project.

The information about which project should be imported must be provided
in the request body as a [ProjectInput](#project-input) entity.

Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  POST /config/server/@PLUGIN@~project HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "from": "https://some-gerrit-server:8080",
    "user": "myUser",
    "pass": "myPassword",
    "projects": [
      "myProject",
      "myOtherProject"
    ]
  }
```


<a id="json-entities">JSON Entities
-----------------------------------

### <a id="project-input"></a>ProjectInput

The `ProjectInput` entity contains information about projects that
should be imported.

* _from_: URL of the remote system from where the project should be
imported.
* _user_: User on remote system.
* _pass_: Password of remote user.
* _projects_: The names of the projects to be imported as a list.


SEE ALSO
--------

* [Config related REST endpoints](../../../Documentation/rest-api-config.html)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
