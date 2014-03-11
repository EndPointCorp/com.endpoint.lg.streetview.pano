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

import org.apache.commons.logging.Log;

import interactivespaces.util.data.json.JsonBuilder;
import interactivespaces.util.data.json.JsonNavigator;

import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.evdev.InputAbsState;
import com.endpoint.lg.support.evdev.InputKeyEvent;
import com.endpoint.lg.support.message.streetview.MessageTypesStreetview;
import com.endpoint.lg.support.message.OutboundRosMessage;
import com.endpoint.lg.support.message.RefreshEvent;
import com.endpoint.lg.support.message.RosMessageHandler;
import com.endpoint.lg.support.message.RosMessageHandlers;
import com.google.common.eventbus.EventBus;

/**
 * Ros messaging interface for Street View activity. Provides methods for
 * sending messages to routes and registering message handlers.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewRos extends RosMessageHandlers {
  private EventBus eventBus;

  public StreetviewRos(EventBus bus, Log log) {
    super(log);
    this.eventBus = bus;

    registerHandler("key", new RosMessageHandler() {
      public void handleMessage(JsonNavigator json) {
        eventBus.post(new InputKeyEvent(json));
      }
    });

    registerHandler("abs", new RosMessageHandler() {
      public void handleMessage(JsonNavigator json) {
        eventBus.post(new InputAbsState(json));
      }
    });

    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV, new RosMessageHandler() {
      public void handleMessage(JsonNavigator json) {
        eventBus.post(new StreetviewPov(json));
      }
    });

    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO, new RosMessageHandler() {
      public void handleMessage(JsonNavigator json) {
        eventBus.post(new StreetviewPano(json));
      }
    });

    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_REFRESH,
        new RosMessageHandler() {
          public void handleMessage(JsonNavigator json) {
            eventBus.post(new RefreshEvent());
          }
        });
  }

  /**
   * Abstraction for publishing Ros messages via <code>EventBus</code>.
   * 
   * @param channel
   *          destination route
   * @param json
   *          the message body
   */
  private void publishRosMessage(String channel, JsonBuilder json) {
    eventBus.post(new OutboundRosMessage(channel, json));
  }

  /**
   * Sends a <code>StreetviewPov</code> to the route.
   */
  public void sendPov(StreetviewPov pov) {
    JsonBuilder json = pov.getJsonBuilder();

    publishRosMessage(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV, json);
  }

  /**
   * Sends a <code>StreetviewPano</code> to the route.
   */
  public void sendPano(StreetviewPano pano) {
    if (pano.getPanoid() == null)
      return;

    JsonBuilder json = pano.getJsonBuilder();

    publishRosMessage(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO, json);
  }

  /**
   * Requests the authoritative view state from the master.
   */
  public void sendRefresh() {
    JsonBuilder json = new JsonBuilder(); // empty message

    publishRosMessage(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_REFRESH, json);
  }
}
