package com.acme.salarymanagement.support;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * A {@link DataSource} that counts the statements executed through it.
 *
 * <p>This exists because {@code CLAUDE.md} promises that integration tests count statements and
 * fail when the count exceeds what the test declares, and nothing in the repository did. The
 * first attempt read {@code pg_stat_database}, whose counters are transactions and rows rather
 * than statements - it would have passed at any number of queries (D104).
 *
 * <p>Counting happens at the JDBC boundary, where an N+1 actually is: every {@code execute*} on
 * every statement handed out by every connection, whoever issued it. A repository that loops is
 * caught whether it loops in Java, in a mapper, or in a lazily-initialised association.
 */
public final class CountingDataSource implements DataSource {

    private static final AtomicLong EXECUTIONS = new AtomicLong();

    private final DataSource delegate;

    public CountingDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    /** Runs the work and returns how many statements it executed. */
    public static long statementsIssuedBy(Runnable work) {
        long before = EXECUTIONS.get();
        work.run();
        return EXECUTIONS.get() - before;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return counting(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return counting(delegate.getConnection(username, password));
    }

    private static Connection counting(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                CountingDataSource.class.getClassLoader(), new Class<?>[] {Connection.class}, handlerFor(real));
    }

    private static InvocationHandler handlerFor(Connection real) {
        return (proxy, method, args) -> {
            Object result = invoke(real, method, args);
            if (result instanceof PreparedStatement prepared) {
                return countingStatement(prepared, PreparedStatement.class);
            }
            if (result instanceof Statement statement) {
                return countingStatement(statement, Statement.class);
            }
            return result;
        };
    }

    private static <T extends Statement> Object countingStatement(T real, Class<T> asInterface) {
        return Proxy.newProxyInstance(
                CountingDataSource.class.getClassLoader(), new Class<?>[] {asInterface}, (proxy, method, args) -> {
                    if (method.getName().startsWith("execute")) {
                        EXECUTIONS.incrementAndGet();
                    }
                    return invoke(real, method, args);
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            // Unwrap, or every SQLException reaches the caller as an UndeclaredThrowable and the
            // constraint tests stop recognising what the database refused.
            throw wrapped.getCause();
        }
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() {
        throw new UnsupportedOperationException("not needed by any test");
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return type.isInstance(this) ? type.cast(this) : delegate.unwrap(type);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        return type.isInstance(this) || delegate.isWrapperFor(type);
    }
}
