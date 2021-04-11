package org.seasar.doma.jdbc.builder;

import java.sql.Statement;
import java.util.List;
import org.seasar.doma.DomaNullPointerException;
import org.seasar.doma.jdbc.Config;
import org.seasar.doma.jdbc.JdbcException;
import org.seasar.doma.jdbc.Sql;
import org.seasar.doma.jdbc.SqlLogType;
import org.seasar.doma.jdbc.UniqueConstraintException;
import org.seasar.doma.jdbc.command.InsertCommand;
import org.seasar.doma.jdbc.query.SqlInsertQuery;

/**
 * A builder for an SQL INSERT statement.
 *
 * <p>This is not thread safe.
 *
 * <h2>Java</h2>
 *
 * <pre>
 * InsertBuilder builder = InsertBuilder.newInstance(config);
 * builder.sql(&quot;insert into Emp&quot;);
 * builder.sql(&quot;(name, salary)&quot;);
 * builder.sql(&quot;values (&quot;);
 * builder.param(String.class, &quot;SMITH&quot;).sql(&quot;, &quot;);
 * builder.param(BigDecimal.class, new BigDecimal(1000)).sql(&quot;)&quot;);
 * builder.execute();
 * </pre>
 *
 * <h2>built SQL</h2>
 *
 * <pre>
 * insert into Emp
 * (name, salary)
 * values('SMITH', 1000)
 * </pre>
 */
public class InsertBuilder {

  private final BuildingHelper helper;

  private final SqlInsertQuery query;

  private final ParamIndex paramIndex;

  private InsertBuilder(Config config) {
    this.helper = new BuildingHelper();
    this.query = new SqlInsertQuery();
    this.query.setConfig(config);
    this.query.setCallerClassName(getClass().getName());
    this.query.setSqlLogType(SqlLogType.FORMATTED);
    this.paramIndex = new ParamIndex();
  }

  private InsertBuilder(BuildingHelper builder, SqlInsertQuery query, ParamIndex parameterIndex) {
    this.helper = builder;
    this.query = query;
    this.paramIndex = parameterIndex;
  }

  /**
   * Creates a new instance.
   *
   * @param config the configuration
   * @return a builder
   * @throws DomaNullPointerException if {@code config} is {@code null}
   */
  public static InsertBuilder newInstance(Config config) {
    if (config == null) {
      throw new DomaNullPointerException("config");
    }
    return new InsertBuilder(config);
  }

  /**
   * Appends an SQL fragment.
   *
   * @param sql the SQL fragment
   * @return a builder
   * @throws DomaNullPointerException if {@code sql} is {@code null}
   */
  public InsertBuilder sql(String sql) {
    if (sql == null) {
      throw new DomaNullPointerException("sql");
    }
    helper.appendSqlWithLineSeparator(sql);
    return new SubsequentInsertBuilder(helper, query, paramIndex);
  }

  /**
   * Removes the last SQL fragment or parameter.
   *
   * @return a builder
   */
  public InsertBuilder removeLast() {
    helper.removeLast();
    return new SubsequentInsertBuilder(helper, query, paramIndex);
  }

  /**
   * Appends a parameter.
   *
   * <p>The parameter type must be one of basic types or holder types.
   *
   * @param <P> the parameter type
   * @param paramClass the parameter class
   * @param param the parameter
   * @return a builder
   * @throws DomaNullPointerException if {@code paramClass} is {@code null}
   */
  public <P> InsertBuilder param(Class<P> paramClass, P param) {
    if (paramClass == null) {
      throw new DomaNullPointerException("paramClass");
    }
    return appendParam(paramClass, param, false);
  }

  /**
   * Appends a parameter list.
   *
   * <p>The element type of the list must be one of basic types or holder types.
   *
   * @param <E> the element type of the list
   * @param elementClass the element class of the list
   * @param params the parameter list
   * @return a builder
   * @throws DomaNullPointerException if {@code elementClass} or {@code params} is {@code null}
   */
  public <E> InsertBuilder params(Class<E> elementClass, List<E> params) {
    if (elementClass == null) {
      throw new DomaNullPointerException("elementClass");
    }
    if (params == null) {
      throw new DomaNullPointerException("params");
    }
    return appendParams(elementClass, params, false);
  }

