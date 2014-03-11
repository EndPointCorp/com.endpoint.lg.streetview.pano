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

import com.endpoint.lg.support.message.OutboundRosMessage;
import com.endpoint.lg.support.message.OutboundWebsocketMessage;
import com.endpoint.lg.support.message.RefreshEvent;
import com.endpoint.lg.support.message.WebsocketRefreshEvent;
import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewLink;
import com.endpoint.lg.support.domain.streetview.StreetviewLinks;
import com.endpoint.lg.support.evdev.InputEventCodes;
import com.endpoint.lg.support.window.WindowInstanceIdentity;
import com.endpoint.lg.support.window.ManagedWindow;
import com.endpoint.lg.support.evdev.InputKeyEvent;
import com.endpoint.lg.support.evdev.InputAbsState;
import com.endpoint.lg.streetview.pano.StreetviewConfigurationWebHandler;
import com.endpoint.lg.streetview.pano.StreetviewModel;
import com.endpoint.lg.support.window.WindowIdentity;

import interactivespaces.activity.impl.web.BaseRoutableRosWebActivity;
import interactivespaces.service.web.server.WebServer;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

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

  private EventBus eventBus;

  private StreetviewConfiguration configuration;

  private StreetviewModel model;
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

  private void moveToward(double heading) {
    StreetviewLink nearest = model.getLinks().getNearestLink(heading);

    if (nearest != null) {
      model.setPano(new StreetviewPano(nearest.getPano()));
      ros.sendPano(model.getPano());
    }
  }

  private void moveForward() {
    moveToward(model.getPov().getHeading());
  }

  private void moveBackward() {
    moveToward(model.getPov().getHeading() - 180);
  }

  /**
   * Handle outbound web socket messages.
   * 
   * @param message
   *          the message to publish
   */
  @Subscribe
  public void onOutboundWebsocketMessage(OutboundWebsocketMessage message) {
    sendAllWebSocketJsonBuilder(message.getJsonBuilder());
  }

  /**
   * Handle <code>StreetviewLinks</code> updates from the browser.
   * 
   * @param links
   *          updated links
   */
  @Subscribe
  public void onWebsocketLinksMessage(StreetviewLinks links) {
    model.setLinks(links);
    linksDirty = false;
  }

  /**
   * Handle refresh requests from web sockets.
   * 
   * @param refresh
   *          the refresh event
   */
  @Subscribe
  public void onWebsocketRefreshMessage(WebsocketRefreshEvent refresh) {
    websocket.sendPano(model.getPano());
    websocket.sendPov(model.getPov());
  }

  /**
   * Handle outbound Ros messages.
   * 
   * @param message
   *          the message to publish
   */
  @Subscribe
  public void onOutboundRosMessage(OutboundRosMessage message) {
    if (isMaster()) {
      sendOutputJsonBuilder(message.getChannel(), message.getJsonBuilder());
    }
  }

  /**
   * Handle Pano updated from Ros.
   * 
   * @param pano
   *          the new pano
   */
  @Subscribe
  public void onRosPanoMessage(StreetviewPano pano) {
    model.setPano(pano);
    websocket.sendPano(model.getPano());
  }

  /**
   * Handle POV updates from Ros.
   * 
   * @param pov
   *          the new pov
   */
  @Subscribe
  public void onRosPovMessage(StreetviewPov pov) {
    model.setPov(pov);
    websocket.sendPov(model.getPov());
  }

  /**
   * Handles Ros refresh messages by syncing out the view state.
   * 
   * @param refresh
   *          the refresh event
   */
  @Subscribe
  public void onRosRefreshMessage(RefreshEvent refresh) {
    if (isMaster()) {
      ros.sendPov(model.getPov());
      ros.sendPano(model.getPano());
    }
  }

  /**
   * Handle keyboard events from Ros.
   * 
   * @param event
   *          the keyboard event
   */
  @Subscribe
  public void onRosInputKeyEvent(InputKeyEvent event) {
    if (!isMaster() || !isActivated())
      return;

    if (event.getValue() > 0 && !linksDirty) {
      moveForward();
    }
  }

  /**
   * Handle axis change events from Ros.
   * 
   * @param state
   *          the axis state
   */
  @Subscribe
  public void onRosAbsStateEvent(InputAbsState state) {
    if (!isMaster() || !isActivated())
      return;

    double yaw = state.getValue(InputEventCodes.ABS_RZ) * INPUT_SENSITIVITY;

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
            * ((state.getValue(InputEventCodes.ABS_Y) + state.getValue(InputEventCodes.ABS_RX)));

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

  /**
   * Initializes activity configuration, messaging handlers, and state.
   */
  @Override
  public void onActivitySetup() {
    updateConfiguration();

    model = new StreetviewModel();

    eventBus = new AsyncEventBus(getSpaceEnvironment().getExecutorService());
    eventBus.register(this);

    websocket = new StreetviewWebsocket(eventBus, getLog());

    ros = new StreetviewRos(eventBus, getLog());

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
