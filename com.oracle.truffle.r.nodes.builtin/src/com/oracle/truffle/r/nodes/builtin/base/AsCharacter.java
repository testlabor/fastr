/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RBuiltinKind.*;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.nodes.builtin.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;

@RBuiltin(name = "as.character", kind = PRIMITIVE, parameterNames = {"x", "..."})
public abstract class AsCharacter extends RBuiltinNode {

    @Child private CastStringNode castStringNode;
    @Child private DispatchedCallNode dcn;
    @Child private CastToVectorNode castVector;

    private void initCast() {
        if (castStringNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castStringNode = insert(CastStringNodeFactory.create(null, false, false, false, false));
        }
    }

    private RAbstractVector castVector(VirtualFrame frame, Object value) {
        if (castVector == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castVector = insert(CastToVectorNodeFactory.create(null, false, false, false, false));
        }
        return (RAbstractVector) castVector.executeObject(frame, value);
    }

    private String castString(VirtualFrame frame, int o) {
        initCast();
        return (String) castStringNode.executeString(frame, o);
    }

    private String castString(VirtualFrame frame, double o) {
        initCast();
        return (String) castStringNode.executeString(frame, o);
    }

    private String castString(VirtualFrame frame, byte o) {
        initCast();
        return (String) castStringNode.executeString(frame, o);
    }

    private RStringVector castStringVector(VirtualFrame frame, Object o) {
        initCast();
        return (RStringVector) ((RStringVector) castStringNode.executeString(frame, o)).copyDropAttributes();
    }

    @Specialization
    protected String doInt(VirtualFrame frame, int value) {
        controlVisibility();
        return castString(frame, value);
    }

    @Specialization
    protected String doDouble(VirtualFrame frame, double value) {
        controlVisibility();
        return castString(frame, value);
    }

    @Specialization
    protected String doLogical(VirtualFrame frame, byte value) {
        controlVisibility();
        return castString(frame, value);
    }

    @Specialization
    protected String doString(String value) {
        controlVisibility();
        return value;
    }

    @Specialization
    protected String doSymbol(RSymbol value) {
        controlVisibility();
        return value.getName();
    }

    @Specialization
    protected RStringVector doNull(@SuppressWarnings("unused") RNull value) {
        controlVisibility();
        return RDataFactory.createStringVector(0);
    }

    @Specialization(guards = "!isObject")
    protected RStringVector doStringVector(RStringVector vector) {
        controlVisibility();
        return RDataFactory.createStringVector(vector.getDataCopy(), vector.isComplete());
    }

    @Specialization
    protected RStringVector doList(@SuppressWarnings("unused") RList list) {
        controlVisibility();
        throw new UnsupportedOperationException("list type not supported for as.character - requires deparsing");
    }

    @Specialization(guards = "!isObject")
    protected RStringVector doVector(VirtualFrame frame, RAbstractVector vector) {
        controlVisibility();
        return castStringVector(frame, vector);
    }

    @Specialization(guards = "isObject")
    protected Object doObject(VirtualFrame frame, RAbstractVector vector) {
        controlVisibility();
        if (dcn == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            dcn = insert(DispatchedCallNode.create("as.character", RRuntime.USE_METHOD, getSuppliedArgsNames()));
        }
        try {
            return dcn.executeInternal(frame, vector.getClassHierarchy(), new Object[]{vector});
        } catch (RError e) {
            return castStringVector(frame, vector);
        }
    }

    // TODO: this shold be handled by a generic function
    @Specialization
    protected Object doFactor(VirtualFrame frame, RFactor value) {
        controlVisibility();
        Object attr = value.getVector().getAttr(RRuntime.LEVELS_ATTR_KEY);
        if (attr == null) {
            return RNull.instance;
        } else {
            RAbstractStringVector vec = (RAbstractStringVector) castVector(frame, attr);
            String[] data = new String[value.getLength()];
            if (vec.getLength() == 0) {
                Arrays.fill(data, RRuntime.STRING_NA);
                return RDataFactory.createStringVector(data, RDataFactory.INCOMPLETE_VECTOR);
            } else {
                for (int i = 0; i < data.length; i++) {
                    data[i] = vec.getDataAt(value.getVector().getDataAt(i) - 1);
                }
                return RDataFactory.createStringVector(data, RDataFactory.COMPLETE_VECTOR);
            }
        }
    }

    protected boolean isObject(RAbstractVector vector) {
        return vector.isObject();
    }
}
