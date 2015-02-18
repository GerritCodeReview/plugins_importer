include_defs('//bucklets/gerrit_plugin.bucklet')

gerrit_plugin(
  name = 'importer',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: importer',
    'Gerrit-ApiType: plugin',
    'Gerrit-ApiVersion: 2.10',
    'Gerrit-Module: com.googlesource.gerrit.plugins.importer.Module',
    'Gerrit-SshModule: com.googlesource.gerrit.plugins.importer.SshModule',
  ],
)

# this is required for bucklets/tools/eclipse/project.py to work
java_library(
  name = 'classpath',
  deps = [':importer__plugin'],
)

