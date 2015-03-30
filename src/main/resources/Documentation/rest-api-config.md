@PLUGIN@ - /config/ REST API
============================

This page describes the REST endpoints that are added by the @PLUGIN@
plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

<a id="importer-endpoints"> Importer Endpoints
----------------------------------------------

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

Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

It is possible to filter the list of imported projects using the
`match` option. The response will include only those projects whose
name contains the given `match` substring, case insensitive.

#### Request

```
  GET /config/server/@PLUGIN@~projects/?match=my HTTP/1.0
```

As result a map is returned that maps the project name to
[ImportProjectInfo](#import-project-info) entity.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "myProject": {
      "from": "http://localhost:8081/",
      "name": "myProject",
      "imports": [
        {
          "timestamp": "2015-03-11 09:14:21.748000000",
          "user": {
            "_account_id": 1000000,
            "name": "Administrator",
            "email": "edwin.kempin@gmail.com",
            "username": "admin"
          },
          "remote_user": "admin"
        }
      ]
    },
    "myProject2": {
      "from": "http://localhost:8081/",
      "name": "projectToBeRenamed",
      "imports": [
        {
          "timestamp": "2015-03-11 09:16:04.511000000",
          "user": {
            "_account_id": 1000000,
            "name": "Administrator",
            "email": "edwin.kempin@gmail.com",
            "username": "admin"
          },
          "remote_user": "admin"
        }
      ]
    }
  }
```

### <a id="get-imported-project"> Get Imported Project
_GET /config/server/@PLUGIN@~projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)_

Gets information about the imports of a project.

As result a [ImportProjectInfo](#import-project-info) entity is
returned.

Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  GET /config/server/@PLUGIN@~projects/myProject HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "from": "http://localhost:8081/",
    "name": "myProject",
    "imports": [
      {
        "timestamp": "2015-03-11 09:14:21.748000000",
        "user": {
          "_account_id": 1000000,
          "name": "Administrator",
          "email": "edwin.kempin@gmail.com",
          "username": "admin"
        },
        "remote_user": "admin"
      }
    ]
  }
```

### <a id="resume-project-import"> Resume Project Import
_GET /config/server/@PLUGIN@~projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/resume_

Resumes a project import.

Information about the import resume must be provided in the request
body as a [ImportResumeInput](#import-resume-input) entity.

Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  PUT /config/server/@PLUGIN@~projects/myProject/resume HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "user": "myUser",
    "pass": "myPassword"
  }
```

### <a id="import-group"> Import Group
_PUT /config/server/@PLUGIN@~groups/[\{group-name\}](../../../Documentation/rest-api-groups.html#group-name)_

Imports a group.

Information about the group import must be provided in the request
body as a [ImportGroupInput](#import-group-input)
entity.

Caller must be a member of a group that is granted the 'Import'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  PUT /config/server/@PLUGIN@~groups/myGroup HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "from": "https://some-gerrit-server:8080",
    "user": "myUser",
    "pass": "myPassword"
  }
```

### <a id="complete-project-import"> Complete Project Import
_DELETE /config/server/@PLUGIN@~projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)_

Mark a project import as completed.

Once a project import is completed it cannot be resumed any more.

#### Request

```
  DELETE /config/server/@PLUGIN@~projects/myProject HTTP/1.0
```

#### Response

```
  HTTP/1.1 204 No Content
```

<a id="json-entities">JSON Entities
-----------------------------------


### <a id="import-group-input"></a>ImportGroupInput

The `ImportGroupInput` entity contains information about a group import.

* _from_: URL of the remote system from where the group should be
imported.
* _user_: User on remote system.
* _pass_: Password of remote user.

### <a id="import-info"></a>ImportInfo

The `ImportInfo` entity contains information about a past import.

* _timestamp_: The timestamp of when the import was done.
* _user_: User that did the import as a detailed
link:../../../Documentation/rest-api-accounts.html#account-info[AccountInfo]
entity.
* _remote_user_: User on remote system.

### <a id="import-project-info"></a>ImportProjectInfo

The `ImportProjectInfo` entity contains information about the past
imports of a project.

* _from_: URL of the remote system from where the project should be
imported.
* _name_: Name of the project in the source system.
* _parent_: (Optional) Name of the parent project in the target system.
* _imports_: List of past imports as [ImportInfo](#import-info)
entities.

### <a id="import-project-input"></a>ImportProjectInput

The `ImportProjectInput` entity contains information about a project
import.

* _from_: URL of the remote system from where the project should be
imported.
* _name_: (Optional) Name of the project in the source system.
If not specified it is assumed to be the same name as in the target
system.
* _user_: User on remote system.
* _pass_: Password of remote user.
* _parent_: (Optional) Name of the parent project in the target system.
The imported project will be created under this parent project.

### <a id="import-resume-input"></a>ImportResumeInput

The `ImportResumeInput` entity contains information about an import
resume.

* _user_: User on remote system.
* _pass_: Password of remote user.
* _force_: Whether the resume should be done forcefully. On resume with
force changes that have the same last modified timestamp in the source
and target system are resumed, otherwise they will be skipped.


SEE ALSO
--------

* [Config related REST endpoints](../../../Documentation/rest-api-config.html)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
