load("//tools/bzl:classpath.bzl", "classpath_collector")
load(
    "//tools/bzl:plugin.bzl",
    "GWT_PLUGIN_DEPS",
    "PLUGIN_DEPS",
)

classpath_collector(
    name = "main_classpath_collect",
    deps = PLUGIN_DEPS + GWT_PLUGIN_DEPS + [
        "//external:gwt-dev",
        "//external:gwt-user",
        "//:importer__plugin",
    ],
)
