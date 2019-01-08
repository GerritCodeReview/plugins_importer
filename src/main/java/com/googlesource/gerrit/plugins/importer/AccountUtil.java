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

import com.google.gerrit.common.errors.InvalidSshKeyException;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.SshKeyInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.VersionedAuthorizedKeys;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.account.CreateAccount;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class AccountUtil {
  private static Logger log = LoggerFactory.getLogger(AccountUtil.class);

  private static final String IMPORTED_USERS = "Imported Users";

  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final AuthType authType;
  private final com.google.gerrit.extensions.api.GerritApi gApi;
  private final VersionedAuthorizedKeys.Accessor authorizedKeys;
  private final CreateAccount createAccount;

  @Inject
  public AccountUtil(
      AccountCache accountCache,
      AccountManager accountManager,
      AuthConfig authConfig,
      com.google.gerrit.extensions.api.GerritApi gApi,
      VersionedAuthorizedKeys.Accessor authorizedKeys,
      CreateAccount createAccount) {
    this.accountCache = accountCache;
    this.accountManager = accountManager;
    this.authType = authConfig.getAuthType();
    this.gApi = gApi;
    this.authorizedKeys = authorizedKeys;
    this.createAccount = createAccount;
  }

  Account.Id resolveUser(GerritApi api, AccountInfo acc)
      throws NoSuchAccountException, IOException, OrmException, RestApiException,
          ConfigInvalidException, PermissionBackendException {
    if (acc.username == null) {
      throw new NoSuchAccountException(
          String.format(
              "User %s <%s> (%s) doesn't have a username and cannot be looked up.",
              acc.name, acc.email, acc._accountId));
    }
    Optional<AccountState> maybeAccount = accountCache.getByUsername(acc.username);

    if (!maybeAccount.isPresent()) {
      switch (authType) {
        case HTTP_LDAP:
        case CLIENT_SSL_CERT_LDAP:
        case LDAP:
          return createAccountByLdapAndAddSshKeys(api, acc);
        case CUSTOM_EXTENSION:
        case DEVELOPMENT_BECOME_ANY_ACCOUNT:
        case HTTP:
        case LDAP_BIND:
        case OAUTH:
        case OPENID:
        case OPENID_SSO:
        default:
          return createLocalUser(acc);
      }
    }
    Account a = maybeAccount.get().getAccount();
    if (!Objects.equals(a.getPreferredEmail(), acc.email)) {
      log.warn(
          String.format(
              "Email mismatch for user %s: expected %s but found %s",
              acc.username, acc.email, a.getPreferredEmail()));
    }
    return a.getId();
  }

  private Account.Id createAccountByLdapAndAddSshKeys(GerritApi api, AccountInfo acc)
      throws NoSuchAccountException, IOException, OrmException, RestApiException,
          ConfigInvalidException, PermissionBackendException {
    if (!ExternalId.isValidUsername(acc.username)) {
      throw new NoSuchAccountException(String.format("User %s not found", acc.username));
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

  private void addSshKeys(GerritApi api, AccountInfo acc)
      throws BadRequestException, IOException, OrmException, ConfigInvalidException {
    List<SshKeyInfo> sshKeys = api.getSshKeys(acc.username);
    Optional<AccountState> a = accountCache.getByUsername(acc.username);
    if (a.isPresent()) {
      Account.Id id = a.get().getAccount().getId();
      for (SshKeyInfo sshKeyInfo : sshKeys) {
        try {
          authorizedKeys.addKey(id, sshKeyInfo.sshPublicKey);
        } catch (InvalidSshKeyException e) {
          log.warn(String.format("Invalid SSH key for user %s", acc.username));
        }
      }
    }
  }

  private Account.Id createLocalUser(AccountInfo acc)
      throws OrmException, RestApiException, IOException, ConfigInvalidException,
          PermissionBackendException {
    AccountInput input = new AccountInput();
    log.info(String.format("User '%s' not found", acc.username));
    String username = acc.username;
    input.username = username;
    input.email = acc.email;
    input.name = acc.name;

    AccountInfo accInfo = createAccount.apply(IdString.fromDecoded(username), input).value();
    log.info(String.format("Local user '%s' created", username));

    Account.Id userId = new Account.Id(accInfo._accountId);
    Account account = accountCache.get(userId).get().getAccount();
    addToImportedUsersGroup(userId);
    account.setActive(false);
    accountCache.evict(userId);
    return userId;
  }

  private void addToImportedUsersGroup(Account.Id id) throws RestApiException {
    GroupApi importedUsers;
    try {
      importedUsers = gApi.groups().id(IMPORTED_USERS);
    } catch (ResourceNotFoundException e) {
      importedUsers = gApi.groups().create(IMPORTED_USERS);
    }
    importedUsers.addMembers(Integer.toString(id.get()));
  }
}
