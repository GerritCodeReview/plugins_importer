load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "importer",
    srcs = glob(["src/main/java/**/*.java"]),
    resources = glob(["src/main/**/*"]),
    gwt_module = "com.googlesource.gerrit.plugins.importer.Importer",
    manifest_entries = [
        "Gerrit-PluginName: importer",
        "Gerrit-Module: com.googlesource.gerrit.plugins.importer.Module",
        "Gerrit-SshModule: com.googlesource.gerrit.plugins.importer.SshModule",
        "Gerrit-HttpModule: com.googlesource.gerrit.plugins.importer.HttpModule",
    ],
)

