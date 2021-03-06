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
import com.google.gerrit.sshd.BaseCommand.UnloggedFailure;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

class PasswordUtil {

  static String readPassword(InputStream in, String pass)
      throws UnsupportedEncodingException, IOException, UnloggedFailure {
    if ("-".equals(pass)) {
      BufferedReader br = new BufferedReader(new InputStreamReader(in, UTF_8));
      pass = Strings.nullToEmpty(br.readLine());
      if (br.readLine() != null) {
        throw new UnloggedFailure(1, "multi-line password not allowed");
      }
    }
    return pass;
  }
}
