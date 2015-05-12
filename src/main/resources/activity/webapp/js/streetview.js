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

define(
    ['config', 'bigl', 'validate', 'stapes', 'googlemaps'],
    function(config, L, validate, Stapes, GMaps) {
      var StreetViewModule = Stapes.subclass({

        // street view horizontal field of view per zoom level
        // varies per render mode
        SV_HFOV_TABLES: {
          "webgl": [127, 90, 53.5, 28.125, 14.25],
          "html4": [180, 90, 45, 22.5, 11.25],
          "html5": [127, 90, 53.5, 28.125, 14.25],
          "flash": [180, 90, 45, 22.5, 11.25]
        },

        constructor: function($canvas) {
          this.$canvas = $canvas;
          this.debug = (config['space.activity.webapp.browser.debug'] == 'true');
          this.zoom = Number(config['lg.streetview.gapi.zoom']);
          this.renderMode = config['lg.streetview.gapi.renderMode'];
          this.showLinks = (config['lg.streetview.gapi.showLinks'] == 'true');
          this.sendView = (config['lg.streetview.viewSync.send'] == 'true');
          this.sendLinks = (config['lg.streetview.viewSync.linkCapture'] == 'true');
          this.map = null;
          this.streetview = null;
          this.meta = null;
          this.pov = null;
          this.fov_table = this.SV_HFOV_TABLES[this.renderMode];
          this.hfov = this.fov_table[this.zoom];
          this.vfov = null;
        },

        // PUBLIC

        // *** init()
        // should be called once when ready to set Maps API into motion
        init: function() {
          if (this.debug)
            console.debug('StreetView: init');

          var self = this;

          // *** ensure success of Maps API load
          if (typeof GMaps === 'undefined') L.error('Maps API not loaded!');

          // *** initial field-of-view
          this._resize();

          // *** create a local streetview query object
          this.sv_svc = new GMaps.StreetViewService();

          // *** options for the map object
          // the map will never be seen, but we can still manipulate the experience
          // with these options.
          var mapOptions = {
              disableDefaultUI: true,
              center: new GMaps.LatLng(45,45),
              backgroundColor: "black",
              zoom: 8
          };

          // *** options for the streetview object
          var svOptions = {
              visible: true,
              disableDefaultUI: true,
              linksControl: this.showLinks
          };

          // *** init map object
          this.map = new GMaps.Map(
              this.$canvas,
              mapOptions
          );

          // *** init streetview object
          this.streetview = new GMaps.StreetViewPanorama(
              this.$canvas,
              svOptions
          );

          // *** init streetview pov
          this.streetview.setPov({
            heading: 0,
            pitch: 0,
            zoom: this.zoom
          });

          // *** set the display mode as specified in global configuration
          this.streetview.setOptions({ mode: this.renderMode });

          // *** apply the custom streetview object to the map
          this.map.setStreetView( this.streetview );

          // *** events for viewSync sender
          if (this.sendView) {
            // *** handle view change events from the streetview object
            GMaps.event.addListener(this.streetview, 'pov_changed', function() {
              var pov = self.streetview.getPov();

              if (!self.pov || pov.heading != self.pov.heading || pov.pitch != self.pov.pitch || pov.zoom != self.pov.zoom) {
                self.emit('pov_changed', pov);
                self.pov = pov;
              }
            });

            // *** handle pano change events from the streetview object
            GMaps.event.addListener(this.streetview, 'pano_changed', function() {
              var panoid = self.streetview.getPano();

              if (panoid != self.pano) {
                self.emit('pano_changed', panoid);
                self.pano = panoid;
              }
            });
          }

          // *** events for viewSync linkCapture
          if (this.sendLinks) {
            GMaps.event.addListener(this.streetview, 'links_changed', function() {
              self.emit('links_changed', self.streetview.getLinks());
            });
          }

          // *** disable <a> tags at the bottom of the canvas
          GMaps.event.addListenerOnce(this.map, 'idle', function() {
            var links = self.$canvas.getElementsByTagName("a");
            var len = links.length;
            for (var i = 0; i < len; i++) {
              links[i].style.display = 'none';
              links[i].onclick = function() {return(false);};
            }
          });

          // *** request the last known state from the server
          this.on('ready', function() {
            self.emit('refresh');
          });

          // *** wait for an idle event before reporting module readiness
          GMaps.event.addListenerOnce(this.map, 'idle', function() {
            if (this.debug)
              console.debug('StreetView: ready');

            self.emit('ready');
          });

          // *** handle window resizing
          window.addEventListener('resize',  function() {
            self._resize();
          });
        },

        // *** setPano(panoid)
        // switch to the provided pano, immediately
        setPano: function(panoid) {
          if (!validate.panoid(panoid)) {
            L.error('StreetView: bad panoid to setPano!');
            return;
          }

          if (panoid != this.streetview.getPano()) {
            this.streetview.setPano(panoid);
            this.pano = panoid;
          } else {
            console.warn('StreetView: ignoring redundant setPano');
          }
        },

        // *** setPov(GMaps.StreetViewPov)
        // set the view to the provided pov, immediately
        setPov: function(pov) {
          if (!validate.number(pov.heading) || !validate.number(pov.pitch)) {
            L.error('StreetView: bad pov to setPov!');
            return;
          }

          if (!this.pov || pov.heading != this.pov.heading || pov.pitch != this.pov.pitch || pov.zoom != this.pov.zoom) {
            this.pov = pov;
                // This doesn't return
            this.streetview.setPov(pov);
          }
        },

        // PRIVATE

        // *** _resize()
        // called when the canvas size has changed
        _resize: function() {
          var screenratio = window.innerHeight / window.innerWidth;
          this.vfov = this.hfov * screenratio;
          this.emit('size_changed', {hfov: this.hfov, vfov: this.vfov});

          if (this.debug)
            console.debug('StreetView: resize', this.hfov, this.vfov);
        }
      });

      return StreetViewModule;
    });
