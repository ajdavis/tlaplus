/*******************************************************************************
 * Copyright (c) 2020 Microsoft Research. All rights reserved. 
 *
 * The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. 
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *   Markus Alexander Kuppe - initial API and implementation
 ******************************************************************************/
package tlc2.tool.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import tla2sany.semantic.ASTConstants;
import tla2sany.semantic.OpApplNode;
import tla2sany.semantic.SemanticNode;
import tlc2.TLCGlobals;
import tlc2.debug.IDebugTarget;
import tlc2.tool.Action;
import tlc2.tool.EvalControl;
import tlc2.tool.EvalException;
import tlc2.tool.IActionItemList;
import tlc2.tool.INextStateFunctor;
import tlc2.tool.IStateFunctor;
import tlc2.tool.ITool;
import tlc2.tool.TLCState;
import tlc2.tool.TLCStateFun;
import tlc2.tool.TLCStateMutExt;
import tlc2.tool.coverage.CostModel;
import tlc2.util.Context;
import tlc2.util.SetOfStates;
import tlc2.value.IValue;
import tlc2.value.impl.Value;
import util.Assert.TLCRuntimeException;
import util.FilenameToStream;

@SuppressWarnings("serial")
public class DebugTool extends Tool {

	private static final Set<Integer> KINDS = new HashSet<>(
			Arrays.asList(ASTConstants.NumeralKind, ASTConstants.DecimalKind, ASTConstants.StringKind));
	
	private final IDebugTarget target;
	
	/**
	 * The debugger doesn't handle the evaluation of all expressions. For example,
	 * it ignores the evaluation of expression related to liveness. Instead of
	 * inferring what calls correspond to liveness expressions, the liveness-related
	 * code gets this FastTool.
	 */
	private final FastTool fastTool;

	private EvalMode mode = EvalMode.Const;
	
	/**
	 * Contrary to EvalControl, EvalMode does *not* determine the control path in
	 * Tool. Its purpose is to infer if the debugger should create a TLCStackFrame,
	 * TLCStateStackFrame, or a TLCActionStackFrame.
	 * <p>
	 * We considered to extend EvalControl and use e.g. its MSBs to store the
	 * current scope such as constant-, state-, or action-level expressions.
	 * However, Java module overrides always reset control to its default value
	 * (EvalControl.Clear).
	 */
	public enum EvalMode {
		Const, State, Action, Debugger;
	}
	
	public DebugTool(String mainFile, String configFile, FilenameToStream resolver, IDebugTarget target) {
		this(mainFile, configFile, resolver, Mode.MC_DEBUG, target);
	}
	
	public DebugTool(String mainFile, String configFile, FilenameToStream resolver, Mode mode, IDebugTarget target) {
		super(mainFile, configFile, resolver, mode);
		
		// This and FastTool share state.  Do not evaluate things concurrently.
		this.fastTool = new FastTool(this);
		// Evaluation related to fingerprinting should not/cannot use DebugTool. There
		// is nothing that a user could debug except, perhaps, for EvaluationExceptions.
		TLCStateMutExt.setTool(this.fastTool);
		
		this.target = target.setTool(this);
	}

	// 88888888888888888888888888888888888888888888888888888888888888888888888888 //

	@Override
	public boolean isValid(Action act, TLCState state) {
		mode = EvalMode.State;
		return this.isValid(act, state, TLCState.Empty);
	}

	@Override
	public final IValue eval(SemanticNode expr, Context ctxt) {
		mode = EvalMode.Const;
		return this.evalImpl(expr, Context.Empty, TLCState.Empty, TLCState.Empty, EvalControl.Clear,
				CostModel.DO_NOT_RECORD);
	}
	
	@Override
	public final IValue eval(SemanticNode expr, Context c, TLCState s0) {
		return this.eval(expr, c, s0, CostModel.DO_NOT_RECORD);
	}

	@Override
	public final IValue eval(SemanticNode expr, Context c, TLCState s0, CostModel cm) {
		mode = EvalMode.State;
		return this.evalImpl(expr, c, s0, TLCState.Empty, EvalControl.Clear, cm);
	}

