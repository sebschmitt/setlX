package org.randoom.setlx.functions;

import org.randoom.setlx.exceptions.IncompatibleTypeException;
import org.randoom.setlx.types.Om;
import org.randoom.setlx.types.Value;
import org.randoom.setlx.utilities.ParameterDef;

import java.util.List;

/*
 * from(rw collectionValue)      : selects and removes an arbitrary
 *                                 member from `collectionValue'
 */

public class PD_from extends PreDefinedFunction {
    public final static PreDefinedFunction DEFINITION
                                            = new PD_from();

    private PD_from() {
        super("from");
        addParameter("collectionValue", ParameterDef.READ_WRITE);
    }

    public Value execute(final List<Value> args,
                         final List<Value> writeBackVars
    ) throws IncompatibleTypeException {
        final Value collection = args.get(0);
        final Value element;
        try {
            // throws exception when `collection' is not a collection
            final int size = collection.size();
            if (size % 2 == 0) {
                element = collection.removeFirstMember();
            } else {
                element = collection.removeLastMember();
            }
        } catch (final IncompatibleTypeException ite) {
            throw new IncompatibleTypeException(
                "Argument '" + collection + "' is not a collection value."
            );
        }

        /* write the reduced collection back
           into the outer environment        */
        writeBackVars.add(collection);

        return element;
    }
}

