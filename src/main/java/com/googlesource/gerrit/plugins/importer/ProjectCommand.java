//Copyright (C) 2015 The Android Open Source Project
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;

@CommandMetaData(name = "project", description = "Imports a project")
public class ProjectCommand extends SshCommand {
  @Option(name = "--from", aliases = {"-f"}, required = true, metaVar = "URL",
      usage = "URL of the remote system from where the project should be imported")
  private String url;

  @Option(name = "--user", aliases = {"-u"}, required = true, metaVar = "NAME",
      usage = "user on remote system")
  private String user;

  @Option(name = "--pass", aliases = {"-p"}, required = true, metaVar = "-|PASS",
      usage = "password of remote user")
  private String pass;

  @Argument(index = 0, multiValued = true, required = true, metaVar = "NAME",
      usage = "name of project to be imported")
  private List<String> projects;

  @Override
  protected void run() throws OrmException, IOException, UnloggedFailure {
    String password = readPassword();

    // TODO
    stdout.println("TODO");
  }

  private String readPassword() throws UnsupportedEncodingException,
      IOException {
    if ("-".equals(pass)) {
      BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8));
      pass = Strings.nullToEmpty(br.readLine());
      if (br.readLine() != null) {
        die("multi-line password not allowed");
      }
    }
    return pass;
  }
}