	/**
	 * s0 might be a fully or partially evaluated state including TLCState.Empty.
	 * s1 might be a fully or partially evaluated state including TLCState.Empty, or null.
	 * control can be anything such as EvalControl.Init
	 */
	@Override
	public final Value eval(final SemanticNode expr, final Context c, final TLCState s0, final TLCState s1,
			final int control, final CostModel cm) {
		if (mode == EvalMode.Debugger) {
			return fastTool.evalImpl(expr, c, s0, s1, control, cm);
		}
		if (s1 == null || EvalControl.isPrimed(control) || EvalControl.isEnabled(control)) {
			return fastTool.evalImpl(expr, c, s0, s1, control, cm);
		}
		if (mode == EvalMode.Action && s1.getAction() == null) {
			// We are in mode action but s1 has no action. This is the case if the UNCHANGED
			// of an action is evaluated. We could set mode to State or ignore these frames.
			return fastTool.evalImpl(expr, c, s0, s1, control, cm);
		}
		if (EvalControl.isInit(control)) {
			mode = EvalMode.State;
			return evalImpl(expr, c, s0, s1, control, cm);
		}
		if (EvalControl.isConst(control)) {
			mode = EvalMode.Const;
			return evalImpl(expr, c, s0, s1, control, cm);
		}
		if (mode == EvalMode.State && s1 != null && s1.noneAssigned()) {
			return evalImpl(expr, c, s0, s1, control, cm);
		}
		if (mode == EvalMode.Const && s0.noneAssigned() && s1.noneAssigned()) {
			return evalImpl(expr, c, s0, s1, control, cm);
		}
		mode = EvalMode.Action;
		return evalImpl(expr, c, s0, s1, control, cm);
	}

	@Override
	protected final Value evalImpl(final SemanticNode expr, final Context c, final TLCState s0, final TLCState s1,
			final int control, CostModel cm) {
		if (isInitialize()) {
			// Cannot delegate to fastTool that is null during initialization.
			return super.evalImpl(expr, c, s0, s1, control, cm);
		}
		if (isLiveness(control, s0, s1) || isLeaf(expr) || isBoring(expr, c)) {
			return fastTool.evalImpl(expr, c, s0, s1, control, cm);
		}
		if (mode == EvalMode.Debugger) {
			// Skip debugging when evaluation was triggered by the debugger itself. For
			// example, when LazyValues get unlazied.
			return fastTool.evalImpl(expr, c, s0, s1, control, cm);
		}
		if (mode == EvalMode.Const) {
			assert s0.noneAssigned() && s1.noneAssigned();
			// s0 and s1 can be dummies that are passed by some value instances or Tool
			// during the evaluation of the init-predicate or other const-level expressions.
			return constLevelEval(expr, c, s0, s1, control, cm);
		} else if (mode == EvalMode.State) {
			assert s1 == null || s1.noneAssigned();
			return stateLevelEval(expr, c, s0, s1, control, cm);
		} else {
			return actionLevelEval(expr, c, s0, s1, control, cm);
		}
	}

	private boolean isLeaf(SemanticNode expr) {
		// These nodes don't seem interesting to users. They are leaves and we don't
		// care to see how TLC figures out that then token 1 evaluates to the IntValue 1.
		return KINDS.contains(expr.getKind());
	}

	private boolean isInitialize() {
		// target is null during instantiation of super (see constructor above), ie.
		// eager evaluation of operators in SpecProcessor. mainChecker is null
		// while SpecProcessor processes constant definitions, ...
		return target == null || (TLCGlobals.mainChecker == null && TLCGlobals.simulator == null);
	}

	private boolean isLiveness(int control, TLCState s0, TLCState s1) {
		if (EvalControl.isEnabled(control) || EvalControl.isPrimed(control)) {
			// If EvalControl is set to primed or enabled, TLC is evaluating an ENABLED expr.
			// TLCStateFun are passed in when enabled is evaluated. However, it is also
			// possible for enabled to be replaced with primed. At any rate, there is no
			// point evaluating ENABLED expr.
			return true;
		}
		if (s0 instanceof TLCStateFun || s1 instanceof TLCStateFun) {
			// If EvalControl is set to primed or enabled, TLC is evaluating an ENABLED expr.
			// (see previous if branch).  However, if expr is built from an operator with a
			// Java module override, control is cleared/reset and the only indicator that
			// evaluation is in the scope of enabled, is TLCStateFunc.
			return true;
		}
		return false;
	}

	private boolean isBoring(final SemanticNode expr, Context c) {
//		if (c.isEmpty()) {
//		// It is tempting to ignore also frames with an empty Context. However, ASSUMES
//		// for example don't have a Context. Perhaps, we should track the level here and
//		// ignore frames with empty Context for level greater than zero (or whatever the
//		// base-level is).
//			return true;
//		}
		// Skips N and Nat in:
		//     CONSTANT N
		//     ASSUME N \in Nat
		// or the S, the f, and the 1..3 of:
		//     LET FS == INSTANCE FiniteSets
        //              Perms(S, a, b) == 
        //                { f \in [S -> S] :
        //                      /\ S = { f[x] : x \in DOMAIN f }
        //                      /\ \E n, m \in DOMAIN f: /\ f[n] = a
        //                                  /\ f[m] = b
        //                                  /\ n - m \in {1, -1}               
        //                }
        //     IN FS!Cardinality(Perms(1..3, 1, 2)) = 4
		// TODO: For now, don't filter boring frames because we have not clear
		// understanding of what constitutes a boring frame. Built-in operators
		// are candidates, but for now I find this more confusing than helpful.'x'x
		return false;
	}

