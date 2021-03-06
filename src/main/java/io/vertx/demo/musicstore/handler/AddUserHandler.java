/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.demo.musicstore.handler;

import io.vertx.core.Handler;
import io.vertx.ext.auth.sqlclient.SqlAuthentication;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.auth.VertxContextPRNG;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Tuple;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;

/**
 * @author Thomas Segismont
 */
public class AddUserHandler implements Handler<RoutingContext> {

  private final PgPool dbClient;
  private final String insertUser;
  private final SqlAuthentication authProvider;

  public AddUserHandler(PgPool dbClient, Properties sqlQueries, SqlAuthentication authProvider) {
    this.dbClient = dbClient;
    insertUser = sqlQueries.getProperty("insertUser");
    this.authProvider = authProvider;
  }

  @Override
  public void handle(RoutingContext rc) {
    MultiMap formAttributes = rc.request().formAttributes();

    String username = formAttributes.get("username");
    String password = formAttributes.get("password");
    String passwordConfirm = formAttributes.get("password-confirm");

    if (username == null || username.isEmpty()
      || password == null || password.isEmpty()
      || passwordConfirm == null || passwordConfirm.isEmpty()) {
      rc.response().setStatusCode(HTTP_BAD_REQUEST).end("Missing param");
      return;
    }

    if (!passwordConfirm.equals(password)) {
      rc.response().setStatusCode(HTTP_BAD_REQUEST).end("Password and confirmation differ");
      return;
    }

    String hash = authProvider.hash("pbkdf2", VertxContextPRNG.current().nextString(32), password);

    dbClient.preparedQuery(insertUser).rxExecute(Tuple.of(username, hash))
      .subscribe(updateResult -> {
        StringBuilder location = new StringBuilder("/login");
        Session session = rc.session();
        String return_url = session == null ? null : session.get("return_url");
        if (return_url != null) {
          try {
            location.append("?return_url=").append(new URI(return_url).toASCIIString());
          } catch (URISyntaxException ignored) {
          }
        }
        rc.response().setStatusCode(HTTP_MOVED_TEMP).putHeader("Location", location.toString()).end();
      }, rc::fail);
  }
}
