package com.googlesource.gerrit.plugins.importer;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.inject.Injector;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

@NoHttpd
@TestPlugin(
    name = "importer",
    sysModule = "com.googlesource.gerrit.plugins.importer.Module",
    httpModule = "com.googlesource.gerrit.plugins.importer.HttpModule",
    sshModule = "com.googlesource.gerrit.plugins.importer.SshModule"
)
public class PluginSmokeTest extends LightweightPluginDaemonTest {

  @Test
  public void contextBoots() {
    // when
    Injector sysInjector = plugin.getSysInjector();

    // then
    assertThat(sysInjector.getBindings().size()).isGreaterThan(1);
  }
}
