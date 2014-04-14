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
package com.oracle.truffle.r.nodes.builtin;

import java.io.*;
import java.util.*;

import org.antlr.runtime.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.function.*;
import com.oracle.truffle.r.parser.*;
import com.oracle.truffle.r.parser.ast.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.REnvironment.PutException;
import com.oracle.truffle.r.runtime.RContext.*;
import com.oracle.truffle.r.runtime.data.*;

/**
 * The engine for the FastR implementation. Handles parsing and evaluation. There is exactly one
 * instance of this class, stored in {link #singleton}.
 */
public final class REngine implements RBuiltinLookupProvider {

    private static REngine singleton = new REngine();
    private static final RContext context = RContext.linkRBuiltinLookupProvider(singleton);
    private static boolean crashOnFatalError;

    private REngine() {
    }

    public RBuiltinLookup getRBuiltinLookup() {
        return RDefaultBuiltinPackages.getInstance();
    }

    /**
     * Initialize the engine.
     *
     * @param commandArgs
     * @param consoleHandler for console input/output
     * @param crashOnFatalErrorArg if {@code true} any unhandled exception will terminate the
     *            process.
     * @return a {@link VirtualFrame} that can be passed to
     *         {@link #parseAndEval(String, VirtualFrame, boolean)}
     */
    public static VirtualFrame initialize(String[] commandArgs, ConsoleHandler consoleHandler, boolean crashOnFatalErrorArg, boolean headless) {
        crashOnFatalError = crashOnFatalErrorArg;
        RContext.setRuntimeState(commandArgs, consoleHandler, headless);
        VirtualFrame globalFrame = createVirtualFrame();
        VirtualFrame baseFrame = createVirtualFrame();
        REnvironment.initialize(globalFrame, baseFrame);
        RBuiltinPackage.initialize();
        RRuntime.initialize();
        return globalFrame;
    }

    /**
     * Create a {@link VirtualFrame} for use in {@link #parseAndEval} for accumulating the results
     * from evaluating expressions in an interactive context. Such a value cannot be stored in an
     * object field, so must be passed as an argument.
     */
    private static VirtualFrame createVirtualFrame() {
        return Truffle.getRuntime().createVirtualFrame(null, RArguments.create(), new FrameDescriptor());
    }

    public static RContext getContext() {
        return context;
    }

    /**
     * Parse and evaluate {@code rscript}. Value of {@code globalFrame} may be null. If
     * {@code printResult == true}, the result of the evaluation is printed to the console.
     */
    public static Object parseAndEval(String rscript, VirtualFrame globalFrame, boolean printResult) {
        return parseAndEvalImpl(new ANTLRStringStream(rscript), context.getSourceManager().getFakeFile("<shell_input>", rscript), globalFrame, printResult);
    }

    /**
     *
     * This is intended for use by the unit test environment, where a fresh global environment is
     * desired for each evaluation.
     */
    public static Object parseAndEval(String rscript, boolean printResult) {
        VirtualFrame frame = createVirtualFrame();
        REnvironment.resetGlobalEnv(frame.materialize());
        return parseAndEvalImpl(new ANTLRStringStream(rscript), context.getSourceManager().getFakeFile("<shell_input>", rscript), frame, printResult);
    }

    public static class ParseException extends Exception {
        private static final long serialVersionUID = 1L;

        public ParseException(String msg) {
            super(msg);
        }
    }

    public static Object[] parse(String rscript) throws ParseException {
        try {
            Sequence seq = (Sequence) ParseUtil.parseAST(new ANTLRStringStream(rscript), context.getSourceManager().getFakeFile("<parse_input>", rscript));
            ASTNode[] exprs = seq.getExprs();
            RExpression[] result = new RExpression[exprs.length];
            for (int i = 0; i < exprs.length; i++) {
                result[i] = new RExpression(exprs[i]);
            }
            return result;
        } catch (RecognitionException ex) {
            throw new ParseException(ex.getMessage());
        }
    }

