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

import com.endpoint.lg.support.message.WebsocketMessageHandler;
import com.endpoint.lg.support.message.RosMessageHandler;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewLink;
import com.endpoint.lg.support.domain.streetview.StreetviewLinks;
import com.endpoint.lg.support.evdev.InputEventCodes;
import com.endpoint.lg.support.message.streetview.MessageTypesStreetview;
import com.endpoint.lg.support.window.WindowInstanceIdentity;
import com.endpoint.lg.support.window.ManagedWindow;

import interactivespaces.activity.impl.web.BaseRoutableRosWebActivity;
import interactivespaces.service.web.server.WebServer;
import interactivespaces.util.data.json.JsonNavigator;

import com.endpoint.lg.support.evdev.InputKeyEvent;

import com.endpoint.lg.support.evdev.InputAbsState;
import com.endpoint.lg.streetview.pano.StreetviewConfigurationWebHandler;
import com.endpoint.lg.streetview.pano.StreetviewModel;
import com.endpoint.lg.support.window.WindowIdentity;

import java.io.File;
import java.util.Map;

/**
 * A Street View web activity. Synchronizes view changes with other activities
 * with a master/slave architecture.
 * 
 * <p>
 * View changes from external sources (input devices) are broadcast to all
 * instances simultaneously, reducing latency between master and slaves for
 * those changes.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewPanoActivity extends BaseRoutableRosWebActivity {

  /**
   * The dynamic configuration handler will catch requests for this file.
   */
  public static final String CONFIG_HANDLER_PATH = "/config.js";

  /**
   * Coefficient of input event value to POV translation.
   */
  public static final double INPUT_SENSITIVITY = 0.0032;

  /**
   * Frequency of the input event aggregation loop, in Hz.
   */
  public static final double INPUT_LOOP_FREQUENCY = 60.0;

  /**
   * How much "momentum" on a controller is needed to move forward or backward.
   */
  public static final int INPUT_MOVEMENT_COUNT = 10;

  /**
   * Controller forward/backward axes must exceed this value for movement (after
   * sensitivity).
   */
  public static final double INPUT_MOVEMENT_THRESHOLD = 1.0;

  /**
   * After movement, wait this many milliseconds before moving again.
   */
  public static final int INPUT_MOVEMENT_COOLDOWN = 500;

  private StreetviewConfiguration configuration;

  private StreetviewModel model;
  private InputAbsState absState;
  private StreetviewWebsocket websocket;
  private StreetviewRos ros;

  private ManagedWindow window;

  private boolean linksDirty = false;
  private boolean master = false;

  long lastMoveTime;
  int movementCounter;

  /**
   * Returns true if this is the master live activity. The master has the
   * authoritative POV and handles external inputs.
   * 
   * @return true if master
   */
  public boolean isMaster() {
    return master;
  }

  /**
   * Handles <code>StreetviewPov</code> updates from the browser.
   */
  private class WebsocketPovHandler implements WebsocketMessageHandler {
    public void handleMessage(String connectionId, JsonNavigator data) {
      if (isMaster()) {
        StreetviewPov pov = new StreetviewPov(data);

        model.setPov(pov);
        ros.sendPov(pov);
      }
    }
  }

  /**
   * Handles <code>StreetviewPano</code> updates from the browser.
   */
  private class WebsocketPanoHandler implements WebsocketMessageHandler {
    public void handleMessage(String connectionId, JsonNavigator data) {
      if (isMaster()) {
        StreetviewPano pano = new StreetviewPano(data);

        model.setPano(pano);
        ros.sendPano(pano);
      }
    }
  }

  /**
   * Handles <code>StreetviewLinks</code> updates from the browser.
   */
  private class WebsocketLinksHandler implements WebsocketMessageHandler {
    public void handleMessage(String connectionId, JsonNavigator data) {
      if (isMaster()) {
        model.setLinks(new StreetviewLinks(data));
        linksDirty = false;
      }
    }
  }

  /**
   * Handles refresh requests from the browser. If this is the master instance,
   * the model data should be returned. Otherwise, it should be requested from
   * the master.
   */
  private class WebsocketRefreshHandler implements WebsocketMessageHandler {
    public void handleMessage(String connectionId, JsonNavigator data) {
      if (!isMaster()) {
        ros.sendRefresh();
      } else {
        ros.sendPov(model.getPov());
        ros.sendPano(model.getPano());
      }
    }
  }

  /**
   * Handles log messages from the browser.
   */
  private class WebsocketLogHandler implements WebsocketMessageHandler {
    public void handleMessage(String connectionId, JsonNavigator data) {
      getLog().info(
          String.format("%s: %s", data.getString("type").toUpperCase(), data.getString("message")));
    }
  }

  /**
   * Handles <code>StreetviewPov</code> updates from Ros.
   */
  private class RosPovHandler implements RosMessageHandler {
    public void handleMessage(JsonNavigator json) {
      StreetviewPov pov = new StreetviewPov(json);

      model.setPov(pov);
      websocket.sendPov(pov);
    }
  }

  /**
   * Handles <code>StreetviewPano</code> updates from Ros.
   */
  private class RosPanoHandler implements RosMessageHandler {
    public void handleMessage(JsonNavigator json) {
      StreetviewPano pano = new StreetviewPano(json);

      model.setPano(pano);
      websocket.sendPano(pano);
    }
  }

  /**
   * Handles Ros refresh requests. A refresh request should be handled by
   * sending out the view state.
   */
  private class RosRefreshHandler implements RosMessageHandler {
    public void handleMessage(JsonNavigator json) {
      if (isMaster()) {
        ros.sendPov(model.getPov());
        ros.sendPano(model.getPano());
      }
    }
  }

  /**
   * Handles <code>InputKeyEvent</code> updates from Ros.
   */
  private class RosKeyEventHandler implements RosMessageHandler {
    public void handleMessage(JsonNavigator json) {
      if (isMaster() && isActivated()) {
        InputKeyEvent event = new InputKeyEvent(json);
        if (event.getValue() > 0 && !linksDirty) {
          moveForward();
        }
      }
    }
  }

  /**
   * Handles <code>InputAbsState</code> updates from Ros.
   */
  private class RosAbsStateHandler implements RosMessageHandler {
    public void handleMessage(JsonNavigator json) {
      if (isMaster() && isActivated()) {
        if (absState.update(json)) {
          processAbsUpdate();
        }
      }
    }
  }

  /**
   * Sends incoming web socket messages to the web socket message handlers.
   */
  @Override
  public void onWebSocketReceive(String connectionId, Object d) {
    websocket.handleMessage(connectionId, d);
  }

  /**
   * Sends incoming Ros messages to the Ros message handlers.
   */
  @Override
  public void onNewInputJson(String channel, Map<String, Object> message) {
    ros.handleMessage(channel, message);
  }

  /**
   * Process EV_ABS state updates from SpaceNav reader. Check for yaw and
   * movement.
   */
  private void processAbsUpdate() {
    double yaw = absState.getValue(InputEventCodes.ABS_RZ) * INPUT_SENSITIVITY;

    if (yaw != 0) {
      StreetviewPov pov = model.getPov();
      pov.translate(yaw, 0);
      ros.sendPov(pov);
    }

    long currentTime = System.currentTimeMillis();

    // movement can be either forwards or backwards, depending on whether the
    // SpaceNav is moved+tilted forwards or backwards.
    // TODO: Movement in all directions.
    double movement =
        -INPUT_SENSITIVITY
            * ((absState.getValue(InputEventCodes.ABS_Y) + absState
                .getValue(InputEventCodes.ABS_RX)));

    if (movement > INPUT_MOVEMENT_THRESHOLD) {
      movementCounter++;
    } else if (movement < -INPUT_MOVEMENT_THRESHOLD) {
      movementCounter--;
    } else {
      movementCounter = 0;
    }

    if ((currentTime - lastMoveTime) < INPUT_MOVEMENT_COOLDOWN) {
      movementCounter = 0;
    } else if (movementCounter > INPUT_MOVEMENT_COUNT && !linksDirty) {
      moveForward();
      movementCounter = 0;
      lastMoveTime = currentTime;
    } else if (movementCounter < -INPUT_MOVEMENT_COUNT && !linksDirty) {
      moveBackward();
      movementCounter = 0;
      lastMoveTime = currentTime;
    }
  }

  private void moveToward(double heading) {
    StreetviewLink nearest = model.getLinks().getNearestLink(heading);

    if (nearest != null)
      ros.sendPano(new StreetviewPano(nearest.getPano()));
  }

  private void moveForward() {
    moveToward(model.getPov().getHeading());
  }

  private void moveBackward() {
    moveToward(model.getPov().getHeading() - 180);
  }

  /**
   * Initializes activity configuration, messaging handlers, and state.
   */
  @Override
  public void onActivitySetup() {
    updateConfiguration();

    model = new StreetviewModel();

    websocket = new StreetviewWebsocket(this);

    websocket.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV,
        new WebsocketPovHandler());
    websocket.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO,
        new WebsocketPanoHandler());
    websocket.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_LINKS,
        new WebsocketLinksHandler());
    websocket.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_REFRESH,
        new WebsocketRefreshHandler());
    websocket.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_LOG,
        new WebsocketLogHandler());

    ros = new StreetviewRos(this);

    ros.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_POV, new RosPovHandler());
    ros.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_PANO, new RosPanoHandler());
    ros.registerHandler(MessageTypesStreetview.MESSAGE_TYPE_STREETVIEW_REFRESH,
        new RosRefreshHandler());
    ros.registerHandler("key", new RosKeyEventHandler());
    ros.registerHandler("abs", new RosAbsStateHandler());

    absState = new InputAbsState();

    lastMoveTime = System.currentTimeMillis();
    movementCounter = 0;

    File tempdir = getActivityFilesystem().getTempDataDirectory();
    WindowIdentity windowId = new WindowInstanceIdentity(tempdir.getAbsolutePath());

    window = new ManagedWindow(this, windowId);
    addManagedResource(window);
    window.startup();
  }

  /**
   * Starts the web server.
   */
  @Override
  public void onActivityStartup() {
    WebServer webserver = getWebServer();
    StreetviewConfigurationWebHandler configHandler =
        new StreetviewConfigurationWebHandler(configuration);

    webserver.addDynamicContentHandler(CONFIG_HANDLER_PATH, false, configHandler);
  }

  @Override
  public void onActivityActivate() {
    window.activate();
  }

  @Override
  public void onActivityDeactivate() {
    window.deactivate();
  }

  /**
   * Refreshes the configuration object on configuration updates from the
   * master.
   */
  @Override
  public void onActivityConfigurationUpdate(Map<String, Object> update) {
    updateConfiguration();
  }

  private void updateConfiguration() {
    configuration = new StreetviewConfiguration(getConfiguration());

    master = configuration.getBoolean("master");

    if (window != null)
      window.update();
  }
}
