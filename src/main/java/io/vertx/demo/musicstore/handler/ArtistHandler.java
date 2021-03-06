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

import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.demo.musicstore.PathUtil;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.reactivex.pgclient.PgPool;
import io.vertx.reactivex.sqlclient.Tuple;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Thomas Segismont
 */
public class ArtistHandler implements Handler<RoutingContext> {

  private final PgPool dbClient;
  private final String findArtistById;
  private final String findAlbumsByArtist;
  private final FreeMarkerTemplateEngine templateEngine;

  public ArtistHandler(PgPool dbClient, Properties sqlQueries, FreeMarkerTemplateEngine templateEngine) {
    this.dbClient = dbClient;
    findArtistById = sqlQueries.getProperty("findArtistById");
    findAlbumsByArtist = sqlQueries.getProperty("findAlbumsByArtist");
    this.templateEngine = templateEngine;
  }

  @Override
  public void handle(RoutingContext rc) {
    Long artistId = PathUtil.parseLongParam(rc.pathParam("artistId"));
    if (artistId == null) {
      rc.next();
      return;
    }

    Single<JsonObject> ars = findArtist(artistId);
    Single<JsonArray> als = findAlbums(artistId);

    Single.zip(ars, als, (artist, albums) -> {
      Map<String, Object> data = new HashMap<>(2);
      data.put("artist", artist);
      data.put("albums", albums);
      return data;
    }).flatMap(data -> {
      data.forEach(rc::put);
      return templateEngine.rxRender(rc.data(), "templates/artist");
    }).subscribe(rc.response()::end, rc::fail);
  }

  private Single<JsonObject> findArtist(Long artistId) {
    return dbClient.preparedQuery(findArtistById).rxExecute(Tuple.of(artistId))
      .flatMapObservable(Observable::fromIterable)
      .map(row -> new JsonObject().put("id", artistId).put("name", row.getString(0)))
      .singleOrError();
  }

  private Single<JsonArray> findAlbums(Long artistId) {
    return dbClient.preparedQuery(findAlbumsByArtist).rxExecute(Tuple.of(artistId))
      .flatMapObservable(Observable::fromIterable)
      .map(row -> new JsonObject().put("id", row.getLong(0)).put("title", row.getString(1)))
      .collect(JsonArray::new, JsonArray::add);
  }
}
