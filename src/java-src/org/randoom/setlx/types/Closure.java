package org.randoom.setlx.types;

import org.randoom.setlx.exceptions.SetlException;
import org.randoom.setlx.exceptions.TermConversionException;
import org.randoom.setlx.expressions.Expr;
import org.randoom.setlx.expressions.Variable;
import org.randoom.setlx.statements.Block;
import org.randoom.setlx.utilities.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class represents a function definition, where closures are explicitly enabled.
 *
 * grammar rule:
 * procedure
 *     : 'closure' '(' procedureParameters ')' '{' block '}'
 *     ;
 *
 * implemented here as:
 *                     ===================         =====
 *                          parameters           statements
 */
public class Closure extends Procedure {
    // functional character used in terms
    private   final static String FUNCTIONAL_CHARACTER = generateFunctionalCharacter(Closure.class);

    /**
     * Variables and values used in closure.
     */
    protected       SetlHashMap<Value> closure;

    /**
     * Create new closure definition.
     *
     * @param parameters List of parameters.
     * @param statements Statements in the body of the procedure.
     */
    public Closure(final List<ParameterDef> parameters, final Block statements) {
        this(parameters, statements, null);
    }

    /**
     * Create new procedure definition, which replicates the complete internal
     * state of another procedure.
     *
     * @param parameters procedure parameters
     * @param statements statements in the body of the procedure
     * @param closure    Attached closure variables.
     */
    protected Closure(final List<ParameterDef> parameters, final Block statements, final SetlHashMap<Value> closure) {
        super(parameters, statements);
        if (closure != null) {
            this.closure = new SetlHashMap<Value>(closure);
        } else {
            this.closure = null;
        }
    }

    /**
     * Create a separate instance of this procedure.
     *
     * Note: Only to be used by ProcedureConstructor.
     *
     * @return Copy of this procedure definition.
     */
    public Closure createCopy() {
        return new Closure(parameters, statements);
    }

    @Override
    public Closure clone() {
        if (closure != null || object != null) {
            return new Closure(parameters, statements, closure);
        } else {
            return this;
        }
    }

    /**
     * Attach closure variables and their values.
     *
     * @param closure Closure variables to attach.
     */
    public void setClosure(final SetlHashMap<Value> closure) {
        this.closure = closure;
    }

    @Override
    public void collectVariablesAndOptimize (
            final State        state,
            final List<String> boundVariables,
            final List<String> unboundVariables,
            final List<String> usedVariables
    ) {
        /* first collect and optimize the inside */
        final List<String> innerBoundVariables   = new ArrayList<String>();
        final List<String> innerUnboundVariables = new ArrayList<String>();
        final List<String> innerUsedVariables    = new ArrayList<String>();

        // add all parameters to bound
        for (final ParameterDef def : parameters) {
            def.collectVariablesAndOptimize(state, innerBoundVariables, innerBoundVariables, innerBoundVariables);
        }

        statements.collectVariablesAndOptimize(state, innerBoundVariables, innerUnboundVariables, innerUsedVariables);

        /* compute variables as seen by the outside */

        // upon defining this procedure, all variables which are unbound inside
        // will be read to create the closure for this procedure
        for (final String var : innerUnboundVariables) {
            //noinspection StringEquality
            if (var == Variable.getPreventOptimizationDummy()) {
                continue;
            } else if (boundVariables.contains(var)) {
                usedVariables.add(var);
            } else {
                unboundVariables.add(var);
            }
        }
    }

    @Override
    protected Value callAfterEval(final State state, final List<Expr> args, final List<Value> values, final SetlObject object) throws SetlException {
        // increase callStackDepth
        ++(state.callStackDepth);

        // save old scope
        final VariableScope oldScope = state.getScope();
        // create new scope used for the function call
        final VariableScope newScope = oldScope.createFunctionsOnlyLinkedScope();
        state.setScope(newScope);

        // link members of surrounding object
        if (object != null) {
            newScope.linkToThisObject(object);
        }

        // assign closure contents
        if (closure != null) {
            for (final Map.Entry<String, Value> entry : closure.entrySet()) {
                final Value value = entry.getValue();
                new Variable(entry.getKey()).assignUnclonedCheckUpTo(state, value, oldScope, true, FUNCTIONAL_CHARACTER);
            }
        }

        // put arguments into inner scope
        final int parametersSize = parameters.size();
              int rwParameters   = 0;
        for (int i = 0; i < parametersSize; ++i) {
            final ParameterDef param = parameters.get(i);
            final Value        value = values.get(i);
            if (param.getType() == ParameterDef.ParameterType.READ_WRITE) {
                param.assign(state, value, FUNCTIONAL_CHARACTER);
                ++rwParameters;
            } else {
                param.assign(state, value.clone(), FUNCTIONAL_CHARACTER);
            }
        }

        // get rid of value-list to potentially free some memory
        values.clear();

        // results of call to procedure
        ReturnMessage  result = null;
        WriteBackAgent wba    = null;

        try {

            // execute, e.g. perform actual procedure call
            result = statements.execute(state);

            // extract 'rw' arguments from environment and store them into WriteBackAgent
            if (rwParameters > 0) {
                wba = new WriteBackAgent(rwParameters);
                for (int i = 0; i < parametersSize; ++i) {
                    final ParameterDef param = parameters.get(i);
                    if (param.getType() == ParameterDef.ParameterType.READ_WRITE) {
                        // value of parameter after execution
                        final Value postValue = param.getValue(state);
                        // expression used to fill parameter before execution
                        final Expr preExpr = args.get(i);
                        /* if possible the WriteBackAgent will set the variable used in this
                           expression to its postExecution state in the outer environment    */
                        wba.add(preExpr, postValue);
                    }
                }
            }

            // read closure variables and update their current state
            if (closure != null) {
                for (final Map.Entry<String, Value> entry : closure.entrySet()) {
                    entry.setValue(state.findValue(entry.getKey()));
                }
            }

        } catch (final StackOverflowError soe) {
            state.storeStackDepthOfFirstCall(state.callStackDepth);
            throw soe;
        } finally { // make sure scope is always reset
            // restore old scope
            state.setScope(oldScope);

            // write values in WriteBackAgent into restored scope
            if (wba != null) {
                wba.writeBack(state, FUNCTIONAL_CHARACTER);
            }

            // decrease callStackDepth
            --(state.callStackDepth);
        }

        if (result != null) {
            return result.getPayload();
        } else {
            return Om.OM;
        }
    }

