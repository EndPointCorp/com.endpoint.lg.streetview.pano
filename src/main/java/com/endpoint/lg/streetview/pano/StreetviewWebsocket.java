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

import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.message.MessageWrapper;
import com.endpoint.lg.support.message.WebsocketMessageHandlers;
import com.endpoint.lg.support.message.streetview.MessageTypesStreetview;

import interactivespaces.activity.impl.web.BaseRoutableRosWebServerActivity;
import interactivespaces.util.data.json.JsonBuilder;

/**
 * Web socket messaging interface for Street View activity. Provides methods for
 * sending messages to the browser and registering message handlers.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewWebsocket extends WebsocketMessageHandlers {
  private BaseRoutableRosWebServerActivity activity;

  public StreetviewWebsocket(BaseRoutableRosWebServerActivity activity) {
    super(activity.getLog());
    this.activity = activity;
  }

  /**
   * Sends a <code>StreetviewPov</code> to the browser, which should update its
   * view immediately.
   */
  public void sendPov(StreetviewPov pov) {
    JsonBuilder message =
        MessageWrapper.newTypedMessage(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV,
            pov.getMap());

    activity.sendAllWebSocketJsonBuilder(message);
  }

  /**
   * Sends a <code>StreetviewPano</code> to the browser, which should update its
   * view immediately.
   */
  public void sendPano(StreetviewPano pano) {
    if (pano.getPanoid() == null)
      return;

    JsonBuilder message =
        MessageWrapper.newTypedMessage(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO,
            pano.getMap());

    activity.sendAllWebSocketJsonBuilder(message);
  }
}
