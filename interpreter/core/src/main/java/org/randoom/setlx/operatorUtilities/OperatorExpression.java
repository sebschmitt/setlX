package org.randoom.setlx.operatorUtilities;

import org.randoom.setlx.assignments.AAssignableExpression;
import org.randoom.setlx.exceptions.SetlException;
import org.randoom.setlx.exceptions.TermConversionException;
import org.randoom.setlx.exceptions.UndefinedOperationException;
import org.randoom.setlx.operators.AOperator;
import org.randoom.setlx.operators.AZeroOperator;
import org.randoom.setlx.types.Term;
import org.randoom.setlx.types.Value;
import org.randoom.setlx.utilities.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stack of operators that can be evaluated.
 */
public class OperatorExpression extends Expression {
    private final static Map<String, Class<? extends AOperator>> OPERATOR_CLASSES = new HashMap<String, Class<? extends AOperator>>();

    private FragmentList<AOperator> operators;
    private final int numberOfOperators;
    private boolean isConstant;

    /**
     * Create a new operator stack.
     *
     * @param operator Operator evaluate.
     */
    public OperatorExpression(AOperator operator) {
        this(new FragmentList<AOperator>(operator));
    }

    /**
     * Create a new operator stack.
     *
     * @param operators Operator stack to evaluate.
     */
    public OperatorExpression(FragmentList<AOperator> operators) {
        this.operators = unify(operators);
        this.numberOfOperators = operators.size();
        isConstant = false;
    }

    @Override
    public boolean collectVariablesAndOptimize(State state, List<String> boundVariables, List<String> unboundVariables, List<String> usedVariables) {
        if (isConstant) {
            // already marked as constant, no variables are needed during execution
            return true;
        }

        final int preBoundSize   = boundVariables.size();
        final int preUnboundSize = unboundVariables.size();
        final int preUsedSize    = usedVariables.size();

        // collect variables in this expression
        Stack<OptimizerData> optimizerFragments = new Stack<OptimizerData>();

        for (AOperator operator : operators) {
            optimizerFragments.push(operator.collectVariables(state, boundVariables, unboundVariables, usedVariables, optimizerFragments));
        }

        if (optimizerFragments.size() == 1) {
            OptimizerData optimizerData = optimizerFragments.poll();

            // prerequisite for optimization is that no variables are provided for later
            // expressions and that no unbound variables are used in this expression
            if (boundVariables.size() == preBoundSize && unboundVariables.size() == preUnboundSize) {
                // optimize when there where also no variables used at all
                if (usedVariables.size() != preUsedSize) {
                    isConstant = optimizerData.isAllowOptimization();
                }
                // or if all used variables are not prebound
                else {
                    final List<String> prebound     = boundVariables.subList(0, preBoundSize);
                    final List<String> usedHere     = new ArrayList<String>(usedVariables.subList(preUsedSize, usedVariables.size()));
                    final int          usedHereSize = usedHere.size();

                    // check if any prebound variables could have been used
                    usedHere.removeAll(prebound);
                    if (usedHere.size() == usedHereSize) {
                        // definitely not, therefore safe to optimize
                        isConstant = optimizerData.isAllowOptimization();
                    }
                }
            }

            return isConstant;
        } else {
            throw new IllegalStateException("Error in operator stack optimization!");
        }
    }

    /**
     * Create a Statement from a term representing a statement
     *
     * @param state                    Current state of the running setlX program.
     * @param term                     Term to convert.
     * @param functionalCharacter      Functional character of the term to convert.
     * @return                         New Statement or null, if term does not represent a statement.
     * @throws TermConversionException in case the term is malformed.
     */
    public static OperatorExpression createFromTerm(State state, Term term, String functionalCharacter) throws TermConversionException {
        Class<? extends AOperator> operatorClass;
        synchronized (OPERATOR_CLASSES) {
            operatorClass = OPERATOR_CLASSES.get(functionalCharacter);
        }
        if (operatorClass == null) {
            operatorClass = TermUtilities.getClassForTerm(AOperator.class, functionalCharacter);

            if (operatorClass != null) {
                synchronized (OPERATOR_CLASSES) {
                    OPERATOR_CLASSES.put(functionalCharacter, operatorClass);
                }
            }
        }
        return null;
    }

    /**
     * Data for optimization.
     */
    public static class OptimizerData {
        private boolean allowOptimization;

        /**
         * Create new OptimizerFragment.
         *
         * @param allowOptimization true iff this fragment may be optimized if it is constant
         */
        public OptimizerData(boolean allowOptimization) {
            this.allowOptimization = allowOptimization;
        }

        /**
         * @return true iff this fragment is constant and can be optimized.
         */
        public boolean isAllowOptimization() {
            return allowOptimization;
        }
    }

