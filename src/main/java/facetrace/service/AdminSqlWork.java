package facetrace.service;

import java.sql.Connection;
import java.sql.SQLException;

/** Lets a short database action run inside the service's transaction. */
@FunctionalInterface
interface AdminSqlWork<T> {
    T run(Connection connection) throws SQLException;
}
