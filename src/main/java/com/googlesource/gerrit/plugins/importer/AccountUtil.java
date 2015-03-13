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

import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Objects;

@Singleton
class AccountUtil {

  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final AuthType authType;

  @Inject
  public AccountUtil(AccountCache accountCache,
      AccountManager accountManager,
      AuthConfig authConfig) {
    this.accountCache = accountCache;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
  }

  Account.Id resolveUser(AccountInfo acc) throws NoSuchAccountException {
    AccountState a = accountCache.getByUsername(acc.username);
    if (a == null) {
      switch (authType) {
        case HTTP_LDAP:
        case CLIENT_SSL_CERT_LDAP:
        case LDAP:
          return createAccountByLdap(acc.username);
        default:
          throw new NoSuchAccountException(String.format("User %s not found",
              acc.username));
      }
    }
    if (!Objects.equals(a.getAccount().getPreferredEmail(), acc.email)) {
      throw new NoSuchAccountException(String.format(
          "User %s not found: Email mismatch, expected %s but found %s",
          acc.username, acc.email, a.getAccount().getPreferredEmail()));
    }
    return a.getAccount().getId();
  }

  private Account.Id createAccountByLdap(String user)
      throws NoSuchAccountException {
    if (!user.matches(Account.USER_NAME_PATTERN)) {
      throw new NoSuchAccountException(String.format("User %s not found", user));
    }

    try {
      AuthRequest req = AuthRequest.forUser(user);
      req.setSkipAuthentication(true);
      return accountManager.authenticate(req).getAccountId();
    } catch (AccountException e) {
      throw new NoSuchAccountException(String.format("User %s not found", user));
    }
  }
}