  /**
   * Appends a parameter as literal.
   *
   * <p>The parameter type must be one of basic types or holder types.
   *
   * @param <P> the parameter type
   * @param paramClass the parameter class
   * @param param the parameter
   * @return a builder
   * @throws DomaNullPointerException if {@code paramClass} is {@code null}
   */
  public <P> InsertBuilder literal(Class<P> paramClass, P param) {
    if (paramClass == null) {
      throw new DomaNullPointerException("paramClass");
    }
    return appendParam(paramClass, param, true);
  }

  /**
   * Appends a parameter list as literal.
   *
   * <p>The element type of the list must be one of basic types or holder types.
   *
   * @param <E> the element type of the list
   * @param elementClass the element class of the list
   * @param params the parameter list
   * @return a builder
   * @throws DomaNullPointerException if {@code elementClass} or {@code params} is {@code null}
   */
  public <E> InsertBuilder literals(Class<E> elementClass, List<E> params) {
    if (elementClass == null) {
      throw new DomaNullPointerException("elementClass");
    }
    if (params == null) {
      throw new DomaNullPointerException("params");
    }
    return appendParams(elementClass, params, true);
  }

  private <P> InsertBuilder appendParam(Class<P> paramClass, P param, boolean literal) {
    helper.appendParam(new Param(paramClass, param, paramIndex, literal));
    paramIndex.increment();
    return new SubsequentInsertBuilder(helper, query, paramIndex);
  }

  private <E> InsertBuilder appendParams(Class<E> elementClass, List<E> params, boolean literal) {
    InsertBuilder builder = this;
    int index = 0;
    for (E param : params) {
      builder = builder.appendParam(elementClass, param, literal).sql(", ");
      index++;
    }
    if (index == 0) {
      builder = builder.sql("null");
    } else {
      builder = builder.removeLast();
    }
    return builder;
  }

  /**
   * Executes an SQL INSERT statement.
   *
   * @return the affected rows count
   * @throws UniqueConstraintException if an unique constraint violation occurs
   * @throws JdbcException if a JDBC related error occurs
   */
  public int execute() {
    if (query.getMethodName() == null) {
      query.setCallerMethodName("execute");
    }
    prepare();
    InsertCommand command = new InsertCommand(query);
    int result = command.execute();
    query.complete();
    return result;
  }

  private void prepare() {
    query.clearParameters();
    for (Param p : helper.getParams()) {
      query.addParameter(p.name, p.paramClass, p.param);
    }
    query.setSqlNode(helper.getSqlNode());
    query.prepare();
  }

  /**
   * Sets the query timeout limit in seconds.
   *
   * <p>If not specified, the value of {@link Config#getQueryTimeout()} is used.
   *
   * @param queryTimeout the query timeout limit in seconds
   * @see Statement#setQueryTimeout(int)
   */
  public void queryTimeout(int queryTimeout) {
    query.setQueryTimeout(queryTimeout);
  }

  /**
   * Sets the SQL log format.
   *
   * @param sqlLogType the SQL log format type
   */
  public void sqlLogType(SqlLogType sqlLogType) {
    if (sqlLogType == null) {
      throw new DomaNullPointerException("sqlLogType");
    }
    query.setSqlLogType(sqlLogType);
  }

  /**
   * Sets the caller class name.
   *
   * <p>If not specified, the class name of this instance is used.
   *
   * @param className the caller class name
   * @throws DomaNullPointerException if {@code className} is {@code null}
   */
  public void callerClassName(String className) {
    if (className == null) {
      throw new DomaNullPointerException("className");
    }
    query.setCallerClassName(className);
  }

  /**
   * Sets the caller method name.
   *
   * <p>if not specified, {@code execute} is used.
   *
   * @param methodName the caller method name
   * @throws DomaNullPointerException if {@code methodName} is {@code null}
   */
  public void callerMethodName(String methodName) {
    if (methodName == null) {
      throw new DomaNullPointerException("methodName");
    }
    query.setCallerMethodName(methodName);
  }

  /**
   * Returns the built SQL.
   *
   * @return the built SQL
   */
  public Sql<?> getSql() {
    if (query.getMethodName() == null) {
      query.setCallerMethodName("getSql");
    }
    prepare();
    return query.getSql();
  }

  private static class SubsequentInsertBuilder extends InsertBuilder {

    private SubsequentInsertBuilder(
        BuildingHelper builder, SqlInsertQuery query, ParamIndex parameterIndex) {
      super(builder, query, parameterIndex);
    }

    @Override
    public InsertBuilder sql(String sql) {
      if (sql == null) {
        throw new DomaNullPointerException("sql");
      }
      super.helper.appendSql(sql);
      return this;
    }
  }
}
