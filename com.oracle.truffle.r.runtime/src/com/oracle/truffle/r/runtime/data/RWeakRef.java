/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.runtime.data;

import com.oracle.truffle.r.runtime.RType;

public final class RWeakRef extends RObject implements RTruffleObject, RTypedValue {

    private int typedValueInfo = ASCII_MASK_SHIFTED;

    private final Object key;
    private final Object value;
    @SuppressWarnings("unused") private final Object fin;
    @SuppressWarnings("unused") private final boolean onexit;

    public RWeakRef(Object key, Object value, Object fin, boolean onexit) {
        this.key = key;
        this.value = value;
        this.fin = fin;
        this.onexit = onexit;
    }

    public Object getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public RType getRType() {
        return RType.WeakRef;
    }

    @Override
    public int getTypedValueInfo() {
        return typedValueInfo;
    }

    @Override
    public void setTypedValueInfo(int value) {
        typedValueInfo = value;
    }
}
