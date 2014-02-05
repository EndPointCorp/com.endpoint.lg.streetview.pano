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

import interactivespaces.configuration.Configuration;
import interactivespaces.util.data.json.JsonNavigator;
import interactivespaces.util.data.json.JsonMapper;

import java.util.Map;
import com.google.common.collect.Maps;

public class StreetviewConfiguration {
  private Map<String, Object> configMap;
  private JsonNavigator configJson;
  private static JsonMapper mapper = new JsonMapper();

  public double getDouble(String key) {
    return configJson.getDouble(key);
  }

  public boolean getBoolean(String key) {
    return configJson.getBoolean(key);
  }

  public String getString(String key) {
    return configJson.getString(key);
  }

  public String toString() {
    return mapper.toString(configMap);
  }

  public StreetviewConfiguration(Configuration activityConfig) {
    configMap = Maps.newHashMap();

    configMap.put("port",
        activityConfig.getRequiredPropertyInteger("space.activity.webapp.web.server.port"));
    configMap.put("debug",
        activityConfig.getRequiredPropertyBoolean("space.activity.webapp.browser.debug"));

    configMap.put("renderMode",
        activityConfig.getRequiredPropertyString("lg.streetview.gapi.renderMode"));
    configMap.put("zoom", activityConfig.getRequiredPropertyInteger("lg.streetview.gapi.zoom"));

    configMap.put("yawOffset",
        activityConfig.getRequiredPropertyDouble("lg.streetview.viewSync.yawOffset"));
    configMap.put("master",
        activityConfig.getRequiredPropertyBoolean("lg.streetview.viewSync.master"));
    configMap.put("defaultPano",
        activityConfig.getRequiredPropertyString("lg.streetview.viewSync.defaultPano"));

    configJson = new JsonNavigator(configMap);
  }
}
