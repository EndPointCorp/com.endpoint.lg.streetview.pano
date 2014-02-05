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

import interactivespaces.activity.impl.web.BaseRoutableRosWebServerActivity;
import interactivespaces.util.data.json.JsonBuilder;

import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.message.streetview.MessageTypesStreetview;
import com.endpoint.lg.support.message.RosMessageHandlers;

/**
 * Ros messaging interface for Street View activity. Provides methods for
 * sending messages to routes and registering message handlers.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewRos extends RosMessageHandlers {
  public static final String FIELD_ECHO = "echo";

  private BaseRoutableRosWebServerActivity activity;

  public StreetviewRos(BaseRoutableRosWebServerActivity activity) {
    super(activity.getLog());
    this.activity = activity;
  }

  /**
   * Sends a <code>StreetviewPov</code> to the route. If echo is true, the
   * message will be flagged to be ignored by the master.
   * 
   * @param echo
   *          if true, do not echo to master
   */
  public void sendPov(StreetviewPov pov, boolean echo) {
    JsonBuilder message = pov.getJsonBuilder();
    message.put(FIELD_ECHO, echo);

    activity.sendOutputJsonBuilder(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV, message);
  }

  /**
   * Sends a <code>StreetviewPano</code> to the route. If echo is true, the
   * message will be flagged to be ignored by the master.
   * 
   * @param echo
   *          if true, do not echo to master
   */
  public void sendPano(StreetviewPano pano, boolean echo) {
    JsonBuilder message = pano.getJsonBuilder();
    message.put(FIELD_ECHO, echo);

    activity.sendOutputJsonBuilder(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO, message);
  }

  /**
   * Requests the authoritative view state from the master.
   */
  public void sendRefresh() {
    JsonBuilder message = new JsonBuilder(); // empty message

    activity.sendOutputJsonBuilder(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_REFRESH, message);
  }
}
