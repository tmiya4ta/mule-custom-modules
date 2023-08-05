package org.mule.extension.csvfilesplit.internal;

import java.util.Optional;

import org.mule.runtime.extension.api.error.ErrorTypeDefinition;
import org.mule.runtime.extension.api.error.MuleErrors;

public enum CsvFileSplitErrors implements ErrorTypeDefinition<CsvFileSplitErrors> {
    INVALID_PARAMETER,
    INTERRUPTED,
    IO_OPERATION_FAILED,
    ILLEGAL_ACTION;
    private ErrorTypeDefinition<? extends Enum<?>> parent;

    CsvFileSplitErrors(ErrorTypeDefinition<? extends Enum<?>> parent) {
        this.parent = parent;
    }

    CsvFileSplitErrors() {
    }

    @Override
    public Optional<ErrorTypeDefinition<? extends Enum<?>>> getParent() {
        return Optional.ofNullable(parent);
    }
}
