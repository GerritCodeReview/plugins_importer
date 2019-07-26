load("//tools/bzl:junit.bzl", "junit_tests")
load("//tools/bzl:plugin.bzl", "PLUGIN_DEPS", "PLUGIN_TEST_DEPS", "gerrit_plugin")

gerrit_plugin(
    name = "importer",
    srcs = glob(["src/main/java/**/*.java"]),
    gwt_module = "com.googlesource.gerrit.plugins.importer.Importer",
    manifest_entries = [
        "Gerrit-PluginName: importer",
        "Gerrit-Module: com.googlesource.gerrit.plugins.importer.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.importer.SshModule",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.importer.HttpModule",
    ],
    resources = glob(["src/main/**/*"]),
)

junit_tests(
    name = "importer_tests",
    srcs = glob(["src/test/java/**/*Test.java"]),
    tags = ["importer"],
    visibility = ["//visibility:public"],
    deps = PLUGIN_TEST_DEPS + PLUGIN_DEPS + [
        ":importer__plugin"
    ],
)
