package org.sql2o;

import org.joda.time.DateTime;
import org.sql2o.reflection.Pojo;
import org.sql2o.reflection.PojoMetadata;
import org.sql2o.tools.NamedParameterStatement;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lars
 * Date: 5/18/11
 * Time: 8:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class Query {

    public Query(Connection connection, String queryText) {
        this.connection = connection;

        try{
            statement = new NamedParameterStatement(connection.getJdbcConnection(), queryText);
        }
        catch(Exception ex){
            throw new RuntimeException(ex);
        }

        this.columnMappings = connection.getSql2o().getDefaultColumnMappings() == null ? new HashMap<String, String>() : connection.getSql2o().getDefaultColumnMappings();
        this.caseSensitive = connection.getSql2o().isDefaultCaseSensitive();
        this.methodsMap = new HashMap<String, Method>();
    }

    private Connection connection;

    private Map<String, String> columnMappings;
    private Map<String, Method> methodsMap;

    private NamedParameterStatement statement;

    private boolean caseSensitive;

    public Query addParameter(String name, Object value){
        try{
            statement.setObject(name, value);
        }
        catch(Exception ex){
            throw new RuntimeException(ex);
        }
        return this;
    }

    public Query addParameter(String name, int value){
        try{
            statement.setInt(name, value);
        }
        catch(Exception ex){
            throw new RuntimeException(ex);
        }
        return this;
    }

    public Query addParameter(String name, long value){
        try{
            statement.setLong(name, value);
        }
        catch(Exception ex){
            throw new RuntimeException(ex);
        }
        return this;
    }

    public Query addParameter(String name, String value){
        try{
            statement.setString(name, value);
        }
        catch(Exception ex){
            throw new RuntimeException(ex);
        }
        return this;
    }

    public Query addParameter(String name, Timestamp value){
        try{
            statement.setTimestamp(name,value);
        }
        catch(Exception ex){
            throw new RuntimeException(ex);
        }
        return this;
    }

    public Query addParameter(String name, Date value){
        try{
            statement.setDate(name, value);
        }
        catch (Exception ex){
            throw new RuntimeException(ex);
        }

        return this;
    }

    public Query addParameter(String name, java.util.Date value){
        Date sqlDate = value == null ? null : new Date(value.getTime());
        return addParameter(name, sqlDate);
    }

    public Query addParameter(String name, Time value){
        try {
            statement.setTime(name,value);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public Query addParameter(String name, DateTime value){
        return addParameter(name, value.toDate());
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public Query setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        return this;
    }

    public <T> List<T> executeAndFetch(Class returnType){
        List list = new ArrayList();
        PojoMetadata metadata = new PojoMetadata(returnType, this.isCaseSensitive());
        try{
            java.util.Date st = new java.util.Date();
            ResultSet rs = statement.executeQuery();
            System.out.println(String.format("execute query time: %s", new java.util.Date().getTime() - st.getTime()));

            ResultSetMetaData meta = rs.getMetaData();

            while(rs.next()){

                Pojo pojo = new Pojo(metadata, this.isCaseSensitive());

                //Object obj = returnType.newInstance();
                for(int colIdx = 1; colIdx <= meta.getColumnCount(); colIdx++){
                    String colName = meta.getColumnName(colIdx);
                    pojo.setProperty(colName, rs.getObject(colIdx));
                }

                list.add(pojo.getObject());
            }

            rs.close();
        }
        catch(SQLException ex){
            throw new Sql2oException("Database error", ex);
        }
        finally {
            closeConnectionIfNecessary();
        }

        return list;
    }

    public <T> T executeAndFetchFirst(Class returnType){
        List l = this.executeAndFetch(returnType);
        if (l.size() == 0){
            return null;
        }
        else{
            return (T)l.get(0);
        }
    }

    public Connection executeUpdate(){
        return executeUpdate(false);
    }

    public Connection executeUpdate(boolean getKeys){
        try{
            this.connection.setResult(statement.executeUpdate());
            if (getKeys){
                this.connection.setKeys(statement.getStatement().getGeneratedKeys());
            }
            connection.setCanGetKeys(getKeys);
        }
        catch(SQLException ex){
            this.connection.rollback();
            throw new RuntimeException(ex);
        }
        finally {
            closeConnectionIfNecessary();
        }

        return this.connection;
    }

    public Object executeScalar(){
        try {
            ResultSet rs = this.statement.executeQuery();
            if (rs.next()){
                return rs.getObject(1);
            }
            else{
                return null;
            }

        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally{
            closeConnectionIfNecessary();
        }
    }

    public <T> List<T> executeScalarList(){
        List<T> list = new ArrayList<T>();
        try{
            ResultSet rs = this.statement.executeQuery();
            while(rs.next()){
                list.add((T)rs.getObject(1));
            }

            return list;
        }
        catch(SQLException ex){
            throw new RuntimeException(ex);
        }
        finally{
            closeConnectionIfNecessary();
        }
    }

    /************** batch stuff *******************/

    public Query addToBatch(){
        try {
            statement.addBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return this;
    }

    public Connection executeBatch(){
        try {
            statement.executeBatch();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }
        finally {
            closeConnectionIfNecessary();
        }

        return this.connection;
    }

    /*********** column mapping ****************/

    public Map<String, String> getColumnMappings() {
        return columnMappings;
    }

    public Query addColumnMapping(String columnName, String fieldName){
        this.columnMappings.put(columnName, fieldName);

        return this;
    }

    /************** private stuff ***************/
    private void closeConnectionIfNecessary(){
        try{
            if (!this.connection.getJdbcConnection().isClosed() && this.connection.getJdbcConnection().getAutoCommit() && statement != null){
                this.connection.getJdbcConnection().close();
                statement.close();
            }
        }
        catch (Exception ex){
            throw new Sql2oException("Error while attempting to close connection", ex);
        }
    }

}
