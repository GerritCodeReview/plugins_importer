package com.googlesource.gerrit.plugins.importer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.sshd.SshCommand;

import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

public abstract class ImporterSshCommand extends SshCommand {

  @Option(name = "--pass", aliases = {"-p"}, required = true, metaVar = "-|PASS",
      usage = "password of remote user")
  protected String pass;

  protected String readPassword() throws UnsupportedEncodingException,
      IOException, UnloggedFailure {
    if ("-".equals(pass)) {
      BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8));
      pass = Strings.nullToEmpty(br.readLine());
      if (br.readLine() != null) {
        throw die("multi-line password not allowed");
      }
    }
    return pass;
  }
}
