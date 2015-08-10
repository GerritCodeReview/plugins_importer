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
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.CreateAccount;
import com.google.gerrit.server.account.GetSshKeys.SshKeyInfo;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Singleton
class AccountUtil {
  private static Logger log = LoggerFactory.getLogger(AccountUtil.class);

  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final AuthType authType;
  private final Provider<ReviewDb> db;

  @Inject
  private CreateAccount.Factory createAccountFactory;

  @Inject
  public AccountUtil(
      AccountCache accountCache,
      AccountManager accountManager,
      AuthConfig authConfig,
      Provider<ReviewDb> db) {
    this.accountCache = accountCache;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
    this.db = db;
  }

  Account.Id resolveUser(GerritApi api, AccountInfo acc)
      throws NoSuchAccountException, BadRequestException, IOException,
      OrmException, ResourceConflictException, UnprocessableEntityException {
    if (acc.username == null) {
      throw new NoSuchAccountException(String.format(
          "User %s <%s> (%s) doesn't have a username and cannot be looked up.",
          acc.name, acc.email, acc._accountId));
    }
    AccountState a = getByUserName(acc);

    if (a == null) {
      switch (authType) {
        case HTTP_LDAP:
        case CLIENT_SSL_CERT_LDAP:
        case LDAP:
          return createAccountByLdapAndAddSshKeys(api, acc);
        default:
          return createLocalUser(acc);
      }
    }
    if (!Objects.equals(a.getAccount().getPreferredEmail(), acc.email)) {
      log.warn(String.format(
          "Email mismatch for user %s: expected %s but found %s",
          acc.username, acc.email, a.getAccount().getPreferredEmail()));
    }
    return a.getAccount().getId();
  }

  private AccountState getByUserName(AccountInfo acc) {
    AccountState a = accountCache.getByUsername(acc.username);
    if (a == null) {
      a = accountCache.getByUsername(localUser(acc));
    }
    return a;
  }

  private Account.Id createAccountByLdapAndAddSshKeys(GerritApi api,
      AccountInfo acc)
      throws NoSuchAccountException, BadRequestException, IOException,
      OrmException, ResourceConflictException, UnprocessableEntityException {
    if (!acc.username.matches(Account.USER_NAME_PATTERN)) {
      throw new NoSuchAccountException(String.format("User %s not found",
          acc.username));
    }

    try {
      AuthRequest req = AuthRequest.forUser(acc.username);
      req.setSkipAuthentication(true);
      Account.Id id = accountManager.authenticate(req).getAccountId();
      addSshKeys(api, acc);
      return id;
    } catch (AccountException e) {
      return createLocalUser(acc);
    }
  }

  private void addSshKeys(GerritApi api, AccountInfo acc) throws
  BadRequestException, IOException, OrmException {
    List<SshKeyInfo> sshKeys = api.getSshKeys(acc.username);
    AccountState a = accountCache.getByUsername(acc.username);
    db.get().accountSshKeys().upsert(toAccountSshKey(a, sshKeys));
  }

  private static Collection<AccountSshKey> toAccountSshKey(AccountState a,
      List<SshKeyInfo> sshKeys) {
    Collection<AccountSshKey> result = new HashSet<>();
    int index = 1;
    for (SshKeyInfo sshKeyInfo : sshKeys) {
      result.add(new AccountSshKey(
          new AccountSshKey.Id(a.getAccount().getId(), index++),
          sshKeyInfo.sshPublicKey));
    }
    return result;
  }

  private Account.Id createLocalUser(AccountInfo acc)
      throws BadRequestException, ResourceConflictException,
      UnprocessableEntityException, OrmException {
    CreateAccount.Input input = new CreateAccount.Input();
    log.info(String.format("User '%s' not found", acc.username));
    String username = localUser(acc);
    input.username = username;
    input.email = acc.email;
    input.name = acc.name;

    AccountInfo accInfo =
        createAccountFactory.create(username)
            .apply(TopLevelResource.INSTANCE, input).value();
    log.info(String.format("Local user '%s' created", username));

    Account account =
        accountCache.get(new Account.Id(accInfo._accountId)).getAccount();
    account.setActive(false);
    return account.getId();
  }

  private String localUser(AccountInfo acc) {
    return acc.username + "_imported";
  }


}
