include_defs('//bucklets/gerrit_plugin.bucklet')

MODULE = 'com.googlesource.gerrit.plugins.importer.Importer'

PROVIDED_DEPS = [
  '//lib:gson',
]

gerrit_plugin(
  name = 'importer',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/**/*']),
  gwt_module = MODULE,
  manifest_entries = [
    'Gerrit-PluginName: importer',
    'Gerrit-ApiType: plugin',
    'Gerrit-ApiVersion: 2.12-SNAPSHOT',
    'Gerrit-Module: com.googlesource.gerrit.plugins.importer.Module',
    'Gerrit-SshModule: com.googlesource.gerrit.plugins.importer.SshModule',
    'Gerrit-HttpModule: com.googlesource.gerrit.plugins.importer.HttpModule',
  ],
  provided_deps = PROVIDED_DEPS + GERRIT_TESTS,
)

# this is required for bucklets/tools/eclipse/project.py to work
java_library(
  name = 'classpath',
  deps = GERRIT_GWT_API + GERRIT_PLUGIN_API + GERRIT_TESTS + [
    ':importer__plugin',
    '//lib/gwt:user',
  ],
)
