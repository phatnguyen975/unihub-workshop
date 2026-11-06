package com.unihub.workshop.shared.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionAwareRoutingDataSourceConnectionTest {

    private final DataSource primary = mock(DataSource.class);
    private final DataSource replica = mock(DataSource.class);
    private final Connection primaryConnection = mock(Connection.class);
    private final Connection replicaConnection = mock(Connection.class);
    private final Statement primaryStatement = mock(Statement.class);
    private final Statement replicaStatement = mock(Statement.class);
    private DataSource configured;
    private TransactionTemplate writeTransaction;
    private TransactionTemplate readTransaction;

    @BeforeEach
    void setUp() throws Exception {
        when(primary.getConnection()).thenReturn(primaryConnection);
        when(replica.getConnection()).thenReturn(replicaConnection);
        when(primaryConnection.getAutoCommit()).thenReturn(true);
        when(primaryConnection.getTransactionIsolation()).thenReturn(
            Connection.TRANSACTION_READ_COMMITTED
        );
        when(primaryConnection.createStatement()).thenReturn(primaryStatement);
        when(replicaConnection.createStatement()).thenReturn(replicaStatement);
        configured = new DataSourceRoutingConfig(
            new DataSourceProperties(null, null)
        ).routingDataSource(primary, replica);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(
            configured
        );
        writeTransaction = new TransactionTemplate(manager);
        readTransaction = new TransactionTemplate(manager);
        readTransaction.setReadOnly(true);
    }

    @Test
    void readOnlyReportExecutesOnReplicaAndCleansTransactionState()
        throws Exception {
        readTransaction.executeWithoutResult(status ->
            executeSql("select report")
        );

        verify(replicaStatement).execute("select report");
        assertThat(
            TransactionSynchronizationManager.isSynchronizationActive()
        ).isFalse();
        assertThat(
            TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        ).isFalse();
        assertThat(
            TransactionSynchronizationManager.hasResource(configured)
        ).isFalse();
    }

    @Test
    void writeAndParticipatingReadExecuteOnPrimary() throws Exception {
        writeTransaction.executeWithoutResult(status -> {
            executeSql("update assignment");
            readTransaction.executeWithoutResult(nested ->
                executeSql("select assignment")
            );
        });

        verify(primaryStatement).execute("update assignment");
        verify(primaryStatement).execute("select assignment");
        assertThat(
            TransactionSynchronizationManager.hasResource(configured)
        ).isFalse();
    }

    @Test
    void outsideTransactionExecutesOnPrimary() throws Exception {
        executeSql("select outside");

        verify(primaryStatement).execute("select outside");
    }

    @Test
    void assignmentReadsSeePrimaryAfterAssignmentAndRevocation()
        throws Exception {
        ResultSet assigned = mock(ResultSet.class);
        ResultSet revoked = mock(ResultSet.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(
            primaryConnection.prepareStatement("select assignment")
        ).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(assigned, revoked);
        when(assigned.next()).thenReturn(true);
        when(revoked.next()).thenReturn(false);

        boolean first = Boolean.TRUE.equals(
            writeTransaction.execute(status -> assignmentExists())
        );
        boolean second = Boolean.TRUE.equals(
            writeTransaction.execute(status -> assignmentExists())
        );

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(replicaConnection, never()).prepareStatement(
            "select assignment"
        );
    }

    private boolean assignmentExists() {
        Connection connection = DataSourceUtils.getConnection(configured);
        try (
            PreparedStatement statement = connection.prepareStatement(
                "select assignment"
            );
            ResultSet results = statement.executeQuery()
        ) {
            return results.next();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        } finally {
            DataSourceUtils.releaseConnection(connection, configured);
        }
    }

    @Test
    void primaryFailureAtStatementAcquisitionNeverFallsBackToReplica()
        throws Exception {
        when(primary.getConnection())
            .thenReturn(primaryConnection)
            .thenThrow(
                new SQLTransientConnectionException("unavailable", "08001")
            );

        assertThatThrownBy(() ->
            writeTransaction.executeWithoutResult(status -> {
                Connection connection = DataSourceUtils.getConnection(
                    configured
                );
                try {
                    connection.createStatement();
                } catch (SQLException exception) {
                    throw new IllegalStateException(exception);
                } finally {
                    DataSourceUtils.releaseConnection(connection, configured);
                }
            })
        ).hasRootCauseInstanceOf(SQLTransientConnectionException.class);

        verify(replica, never()).getConnection();
        assertThat(
            TransactionSynchronizationManager.hasResource(configured)
        ).isFalse();
    }

    private void executeSql(String sql) {
        Connection connection = DataSourceUtils.getConnection(configured);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, configured);
        }
    }
}
