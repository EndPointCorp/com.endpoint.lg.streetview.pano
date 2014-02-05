/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.endpoint.lg.streetview.pano;

import interactivespaces.service.web.server.HttpDynamicRequestHandler;
import interactivespaces.service.web.server.HttpRequest;
import interactivespaces.service.web.server.HttpResponse;
import interactivespaces.InteractiveSpacesException;

import com.endpoint.lg.streetview.pano.StreetviewConfiguration;

import java.io.OutputStream;
import java.io.IOException;

public class StreetviewConfigurationWebHandler implements HttpDynamicRequestHandler {
  private StreetviewConfiguration configuration;

  public StreetviewConfigurationWebHandler(StreetviewConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public void handle(HttpRequest request, HttpResponse response) {
    OutputStream out = response.getOutputStream();
    
    try {
      out.write(String.format("var config = %s;", configuration.toString()).getBytes());
    } catch (IOException e) {
      throw new InteractiveSpacesException("Error while writing config response", e);
    }
  }
}