	private Value actionLevelEval(SemanticNode expr, Context c, TLCState s0, TLCState s1, int control, CostModel cm) {
		try {
			target.pushFrame(this, expr, c, s0, s1.getAction(), s1);
			final Value v = super.evalImpl(expr, c, s0, s1, control, cm);
			target.popFrame(this, expr, c, v, s0, s1);
			return v;
		} catch (TLCRuntimeException | EvalException e) {
			target.pushExceptionFrame(this, expr, c, s0, s1.getAction(), s1, e);
			throw e;
		}
	}

	private Value stateLevelEval(SemanticNode expr, Context c, TLCState s0, TLCState s1, int control, CostModel cm) {
		try {
			target.pushFrame(this, expr, c, s0);
			final Value v = super.evalImpl(expr, c, s0, s1, control, cm);
			target.popFrame(this, expr, c, v, s0);
			return v;
		} catch (TLCRuntimeException | EvalException e) {
			target.pushExceptionFrame(this, expr, c, s0, e);
			throw e;
		}
	}

	private Value constLevelEval(SemanticNode expr, Context c, TLCState s0, TLCState s1, int control, CostModel cm) {
		try {
			target.pushFrame(this, expr, c);
			final Value v = super.evalImpl(expr, c, s0, s1, control, cm);
			target.popFrame(this, expr, c, v);
			return v;
		} catch (TLCRuntimeException | EvalException e) {
			target.pushExceptionFrame(this, expr, c, e);
			throw e;
		}
	}

	@Override
	protected final Value evalAppl(final OpApplNode expr, final Context c, final TLCState s0, final TLCState s1,
			final int control, final CostModel cm) {
		return evalApplImpl(expr, c, s0, s1, control, cm);
	}

	@Override
	protected final Value setSource(final SemanticNode expr, final Value value) {
		// Calling Value#setSource here causes Tool to wrap TLCRuntimExceptions as
		// FingerprintExceptions, which alters error reporting.  This causes some
		// tests to fail (Github179*Test) that expect a specific output.  Note that
		// Value#setSource doesn't have to be called here for DebugTool to work.
		return value;
	}

	@Override
	public final TLCState enabled(final SemanticNode pred, final IActionItemList acts, final Context c,
			final TLCState s0, final TLCState s1, final CostModel cm) {
		return enabledImpl(pred, (ActionItemList) acts, c, s0, s1, cm);
	}

	@Override
	protected final TLCState enabledAppl(final OpApplNode pred, final ActionItemList acts, final Context c,
			final TLCState s0, final TLCState s1, final CostModel cm) {
		return enabledApplImpl(pred, acts, c, s0, s1, cm);
	}

	@Override
	protected final TLCState enabledUnchanged(final SemanticNode expr, final ActionItemList acts, final Context c,
			final TLCState s0, final TLCState s1, final CostModel cm) {
		return enabledUnchangedImpl(expr, acts, c, s0, s1, cm);
	}

	@Override
	protected final TLCState getNextStates(final Action action, final SemanticNode pred, final ActionItemList acts,
			final Context c, final TLCState s0, final TLCState s1, final INextStateFunctor nss, final CostModel cm) {
		if (mode == EvalMode.Debugger) {
			return fastTool.getNextStatesImpl(action, pred, acts, c, s0, s1, nss, cm);
		}
		mode = EvalMode.Action;
		// In regular model-checking mode (no DebugTool), TLC sets the action and
		// predecessor lazily, that is after the successor has been fully constructed
		// and the state- and action-constraints evaluated.  With DebugTool present,
		// users want to see the trace from the initial to the current, partially
		// evaluated state.  Thus, we set action and predecessor eagerly.
		return getNextStatesImpl(action, pred, acts, c, s0, s1.setPredecessor(s0).setAction(action), nss, cm);
	}

	@Override
	protected final TLCState getNextStatesAppl(final Action action, final OpApplNode pred, final ActionItemList acts,
			final Context c, final TLCState s0, final TLCState s1, final INextStateFunctor nss, final CostModel cm) {
		if (mode == EvalMode.Debugger) {
			return fastTool.getNextStatesApplImpl(action, pred, acts, c, s0, s1, nss, cm);
		}
		mode = EvalMode.Action;
		target.pushFrame(this, pred, c, s0, action, s1);
		TLCState s = getNextStatesApplImpl(action, pred, acts, c, s0, s1, nss, cm);
		target.popFrame(this, pred, c, s0, s1);
		return s;
	}

