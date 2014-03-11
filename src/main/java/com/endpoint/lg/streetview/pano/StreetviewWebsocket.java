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

import com.endpoint.lg.support.domain.streetview.StreetviewLinks;
import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.message.MessageWrapper;
import com.endpoint.lg.support.message.OutboundRosMessage;
import com.endpoint.lg.support.message.OutboundWebsocketMessage;
import com.endpoint.lg.support.message.WebsocketMessageHandler;
import com.endpoint.lg.support.message.WebsocketMessageHandlers;
import com.endpoint.lg.support.message.WebsocketRefreshEvent;
import com.endpoint.lg.support.message.streetview.MessageTypesStreetview;
import com.google.common.eventbus.EventBus;

import interactivespaces.util.data.json.JsonBuilder;
import interactivespaces.util.data.json.JsonNavigator;

/**
 * Web socket messaging interface for Street View activity. Provides methods for
 * sending messages to the browser and registering message handlers.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewWebsocket extends WebsocketMessageHandlers {
  private EventBus eventBus;

  public StreetviewWebsocket(EventBus bus, Log log) {
    super(log);
    this.eventBus = bus;

    /**
     * Handles <code>StreetviewPov</code> updates from the browser.
     */
    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV,
        new WebsocketMessageHandler() {
          public void handleMessage(String connectionId, JsonNavigator json) {
            final String type = MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV;

            eventBus.post(new OutboundRosMessage(type, json.getCurrentAsJsonBuilder()));
          }
        });

    /**
     * Handles <code>StreetviewPano</code> updates from the browser.
     */
    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO,
        new WebsocketMessageHandler() {
          public void handleMessage(String connectionId, JsonNavigator json) {
            final String type = MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO;

            eventBus.post(new OutboundRosMessage(type, json.getCurrentAsJsonBuilder()));
          }
        });

    /**
     * Handles <code>StreetviewLinks</code> updates from the browser.
     */
    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_LINKS,
        new WebsocketMessageHandler() {
          public void handleMessage(String connectionId, JsonNavigator json) {
            eventBus.post(new StreetviewLinks(json));
          }
        });

    /**
     * Handles refresh requests from the browser.
     */
    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_REFRESH,
        new WebsocketMessageHandler() {
          public void handleMessage(String connectionId, JsonNavigator json) {
            eventBus.post(new WebsocketRefreshEvent());
          }
        });

    /**
     * Handles log messages from the browser.
     */
    registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_LOG,
        new WebsocketMessageHandler() {
          public void handleMessage(String connectionId, JsonNavigator json) {
            getLog().info(
                String.format("%s: %s", json.getString("type").toUpperCase(), json.getString("message")));
          }
        });
  }

  /**
   * Sends a <code>StreetviewPov</code> to the browser, which should update its
   * view immediately.
   */
  public void sendPov(StreetviewPov pov) {
    JsonBuilder json =
        MessageWrapper.newTypedMessage(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV,
            pov.getMap());

    eventBus.post(new OutboundWebsocketMessage(json));
  }

  /**
   * Sends a <code>StreetviewPano</code> to the browser, which should update its
   * view immediately.
   */
  public void sendPano(StreetviewPano pano) {
    if (pano.getPanoid() == null)
      return;

    JsonBuilder json =
        MessageWrapper.newTypedMessage(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO,
            pano.getMap());

    eventBus.post(new OutboundWebsocketMessage(json));
  }
}
