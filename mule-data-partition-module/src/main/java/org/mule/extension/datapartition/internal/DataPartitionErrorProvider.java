package org.mule.extension.datapartition.internal;

import java.util.HashSet;
import java.util.Set;
import org.mule.runtime.extension.api.annotation.error.ErrorTypeProvider;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

public class DataPartitionErrorProvider implements ErrorTypeProvider {
    @Override
    public Set<ErrorTypeDefinition> getErrorTypes() {
        HashSet<ErrorTypeDefinition> errors = new HashSet<>();
        errors.add(DataPartitionErrors.INVALID_PARAMETER);
        errors.add(DataPartitionErrors.IO_OPERATION_FAILED);
        return errors;
    }
}