	@Override
	protected final TLCState processUnchanged(final Action action, final SemanticNode expr, final ActionItemList acts,
			final Context c, final TLCState s0, final TLCState s1, final INextStateFunctor nss, final CostModel cm) {
		return processUnchangedImpl(action, expr, acts, c, s0, s1, nss, cm);
	}

	@Override
	protected void getInitStates(SemanticNode init, ActionItemList acts, Context c, TLCState ps, IStateFunctor states,
			CostModel cm) {
		if (mode == EvalMode.Debugger) {
			fastTool.getInitStates(init, acts, c, ps, states, cm);
		} else {
			mode = EvalMode.State;
			if (states instanceof WrapperStateFunctor) {
				// Wrap the IStateFunctor so we can intercept Tool adding a new state to the
				// functor. Without it, the debugger wouldn't show the fully assigned state and
				// the variable that is assigned last will always be null.
				super.getInitStates(init, acts, c, ps, states, cm);
			} else {
				super.getInitStates(init, acts, c, ps, new WrapperStateFunctor(states, target), cm);
			}
		}
	}
		
	@Override
	protected void getInitStatesAppl(OpApplNode init, ActionItemList acts, Context c, TLCState ps, IStateFunctor states,
			CostModel cm) {
		if (mode == EvalMode.Debugger) {
			fastTool.getInitStatesAppl(init, acts, c, ps, states, cm);
		} else {
			mode = EvalMode.State;
			target.pushFrame(this, init, c, ps);
			super.getInitStatesAppl(init, acts, c, ps, states, cm);
			target.popFrame(this, init, c, ps);
		}
	}

	@Override
	public boolean getNextStates(final INextStateFunctor functor, final TLCState state) {
		if (mode == EvalMode.Debugger) {
			fastTool.getNextStates(functor, state);
		}
		mode = EvalMode.Action;
		try {
			if (functor instanceof WrapperNextStateFunctor) {
				return super.getNextStates(functor, state);
			} else {
				for (int i = 0; i < actions.length; i++) {
					final Action action = actions[i];
					final WrapperNextStateFunctor wf = new WrapperNextStateFunctor(functor, target);
					if (action.isDeclared()) {
						// Breakpoints for the INextStateFunctor frames are in-line breakpoints on
						// the action declaration. If an action is undeclared, it is impossible to set
						// the breakpoint.
						target.pushFrame(this, action.getOpDef(), action.con, state, action, wf);
						this.getNextStates(wf, state, action);
						target.popFrame(this, action.getOpDef(), action.con, state, action, wf);
					} else {
						this.getNextStates(wf, state, action);
					}
				}
				return false;
			}
		} finally {
			// In getNextState above, the predecessor state is set eagerly for
			// TLCStateStackFrame#getTrace to function correctly in all cases. Here, we null
			// the predecessor again. Otherwise, TLC would keep the list of predecessors up
			// to the initial states (unless a TLCState is persisted to disk in the StateQueue).
			if (this.toolMode == Mode.MC_DEBUG) {
				// Do *not* unset the predecessor if running simulation that maintains the current behavior as a linked list of TLCStateMutExt.
				state.unsetPredecessor();
			}
		}
	}
	
	private static class WrapperStateFunctor implements IStateFunctor {
		protected final IStateFunctor functor;
		protected final IDebugTarget target;

		WrapperStateFunctor(IStateFunctor functor, IDebugTarget target) {
			this.functor = functor;
			this.target = target;
		}
		
		@Override
		public Object addElement(TLCState state) {
			target.pushFrame(state);
			Object addElement = functor.addElement(state);
			target.popFrame(state);
			return addElement;
		}
	}

	private static class WrapperNextStateFunctor extends WrapperStateFunctor implements INextStateFunctor {

		WrapperNextStateFunctor(INextStateFunctor functor, IDebugTarget target) {
			super(functor, target);
		}

		@Override
		public Object addElement(TLCState predecessor, Action a, TLCState state) {
			target.pushFrame(predecessor, a, state);
			Object addElement = ((INextStateFunctor) functor).addElement(predecessor, a, state);
			target.popFrame(predecessor, state);
			return addElement;
		}

		@Override
		public SetOfStates getStates() {
			return ((INextStateFunctor) functor).getStates();
		}
	}

	@Override
	public final <T> T eval(final Supplier<T> supplier) {
		// Evaluate supplier in the context of the debugger. In other words, the
		// evaluation is *not* intercepted by DebugTool. For example, Value#toString and
		// LazyValue#eval should not be intercepted when the debugger triggers toString
		// or eval.
		final EvalMode old = setDebugger();
		try {
			return supplier.get();
		} finally {
			mode = old;
		}
	}
	
	public EvalMode setDebugger() {
		final EvalMode old = this.mode;
		this.mode = EvalMode.Debugger;
		return old;
	}

	@Override
	public ITool getLiveness() {
		return this.fastTool;
	}
}
