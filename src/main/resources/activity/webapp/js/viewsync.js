/*
 ** Copyright (C) 2015 End Point Corporation
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

define([ 'config', 'bigl', 'validate', 'stapes', 'socket' ], function(config,
    L, validate, Stapes, io) {

  var ViewSyncModule = Stapes.subclass({

    constructor : function() {
      this.debug = (config['space.activity.webapp.browser.debug'] == 'true');
      this.yawShift = null;
      this.pitchShift = null;
      this.remotePov = {
        heading : null,
        pitch : null
      };
      this.remotePano = null;
    },

    // PUBLIC

    // *** resize({hfov, vfov})
    // should be called when the streetview object reports a size change
    resize : function(fov) {
      if (!validate.fov(fov)) {
        L.error('ViewSync: bad fov to resize!');
        return;
      }

      this.yawShift = Number(config['lg.streetview.viewSync.yawOffset']) * fov.hfov;
      this.pitchShift = Number(config['lg.streetview.viewSync.pitchOffset']) * fov.vfov;

      if (this.remotePov !== null) {
        this._applyPov(this._translatePov(this.remotePov));
      }
    },

    // *** sendPov(google.maps.StreetViewPov)
    // send a view change to the ViewSync relay
    // ignore redundant sendPov
    sendPov : function(pov) {
      if (this.remotePov.heading !== pov.heading
          || this.remotePov.pitch !== pov.pitch) {
        this.remotePov = pov;
        io.send('pov', pov);
      }
    },

    // *** sendPano(panoid)
    // send a pano change to the ViewSync relay
    // ignore redundant sendPano
    sendPano : function(panoid) {
      if (this.remotePano != panoid) {
        if (this.debug)
          L.debug('ViewSync: sendPano', panoid);

        this.remotePano = pano;
        io.send('pano', {
          panoid : panoid
        });
      }
    },
    
    // *** sendLinks(links)
    // send the current pano's links to the ViewSync relay
    sendLinks : function(links) {
      io.send('links', {
        links : links
      })
    },

    // *** refresh()
    // request the current state from the relay
    refresh : function() {
      io.send('refresh');
    },

    // *** init()
    // should be called once to start socket communications
    init : function() {
      if (this.debug)
        console.debug('ViewSync: init');

      var self = this;

      io.on('pano', function(pano) {
        if (!validate.panoid(pano.panoid)) {
          L.error('ViewSync: bad panoid from socket!');
          return;
        }

        self._recvPano(pano.panoid);
      });

      io.on('pov', function(pov) {
        if (!validate.pov(pov)) {
          L.error('ViewSync: bad pov from socket!');
          return;
        }

        pov.heading = parseFloat(pov.heading);
        pov.pitch = parseFloat(pov.pitch);
        self._recvPov(pov);
      });

      if (this.debug)
        console.debug('ViewSync: ready');

      this.emit('ready');
    },

    // PRIVATE

    // *** _applyPov(google.maps.StreetViewPov)
    // emits a view change to ViewSync listeners
    _applyPov : function(pov) {
      this.emit('pov_changed', pov);
    },

    // *** _applyPano(panoid)
    // emits a pano change to ViewSync listeners
    _applyPano : function(panoid) {
      this.emit('pano_changed', panoid);
    },

    // *** _translatePov(google.maps.StreetViewPov)
    // translate the point of view by local offsets
    _translatePov : function(pov) {
      var translated = {
        heading : pov.heading + this.yawShift,
        pitch : pov.pitch + this.pitchShift
      };
      return translated;
    },

    // *** _recvPov(google.maps.StreetViewPov)
    // unpack and process the pov from a relay message
    _recvPov : function(pov) {
      if (pov.heading !== this.remotePov.heading
          || pov.pitch !== this.remotePov.pitch) {
        this.remotePov = pov;
        this._applyPov(this._translatePov(pov));
      }
    },

    // *** _recvPano(panoid)
    // unpack and process the panoid from a relay message
    _recvPano : function(panoid) {
      if (pano.panoid !== this.remotePano) {
        this.remotePano = panoid;
        this._applyPano(panoid);
      }
    }
  });

  return ViewSyncModule;
});
