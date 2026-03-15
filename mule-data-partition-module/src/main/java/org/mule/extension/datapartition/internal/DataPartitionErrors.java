package org.mule.extension.datapartition.internal;

import java.util.Optional;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

public enum DataPartitionErrors implements ErrorTypeDefinition<DataPartitionErrors> {
    INVALID_PARAMETER,
    IO_OPERATION_FAILED;

    private ErrorTypeDefinition<? extends Enum<?>> parent;

    DataPartitionErrors(ErrorTypeDefinition<? extends Enum<?>> parent) {
        this.parent = parent;
    }

    DataPartitionErrors() {
    }

    @Override
    public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
        return Optional.ofNullable(parent);
    }
}