    public static Object eval(RExpression expr, REnvironment envir, @SuppressWarnings("unused") REnvironment enclos) throws PutException {
        CallTarget callTarget = transformToCallTarget((ASTNode) expr.getASTNode(), envir);
        // to evaluate this we must create a new VirtualFrame
        VirtualFrame frame = createVirtualFrame();
        Object result = runGlobal(callTarget, frame, false);
        // now copy the values into the environment we were supposed to evaluate this in.
        FrameDescriptor fd = frame.getFrameDescriptor();
        for (FrameSlot slot : fd.getSlots()) {
            envir.put(slot.getIdentifier().toString(), frame.getValue(slot));
        }
        return result;
    }

    private static Object parseAndEvalImpl(ANTLRStringStream stream, Source source, VirtualFrame globalFrame, boolean printResult) {
        try {
            return runGlobal(parseToCallTarget(stream, source), globalFrame, printResult);
        } catch (RecognitionException | RuntimeException e) {
            context.getConsoleHandler().println("Exception while parsing: " + e);
            e.printStackTrace();
            return null;
        }
    }

    private static CallTarget parseToCallTarget(ANTLRStringStream stream, Source source) throws RecognitionException {
        return transformToCallTarget(ParseUtil.parseAST(stream, source), REnvironment.globalEnv());
    }

    private static CallTarget transformToCallTarget(ASTNode astNode, REnvironment environment) {
        RTruffleVisitor transform = new RTruffleVisitor(environment);
        RNode node = transform.transform(astNode);
        REnvironment.FunctionDefinition rootNodeEnvironment = new REnvironment.FunctionDefinition(REnvironment.globalEnv());
        FunctionDefinitionNode rootNode = new FunctionDefinitionNode(null, rootNodeEnvironment, node, RArguments.EMPTY_OBJECT_ARRAY, "<main>");
        CallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        return callTarget;
    }

    private static Object runGlobal(CallTarget callTarget, VirtualFrame globalFrame, boolean printResult) {
        Object result = null;
        try {
            try {
                result = ((DefaultCallTarget) callTarget).getRootNode().execute(globalFrame);
            } catch (ControlFlowException cfe) {
                throw RError.getNoLoopForBreakNext(null);
            }
            if (printResult) {
                printResult(result);
            }
            reportWarnings(false);
        } catch (RError e) {
            reportRError(e);
        } catch (Throwable e) {
            reportImplementationError(e);
        }
        return result;
    }

    private static void printResult(Object result) {
        if (RContext.isVisible()) {
            RFunction function = RContext.getLookup().lookup("print");
            RRuntime.toString(function.call(null, RArguments.create(function, new Object[]{result})));
        }
    }

    private static void reportRError(RError e) {
        context.getConsoleHandler().printErrorln(e.toString());
        reportWarnings(true);
    }

    private static void reportImplementationError(Throwable e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(out));
        context.getConsoleHandler().printErrorln(RRuntime.toString(out));
        // R suicide, unless, e.g., we are running units tests.
        // We don't call quit as the system is broken.
        if (crashOnFatalError) {
            System.exit(2);
        }
    }

    private static void reportWarnings(boolean inAddition) {
        List<String> evalWarnings = context.extractEvalWarnings();
        ConsoleHandler consoleHandler = context.getConsoleHandler();
        // GnuR outputs warnings to the stderr, so we do too
        if (evalWarnings != null && evalWarnings.size() > 0) {
            if (inAddition) {
                consoleHandler.printError("In addition: ");
            }
            if (evalWarnings.size() == 1) {
                consoleHandler.printErrorln("Warning message:");
                consoleHandler.printErrorln(evalWarnings.get(0));
            } else {
                consoleHandler.printErrorln("Warning messages:");
                for (int i = 0; i < evalWarnings.size(); i++) {
                    consoleHandler.printErrorln((i + 1) + ":");
                    consoleHandler.printErrorln("  " + evalWarnings.get(i));
                }
            }
        }
    }

}
