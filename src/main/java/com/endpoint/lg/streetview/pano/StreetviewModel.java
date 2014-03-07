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

import com.endpoint.lg.support.domain.streetview.StreetviewPov;
import com.endpoint.lg.support.domain.streetview.StreetviewPano;
import com.endpoint.lg.support.domain.streetview.StreetviewLinks;

/**
 * State container for Street View data.
 * 
 * @author Matt Vollrath <matt@endpoint.com>
 */
public class StreetviewModel {
  /**
   * Default heading for the POV.
   */
  public static final double DEFAULT_HEADING = 0.0;

  /**
   * Default pitch for the POV.
   */
  public static final double DEFAULT_PITCH = 0.0;

  /**
   * Default panoid.
   */
  public static final String DEFAULT_PANOID = null;

  private StreetviewPov pov;
  private StreetviewPano pano;
  private StreetviewLinks links;

  public StreetviewPov getPov() {
    return pov;
  }

  public void setPov(StreetviewPov pov) {
    this.pov = pov;
  }

  public StreetviewPano getPano() {
    return pano;
  }

  public void setPano(StreetviewPano pano) {
    this.pano = pano;
  }

  public StreetviewLinks getLinks() {
    return links;
  }

  public void setLinks(StreetviewLinks links) {
    this.links = links;
  }

  public StreetviewModel() {
    pov = new StreetviewPov(DEFAULT_HEADING, DEFAULT_PITCH);
    pano = new StreetviewPano(DEFAULT_PANOID);
    links = new StreetviewLinks();
  }
}
