package org.mule.extension.csvfilesplit.internal;

import java.util.HashSet;
import java.util.Set;

import org.mule.runtime.extension.api.annotation.error.ErrorTypeProvider;
import org.mule.runtime.extension.api.error.ErrorTypeDefinition;

public class ExecuteErrorsProvider implements ErrorTypeProvider {
	@Override
	public Set<ErrorTypeDefinition> getErrorTypes() {
		HashSet<ErrorTypeDefinition> errors = new HashSet<>();
		errors.add(CsvFileSplitErrors.INVALID_PARAMETER);
		errors.add(CsvFileSplitErrors.INTERRUPTED);
		errors.add(CsvFileSplitErrors.ILLEGAL_ACTION);
		return errors;
	}
}
