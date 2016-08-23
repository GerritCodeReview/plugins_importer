@PLUGIN@ - /projects/ REST API
==============================

This page describes the REST endpoints that are added by the @PLUGIN@
plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

<a id="importer-endpoints"> Importer Endpoints
----------------------------------------------

### <a id="copy-project"> Copy Project
_PUT /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/@PLUGIN@~copy_

Copies a project.

Information about the copy target must be provided in the request body
as a [CopyProjectInput](#copy-project-input) entity.

Caller must be a member of a group that is granted the 'CopyProject'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  PUT /projects/myProject/@PLUGIN@~copy HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "name": "myProjectCopy"
  }
```

As result a [ImportStatisticInfo](rest-api-config.md#import-statistic-info)
entity is returned.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "num\_changes\_created": 5
  }
```

### <a id="resume-copy-project"> Resume Copy Project
_PUT /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/@PLUGIN@~copy.resume_

Resumes copying to a project from the original copy source.

Caller must be a member of a group that is granted the 'CopyProject'
capability (provided by this plugin) or the 'Administrate Server'
capability.

Options may be specified in the request body as a
[CopyResumeInput](#copy-resume-input) entity.

#### Request

```
  PUT /projects/myProjectCopy/@PLUGIN@~copy.resume HTTP/1.0
```

As result a [ResumeImportStatisticInfo](rest-api-config.md#resume-import-statistic-info)
entity is returned.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "num\_changes\_created": 1,
    "num\_changes\_updated": 2
  }
```

### <a id="resume-project-import"> Resume Project Import
_PUT /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/@PLUGIN@~import.resume_

Resumes importing to a project from the original copy source.

Information about the import resume must be provided in the request
body as a [ImportResumeInput](rest-api-config.md#import-resume-input)
entity.

Caller must be a member of a group that is granted the 'ImportProject'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  PUT /projects/myProjectCopy/@PLUGIN@~import.resume HTTP/1.0
  Content-Type: application/json;charset=UTF-8

  {
    "user": "myUser",
    "pass": "myPassword"
  }
```

As result a [ResumeImportStatisticInfo](rest-api-config.md#resume-import-statistic-info)
entity is returned.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "num\_changes\_created": 1,
    "num\_changes\_updated": 2
  }
```

### <a id="complete-project-import"> Complete Project Import
_POST /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/@PLUGIN@~delete)_

Mark a project import as completed.

Once a project import is completed it cannot be resumed any more.

#### Request

```
  POST /projects/myProject/@PLUGIN@~delete HTTP/1.0
```

#### Response

```
  HTTP/1.1 204 No Content
```


<a id="json-entities">JSON Entities
-----------------------------------

### <a id="copy-project-input"></a>CopyProjectInput

The `CopyProjectInput` entity contains information about a the copy
target.

* _name_: The target project name.

### <a id="copy-resume-input"></a>CopyResumeInput

The `CopyResumeInput` entity contains information about an copy resume.

* _force_: Whether the resume should be done forcefully. On resume with
force changes that have the same last modified timestamp in the source
and target project are resumed, otherwise they will be skipped.


SEE ALSO
--------

* [Config related REST endpoints](../../../Documentation/rest-api-projects.html)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
