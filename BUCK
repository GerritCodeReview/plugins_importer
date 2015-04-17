include_defs('//bucklets/gerrit_plugin.bucklet')

MODULE = 'com.googlesource.gerrit.plugins.importer.Importer'

if STANDALONE_MODE:
  HTTP_LIB = '//lib/http:http_lib'
  GSON = '//lib/gson:gson'
  LOG4J = '//lib/log:log4j'
else:
  HTTP_LIB = '//plugins/importer/lib/http:http_lib'
  GSON = '//plugins/importer/lib/gson:gson'
  LOG4J = '//plugins/importer/lib/log:log4j'

gerrit_plugin(
  name = 'importer',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/**/*']),
  gwt_module = MODULE,
  manifest_entries = [
    'Gerrit-PluginName: importer',
    'Gerrit-ApiType: plugin',
    'Gerrit-ApiVersion: 2.11-SNAPSHOT',
    'Gerrit-Module: com.googlesource.gerrit.plugins.importer.Module',
    'Gerrit-SshModule: com.googlesource.gerrit.plugins.importer.SshModule',
    'Gerrit-HttpModule: com.googlesource.gerrit.plugins.importer.HttpModule',
  ],
  deps = [
    HTTP_LIB,
  ],
  provided_deps = [
    LOG4J,
    GSON,
  ],
)

# this is required for bucklets/tools/eclipse/project.py to work
java_library(
  name = 'classpath',
  deps = [':importer__plugin'],
)