    /**
     * @return true iff this expression is constant, e.g. will always evaluate to the same result.
     */
    public boolean isConstant() {
        return isConstant;
    }

    /**
     * Evaluate this expression of operators.
     *
     * @param state          Current state of the running setlX program.
     * @return               Result of the evaluation.
     * @throws SetlException Thrown in case of some (user-) error.
     */
    @Override
    public Value evaluate(final State state) throws SetlException {
        Stack<Value> values = new Stack<Value>();

        for (int i = 0; i < numberOfOperators; i++) {
            AOperator operator = operators.get(i);
            try {
                values.push(operator.evaluate(state, values));
            } catch (final SetlException se) {
                while (i < numberOfOperators) {
                    StringBuilder error = new StringBuilder();
                    error.append("Error in \"");
                    appendExpression(state, error, ++i);
                    error.append("\":");
                    se.addToTrace(error.toString());
                    while (i < numberOfOperators && operators.get(i) instanceof AZeroOperator) {
                        ++i;
                    }
                }
                throw se;
            }
        }

        return values.poll();
    }

    /**
     * Create an assignable expression from this operator expression.
     *
     * @return                             AssignableExpression.
     * @throws UndefinedOperationException if expression can not be converted.
     */
    public AAssignableExpression convertToAssignable() throws UndefinedOperationException {
        AAssignableExpression assignable = null;
        for (AOperator operator : operators) {
            assignable = unify(operator.convertToAssignableExpression(assignable));
        }
        return assignable;
    }

    /* string operations */

    @Override
    public void appendString(State state, StringBuilder sb, int tabs) {
        appendExpression(state, sb, numberOfOperators);
    }

    private void appendExpression(State state, StringBuilder sb, int maxOperatorDepth) {
        Stack<ExpressionFragment> expressionFragments = new Stack<ExpressionFragment>();

        for (int i = 0; i < maxOperatorDepth; i++) {
            AOperator operator = operators.get(i);
            ExpressionFragment rhs = null;
            if (operator.hasArgumentAfterOperator()) {
                rhs = expressionFragments.poll();
            }
            ExpressionFragment lhs = null;
            if (operator.hasArgumentBeforeOperator()) {
                lhs = expressionFragments.poll();
            }
            StringBuilder expressionFragment = new StringBuilder();
            if (lhs != null) {
                boolean insertBrackets = operator.isRightAssociative()? lhs.getPrecedence() <= operator.precedence() : lhs.getPrecedence() < operator.precedence();
                if (insertBrackets) {
                    expressionFragment.append("(");
                }
                expressionFragment.append(lhs.getExpression());
                if (insertBrackets) {
                    expressionFragment.append(")");
                }
            }
            operator.appendOperatorSign(state, expressionFragment);
            if (rhs != null) {
                boolean insertBrackets = operator.isLeftAssociative()? rhs.getPrecedence() <= operator.precedence() : rhs.getPrecedence() < operator.precedence();
                if (insertBrackets) {
                    expressionFragment.append("(");
                }
                expressionFragment.append(rhs.getExpression());
                if (insertBrackets) {
                    expressionFragment.append(")");
                }
            }
            expressionFragments.push(new ExpressionFragment(expressionFragment.toString(), operator.precedence()));
        }

        sb.append(expressionFragments.poll().getExpression());
    }

    private static class ExpressionFragment {
        private String  expression;
        private int     precedence;

        public ExpressionFragment(String expression, int precedence) {
            this.expression      = expression;
            this.precedence      = precedence;
        }

        public String getExpression() {
            return expression;
        }

        public int getPrecedence() {
            return precedence;
        }
    }

    /* term operations */

    @Override
    public Value toTerm(State state) throws SetlException {
        Stack<Value> termFragments = new Stack<Value>();

        for (AOperator operator : operators) {
            termFragments.push(operator.buildTerm(state, termFragments));
        }

        return termFragments.poll();
    }

    /* comparisons */

    @Override
    public int compareTo(CodeFragment other) {
        if (this == other) {
            return 0;
        } else if (other.getClass() == OperatorExpression.class) {
            final OperatorExpression otr = (OperatorExpression) other;
            return operators.compareTo(otr.operators);
        } else {
            return (this.compareToOrdering() < other.compareToOrdering())? -1 : 1;
        }
    }

    private final static long COMPARE_TO_ORDER_CONSTANT = generateCompareToOrderConstant(OperatorExpression.class);

    @Override
    public long compareToOrdering() {
        return COMPARE_TO_ORDER_CONSTANT;
    }

    @Override
    public final boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (obj.getClass() == OperatorExpression.class) {
            return this.operators.equals(((OperatorExpression) obj).operators);
        }
        return false;
    }

    @Override
    public int computeHashCode() {
        return ((int) COMPARE_TO_ORDER_CONSTANT) + operators.hashCode();
    }
}
