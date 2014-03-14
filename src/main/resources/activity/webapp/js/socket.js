/*
 ** Copyright 2013 Google Inc.
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **    http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

define(
    ['config', 'is.connect', 'stapes'],
    function(config, ISConnect, Stapes) {
      var SocketHandler = Stapes.subclass({
        constructor: function() {
          var self = this;

          this.debug = (config['space.activity.webapp.browser.debug'] == 'true');
          this.port = Number(config['space.activity.webapp.web.server.port']);

          this.socket = new ISConnect.Connection();

          this.socket.onConnect(function() {
            if (self.debug) console.debug('Socket::open');

            self.emit('_connect');
          });

          this.socket.onDisconnect(function() {
            if (self.debug) console.debug('Socket::closed');

            self.emit('_disconnect');
          });

          this.socket.onMessage(function(type, msg) {
            if (self.debug) console.debug('Socket::recv', type, msg);

            self.emit(type, msg);
          });

          this.socket.connect("127.0.0.1", this.port, "/websocket");
        },

        send: function(type, msg) {
          msg = msg || {};

          if (this.debug) console.debug('Socket::send', type, msg);

          this.socket.sendMessage(type, msg);
        },
      });

      return new SocketHandler();
    });
