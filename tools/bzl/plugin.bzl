load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    _gerrit_plugin = "gerrit_plugin",
    _gwt_plugin_deps = "GWT_PLUGIN_DEPS",
    _plugin_deps = "PLUGIN_DEPS",
)

gerrit_plugin = _gerrit_plugin
GWT_PLUGIN_DEPS = _gwt_plugin_deps
PLUGIN_DEPS = _plugin_deps
