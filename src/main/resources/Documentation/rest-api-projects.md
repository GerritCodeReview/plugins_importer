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

### <a id="resume-copy-project"> Resume Copy Project
_PUT /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/@PLUGIN@~copy.resume_

Resumes copying to a project from the original copy source.

Caller must be a member of a group that is granted the 'CopyProject'
capability (provided by this plugin) or the 'Administrate Server'
capability.

#### Request

```
  PUT /projects/myProjectCopy/@PLUGIN@~copy.resume HTTP/1.0
```

### <a id="resume-project-import"> Resume Project Import
_PUT /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/@PLUGIN@~import.resume_

Resumes importing to a project from the original copy source.

Information about the import resume must be provided in the request
body as a [ImportResumeInput](rest-api-config.html#import-resume-input)
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


<a id="json-entities">JSON Entities
-----------------------------------

### <a id="copy-project-input"></a>CopyProjectInput

The `CopyProjectInput` entity contains information about a the copy
target.

* _name_: The target project name.


SEE ALSO
--------

* [Config related REST endpoints](../../../Documentation/rest-api-projects.html)

GERRIT
------
Part of [Gerrit Code Review](../../../Documentation/index.html)
