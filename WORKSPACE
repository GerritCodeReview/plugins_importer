workspace(name = "importer")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "1419057b24e41cda9a6ee9d9c431b9005faf8190",
    # local_path = "/home/<user>/projects/bazlets",
)

#Snapshot Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
#    "gerrit_api_maven_local",
#)

# Load snapshot Plugin API
#gerrit_api_maven_local()

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()

load(
    "@com_googlesource_gerrit_bazlets//:gerrit_gwt.bzl",
    "gerrit_gwt",
)

gerrit_gwt()
