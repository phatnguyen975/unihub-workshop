package com.unihub.workshop.attendances.infrastructure.persistence;

import com.unihub.workshop.attendances.application.ports.out.DatabaseFailureClassifier;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnectionFailureClassifier
    implements DatabaseFailureClassifier
{

    @Override
    public boolean isUnavailable(Throwable failure) {
        for (
            Throwable cause = failure;
            cause != null;
            cause = cause.getCause()
        ) {
            if (
                cause instanceof DataAccessResourceFailureException ||
                cause instanceof JDBCConnectionException
            ) {
                return true;
            }
            if (cause instanceof SQLException sqlException) {
                String state = sqlException.getSQLState();
                if (
                    sqlException instanceof SQLTransientConnectionException ||
                    sqlException instanceof
                        SQLNonTransientConnectionException ||
                    sqlException instanceof SQLRecoverableException ||
                    (state != null && state.startsWith("08"))
                ) {
                    return true;
                }
            }
        }
        return false;
    }
}
