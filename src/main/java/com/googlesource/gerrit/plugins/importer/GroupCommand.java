// Copyright (C) 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.importer;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@RequiresCapability(ImportCapability.ID)
@CommandMetaData(name = "group", description = "Imports a group")
public class GroupCommand extends SshCommand {
  @Option(name = "--from", aliases = {"-f"}, required = true, metaVar = "URL",
      usage = "URL of the remote system from where the group is imported from")
  private String url;

  @Option(name = "--user", aliases = {"-u"}, required = true, metaVar = "NAME",
      usage = "user on remote system")
  private String user;

  @Option(name = "--pass", aliases = {"-p"}, required = true, metaVar = "-|PASS",
      usage = "password of remote user")
  private String pass;

  @Argument(index = 0, required = true, metaVar = "NAME",
      usage = "name of the group to be imported")
  private String group;

  @Inject
  private ImportGroup.Factory importGroupFactory;

  @Override
  protected void run() throws OrmException, IOException, UnloggedFailure,
      ValidationException, GitAPIException, NoSuchChangeException,
      NoSuchAccountException {
    ImportGroup.Input input = new ImportGroup.Input();
    input.from = url;
    input.user = user;
    input.pass = getPassword();

    Response<String> response = importGroupFactory.create(new AccountGroup.NameKey(group)).apply(
        new ConfigResource(), input);
    stdout.println(response);

  }

  private String getPassword() throws IOException, UnloggedFailure {
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
