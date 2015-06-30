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

requirejs.onError = function (err) {
  console.error('Scheduling page reload...');
  setTimeout(window.location.reload, 1000);
  throw err;
};

requirejs.config({
  paths: {
    // *** RequireJS Plugins
    'async': 'lib/require/async',
    // *** Dynamic Configuration
    'config': '/is.config',
    // *** Common Deps
    'bigl': 'bigl',
    //'fields': 'common/fields',
    'stapes': 'lib/stapes/stapes.min',
    'jquery': 'lib/jquery/jquery-2.0.3.min',
    'jquery-private': 'common/jquery-private',
    'googlemaps': 'streetview/googlemaps',
    'sv_svc': 'streetview/sv_svc',
    'validate': 'streetview/validate',
    'is': 'is/is-1.0.0',
    'is.connect': 'is/is.connect-1.0.0',
    'socket': 'socket'
  },
  shim: {
    'config': { exports: 'IS.Configuration' },
    'is': { exports: 'IS' },
    'is.connect': { exports: 'IS' },
    'googlemaps': {
      deps: [
        'async!http://maps.googleapis.com/maps/api/js?v=3.exp&sensor=false!callback'
      ]
    }
  },
  map: {
    '*': { 'jquery': 'jquery-private' },
    'jquery-private': { 'jquery': 'jquery' }
  }
});

require(
['config', 'is', 'streetview', 'viewsync'],
function(
  config,
  IS,
  StreetViewModule,
  ViewSyncModule
) {

  // *** initialize the StreetView module
  var sv = new StreetViewModule(document.getElementById('pano'));

  // *** initialize the ViewSync module
  var viewsync = new ViewSyncModule();

  // *** link StreetView canvas size changes to ViewSync
  sv.on('size_changed', function(fov) {
    viewsync.resize(fov);
  });

  // *** link ViewSync state events to StreetView
  sv.on('ready', function() {
    viewsync.on('pov_changed', function(pov) {
      sv.setPov(pov);
    });
    viewsync.on('pano_changed', function(panoid) {
      sv.setPano(panoid);
    });
  });

  sv.on('refresh', function() {
    viewsync.refresh();
  });

  // *** send view updates if directed
  if (config['lg.streetview.viewSync.send'] == "true") {
    viewsync.on('ready', function() {
      sv.on('pov_changed', function(pov) {
        viewsync.sendPov(pov);
      });
      sv.on('pano_changed', function(pano) {
        viewsync.sendPano(pano);
      });
    });
  }

  // *** send link updates if directed
  if (config['lg.streetview.viewSync.linkCapture'] == "true") {
    viewsync.on('ready', function() {
      sv.on('links_changed', function(links) {
        viewsync.sendLinks(links);
      });
    });
  }

  // *** create the Maps API objects
  sv.init();

  // *** connect the ViewSync socket
  viewsync.init();
});
