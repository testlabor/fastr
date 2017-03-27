/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (C) 2001-3 Paul Murrell
 * Copyright (c) 1998-2013, The R Core Team
 * Copyright (c) 2017, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.library.fastrGrid;

import com.oracle.truffle.r.library.fastrGrid.device.GridDevice;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.env.REnvironment;

public final class GridState {
    private RList gpar;
    private RList viewPort;
    private REnvironment gridEnv;
    private double scale = 1;
    private boolean deviceInitialized;
    private int devHoldCount;

    /**
     * Current grob being drawn (for determining the list of grobs to search when evaluating a
     * grobwidth/height unit via gPath). May be RNull or RList.
     */
    private Object currentGrob;

    GridState() {
    }

    public int getDevHoldCount() {
        return devHoldCount;
    }

    public int setDevHoldCount(int devHoldCount) {
        this.devHoldCount = devHoldCount;
        return devHoldCount;
    }

    public void init(REnvironment gridEnv) {
        this.gridEnv = gridEnv;
        this.currentGrob = RNull.instance;
    }

    void initGPar(GridDevice currentDevice) {
        gpar = GPar.createNew(currentDevice);
    }

    /**
     * Returns something like a canonical gpar, or top level gpar. This is used when we need a
     * context to do e.g. unit conversion, but we are in a situation that no context is available.
     */
    public static GPar getInitialGPar(GridDevice device) {
        return GPar.create(GPar.createNew(device));
    }

    public RList getGpar() {
        assert gridEnv != null : "GridState not initialized";
        return gpar;
    }

    public void setGpar(RList gpar) {
        assert gridEnv != null : "GridState not initialized";
        this.gpar = gpar;
    }

    /**
     * Has the current device been initialized for use by grid?
     */
    public boolean isDeviceInitialized() {
        return deviceInitialized;
    }

    public void setDeviceInitialized() {
        this.deviceInitialized = true;
    }

    public RList getViewPort() {
        return viewPort;
    }

    public void setViewPort(RList viewPort) {
        this.viewPort = viewPort;
    }

    public REnvironment getGridEnv() {
        return gridEnv;
    }

    public Object getCurrentGrob() {
        return currentGrob;
    }

    public void setCurrentGrob(Object currentGrob) {
        this.currentGrob = currentGrob;
    }

    public double getScale() {
        return scale;
    }

}