    /* string and char operations */

    @Override
    public void appendString(final State state, final StringBuilder sb, final int tabs) {
        object = null;
        sb.append("closure(");
        final Iterator<ParameterDef> iter = parameters.iterator();
        while (iter.hasNext()) {
            iter.next().appendString(state, sb, 0);
            if (iter.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append(") ");
        statements.appendString(state, sb, tabs, /* brackets = */ true);
    }

    /* term operations */

    @Override
    public Value toTerm(final State state) throws SetlException {
        object = null;
        final Term result = new Term(FUNCTIONAL_CHARACTER, 2);

        final SetlList paramList = new SetlList(parameters.size());
        for (final ParameterDef param: parameters) {
            paramList.addMember(state, param.toTerm(state));
        }
        result.addMember(state, paramList);

        result.addMember(state, statements.toTerm(state));

        return result;
    }

    /**
     * Convert a term representing a Closure into such a procedure.
     *
     * @param state                    Current state of the running setlX program.
     * @param term                     Term to convert.
     * @return                         Resulting Closure.
     * @throws TermConversionException Thrown in case of an malformed term.
     */
    public static Closure termToValue(final State state, final Term term) throws TermConversionException {
        if (term.size() != 2 || term.firstMember().getClass() != SetlList.class) {
            throw new TermConversionException("malformed " + FUNCTIONAL_CHARACTER);
        } else {
            final SetlList           paramList  = (SetlList) term.firstMember();
            final List<ParameterDef> parameters = new ArrayList<ParameterDef>(paramList.size());
            for (final Value v : paramList) {
                parameters.add(ParameterDef.valueToParameterDef(state, v));
            }
            final Block              block      = TermConverter.valueToBlock(state, term.lastMember());
            return new Closure(parameters, block);
        }
    }

    /* comparisons */

    @Override
    public int compareTo(final Value other) {
        object = null;
        if (this == other) {
            return 0;
        } else if (other.getClass() == Closure.class) {
            final Closure otherClosure = (Closure) other;
            int cmp = Integer.valueOf(parameters.size()).compareTo(otherClosure.parameters.size());
            if (cmp != 0) {
                return cmp;
            }
            for (int index = 0; index < parameters.size(); ++index) {
                cmp = parameters.get(index).compareTo(otherClosure.parameters.get(index));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return statements.compareTo(otherClosure.statements);
        } else {
            return this.compareToOrdering() - other.compareToOrdering();
        }
    }

    @Override
    public int compareToOrdering() {
        object = null;
        return COMPARE_TO_ORDERING_PROCEDURE_CLOSURE;
    }

    @Override
    public boolean equalTo(final Object other) {
        object = null;
        if (this == other) {
            return true;
        } else if (other.getClass() == Closure.class) {
            final Closure otherClosure = (Closure) other;
            if (parameters.size() == otherClosure.parameters.size()) {
                for (int index = 0; index < parameters.size(); ++index) {
                    if ( ! parameters.get(index).equalTo(otherClosure.parameters.get(index))) {
                        return false;
                    }
                }
                return statements.equalTo(otherClosure.statements);
            }
        }
        return false;
    }

    private final static int initHashCode = Closure.class.hashCode();

    @Override
    public int hashCode() {
        object = null;
        return (initHashCode + parameters.size()) * 31 + statements.size();
    }

    /**
     * Get the functional character used in terms.
     *
     * @return functional character used in terms.
     */
    public static String getFunctionalCharacter() {
        return FUNCTIONAL_CHARACTER;
    }
}

