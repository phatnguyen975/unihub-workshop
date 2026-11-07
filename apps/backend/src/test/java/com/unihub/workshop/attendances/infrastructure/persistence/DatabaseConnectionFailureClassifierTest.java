package com.unihub.workshop.attendances.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

class DatabaseConnectionFailureClassifierTest {

    private final DatabaseConnectionFailureClassifier classifier =
        new DatabaseConnectionFailureClassifier();

    @Test
    void recognizesDatasourceAndWrappedConnectionFailures() {
        assertThat(
            classifier.isUnavailable(
                new DataAccessResourceFailureException("offline")
            )
        ).isTrue();
        assertThat(
            classifier.isUnavailable(
                new CannotCreateTransactionException(
                    "transaction",
                    new SQLTransientConnectionException("offline", "08001")
                )
            )
        ).isTrue();
        assertThat(
            classifier.isUnavailable(
                new JDBCConnectionException(
                    "hibernate",
                    new SQLException("offline")
                )
            )
        ).isTrue();
        assertThat(
            classifier.isUnavailable(new SQLException("offline", "08006"))
        ).isTrue();
    }

    @Test
    void doesNotTreatProgrammingOrSqlSyntaxErrorsAsOutage() {
        assertThat(
            classifier.isUnavailable(new IllegalStateException("bug"))
        ).isFalse();
        assertThat(
            classifier.isUnavailable(new SQLException("syntax", "42601"))
        ).isFalse();
    }
}
