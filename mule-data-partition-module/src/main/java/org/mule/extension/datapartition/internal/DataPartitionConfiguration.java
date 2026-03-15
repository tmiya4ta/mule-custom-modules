package org.mule.extension.datapartition.internal;

import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;

@Operations(DataPartitionOperations.class)
@ConnectionProviders(DataPartitionConnectionProvider.class)
public class DataPartitionConfiguration {
}
