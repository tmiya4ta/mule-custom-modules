package org.mule.extension.datapartition.internal;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionValidationResult;
import org.mule.runtime.api.connection.PoolingConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataPartitionConnectionProvider implements PoolingConnectionProvider<DataPartitionConnection> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataPartitionConnectionProvider.class);

    @Override
    public DataPartitionConnection connect() throws ConnectionException {
        return new DataPartitionConnection("id");
    }

    @Override
    public void disconnect(DataPartitionConnection connection) {
        try {
            connection.invalidate();
        } catch (Exception e) {
            LOGGER.error("Error while disconnecting [" + connection.getId() + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public ConnectionValidationResult validate(DataPartitionConnection connection) {
        return ConnectionValidationResult.success();
    }
}
