workspace(name = "importer")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "70d6ae0cee03c456fa4b32c807f3d13c032ef498",
    #local_path = "/home/<user>/projects/bazlets",
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
