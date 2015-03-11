@PLUGIN@ - /config/ REST API
============================

This page describes the REST endpoints that are added by the @PLUGIN@
plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

<a id="importer-endpoints"> Importer Endpoints
---------------------------------------------

### <a id="import-project"> Import Project
_PUT /config/server/@PLUGIN@~projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)_

Imports a project.

Information about the project import must be provided in the request
body as a [ImportProjectInput](#import-project-input)
entity.

Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  PUT /config/server/@PLUGIN@~projects/myProject HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "from": "https://some-gerrit-server:8080",
    "user": "myUser",
    "pass": "myPassword"
  }
```

### <a id="list-imported-projects"> List Imported Projects
_GET /config/server/@PLUGIN@~projects/_

Lists the imported projects.

As result a map is returned that maps the project name to
[ImportProjectInput](#import-project-input) entity.

Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  GET /config/server/@PLUGIN@~projects/ HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
  "myProject": {
    "from": "http://localhost:8081/",
    "user": "admin"
  },
  "myOtherProject": {
    "from": "http://localhost:8081/",
    "user": "admin"
  }
}
```


<a id="json-entities">JSON Entities
-----------------------------------

### <a id="import-project-input"></a>ImportProjectInput

The `ImportProjectInput` entity contains information about a project
import.

* _from_: URL of the remote system from where the project should be
imported.
* _user_: User on remote system.
* _pass_: (Optional) Password of remote user (not set when listing
imported projects).
* _parent_: (Optional) Name of the parent project in the target system.
The imported project will be created under this parent project.


SEE ALSO
--------

* [Config related REST endpoints](../../../Documentation/rest-api-config.html)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
