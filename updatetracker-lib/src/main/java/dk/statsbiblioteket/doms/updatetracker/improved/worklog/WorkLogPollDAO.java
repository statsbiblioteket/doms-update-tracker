package dk.statsbiblioteket.doms.updatetracker.improved.worklog;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import dk.statsbiblioteket.doms.central.connectors.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;


/**
 * The DAO represents the worklog database, with all the methods nessesary for the poller
 * @see dk.statsbiblioteket.doms.updatetracker.improved.worklog.WorkLogPollTask
 */
public class WorkLogPollDAO implements Closeable {

    private final String driver;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    public static final Calendar tzUTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

    private Logger log = LoggerFactory.getLogger(WorkLogPollDAO.class);


    /** The pool with data sources for the database connections. */
    private final ComboPooledDataSource connectionPool;

    public WorkLogPollDAO(String driver, String jdbcUrl, String username, String password) {
        this.driver = driver;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;

        this.connectionPool = new ComboPooledDataSource();

        initialiseConnectionPool();

        ensureLatestKey();
    }

    /**
     * Initialises the ConnectionPool for the connections to the database.
     */
    private void initialiseConnectionPool() {
        try {
            connectionPool.setDriverClass(driver);
            connectionPool.setJdbcUrl(jdbcUrl);
            if (username != null) {
                connectionPool.setUser(username);
            }
            if (password != null) {
                connectionPool.setPassword(password);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Creates and connects to the database.
     *
     * @return The connection to the database.
     */
    public Connection getConnection() {
        try {
            return connectionPool.getConnection();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Cleans up after use.
     */
    public void close() {
        try {
            DataSources.destroy(connectionPool);
        } catch (SQLException e) {
            log.error("Could not destroy the connectionPool",e);
        }
    }

    /**
     * Get the list of worklog events from the worklog database, starting after the lastRegisteredKey.
     * The key is an autoincrementing long, so start from 0 if you want to start at the beginning
     * @param lastRegisteredKey the highest key not to include
     * @param limit the max amount of worklog units to retrieve
     * @return a list of worklog units, at most limit long
     * @throws IOException on any database communication problems
     */
    public List<WorkLogUnit> getFedoraEvents(Long lastRegisteredKey, int limit) throws IOException {

        ArrayList<WorkLogUnit> result = new ArrayList<WorkLogUnit>(limit);

        try {
            Connection conn = getConnection();
            try {
                PreparedStatement statement
                        = conn.prepareStatement("SELECT key,pid,happened,method,param FROM updateTrackerLogs WHERE " +
                                                "key > ? ORDER BY happened ASC LIMIT ?");
                try {
                    statement.setLong(1, lastRegisteredKey);
                    statement.setInt(2, limit);

                    statement.execute();
                    ResultSet resultSet = statement.getResultSet();
                    while (resultSet.next()) {
                        Long key = resultSet.getLong("key");
                        String pid = Connector.toPid(resultSet.getString("pid"));
                        String method = resultSet.getString("method");
                        String param = resultSet.getString("param");
                        Timestamp timestamp = resultSet.getTimestamp("happened",tzUTC);
                        result.add(new WorkLogUnit(key, method, new Date(timestamp.getTime()), pid, param));
                    }
                    return result;
                } finally {
                    statement.close();
                }
            } finally {
                conn.close();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    private void ensureLatestKey() {
        try {
            getLatestKey();
        } catch (IOException e) {
            try {
                Connection conn = getConnection();
                try {
                    conn.prepareStatement("CREATE TABLE latestKey (key BIGINT)").execute();
                    conn.prepareStatement("INSERT INTO latestKey (key) VALUES (0)").execute();
                } finally {
                    conn.close();
                }
                getLatestKey();
            } catch (Exception e1) {
                throw new IllegalStateException(e1);
            }
        }
    }

    public Long getLatestKey() throws IOException {
        try {
            Connection conn = getConnection();
            try {
                ResultSet resultSet = conn.prepareStatement("SELECT key FROM latestKey").executeQuery();
                return resultSet.getLong(1);
            } finally {
                conn.close();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    public void setLatestKey(Long latestKey) throws IOException {
        try {
            Connection conn = getConnection();
            try {
                PreparedStatement preparedStatement = conn.prepareStatement("UPDATE latestKey SET key=? WHERE true");
                preparedStatement.setLong(1, latestKey);
                preparedStatement.execute();
            } finally {
                conn.close();
            }
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }
}
