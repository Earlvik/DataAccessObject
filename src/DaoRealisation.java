import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Earlviktor on 30.04.2014.
 */

/**
 * Класс, реализующий отображение класса Т в таблицу базы данных.
 * Класс Т должен иметь следующий формат:
 *      Иметь поля только примитивных типов или строк (int,double,boolean,String...)
 *      Иметь getters&setters для всех полей
 *      Быть помеченным аннотацией DbProjectable
 *      Иметь хотя бы одно поле, помеченное аннотацией KeyField
 *      Иметь конструктор без параметров *
 * @param <T>  Отображаемый класс.
 */
public class DaoRealisation<T> implements ReflectionJdbcDao<T> {
    /**
     * Имя сервера СУБД
     */
    String serverName,
    /**
     * Имя схемы(базы данных)
     */
            schemeName,
    /**
     * Полное имя класса драйвера СУБД
     */
            driverName,
    /**
     * Имя пользователя СУБД
     */
            login,
    /**
     * Пароль СУБД
     */
            password,
    /**
     * Префикс для URL базы данных в формате jdbc: + префикс + имя сервера + / + имя бд
     */
            dbmcPrefix;
    /**
     * Класс, проецируемый в БД
     */
    Class<T> tableClass;
    /**
     * Имя таблицы
     */
    String tableName;
    /**
     * Список неключевых полей класса в формате имя->тип
     */
    Map<String,Class> fields;
    /**
     * Список ключевых полей класса в формате имя->тип
     */
    Map<String,Class> keyFields;
    /**
     * Является ли ключ таблицы составным
     */
    boolean compositeKey = false;
    /**
     *  Таблица соответствия примитивных классов и классов-обёрток
     */
    public final static Map<Class<?>, Class<?>> CLASS_MAP = new HashMap<Class<?>, Class<?>>();
    static {
        CLASS_MAP.put(boolean.class, Boolean.class);
        CLASS_MAP.put(byte.class, Byte.class);
        CLASS_MAP.put(short.class, Short.class);
        CLASS_MAP.put(char.class, Character.class);
        CLASS_MAP.put(int.class, Integer.class);
        CLASS_MAP.put(long.class, Long.class);
        CLASS_MAP.put(float.class, Float.class);
        CLASS_MAP.put(double.class, Double.class);
    }

    /**
     * Создаёт, если необходимо, таблицу в базе данных и считывает из класса данные о полях
     * @param serverName имя сервера СУБД
     * @param schemeName имя схемы(БД)
     * @param driverName имя класса драйвера СУБД
     * @param dbmcPrefix префикс для URL БД
     * @param login имя пользователя БД
     * @param password пароль БД
     * @param tableClass класс, проецируемый в БД
     */
    public DaoRealisation(String serverName, String schemeName, String driverName,String dbmcPrefix, String login, String password, Class<T> tableClass){

       if(!tableClass.isAnnotationPresent(DbProjectable.class)){
          System.err.println("Class has no DbProjectable annotation");
           throw new IllegalArgumentException("Class has no DbProjectable annotation");
       }
        try {
            tableClass.getConstructor();
        } catch(NoSuchMethodException e){
            throw new IllegalArgumentException("Class has no empty public constructor");
        }

        this.fields = new HashMap<String, Class>();

        this.keyFields = new HashMap<String, Class>();

        this.serverName = serverName;

        this.schemeName = schemeName;

        this.driverName = driverName;

        this.dbmcPrefix = dbmcPrefix;

        this.login = login;

        this.password = password;

        this.tableClass = tableClass;
        int keyFieldsNum = 0;
        List<String> keyFieldsNames = new ArrayList<String>();
        List<String> fieldNames = new ArrayList<String>();
        Field[] fields = tableClass.getDeclaredFields();
        for(Field field: fields){
            if(Modifier.isStatic(field.getModifiers())){
                continue;
            }
           if(field.isAnnotationPresent(KeyField.class)){
               keyFieldsNum++;
               keyFieldsNames.add(field.getName());
               this.keyFields.put(field.getName(),field.getType());
           }else{
               fieldNames.add(field.getName());
               this.fields.put(field.getName(),field.getType());
           }
           if(!hasAccessors(field)){
               System.err.println("The field "+field.getName()+" does not have Set- and/or Get- accessors");
               throw new IllegalArgumentException("The field "+field.getName()+" does not have Set- and/or Get- accessors");
           }
        }
        if(keyFieldsNum>1) compositeKey = true;
        if(keyFieldsNum == 0){
            System.err.println("There are no fields marked as keys in the class");
            throw new IllegalArgumentException("There are no fields marked as keys in the class");
        }
        //Создание таблицы
        try{
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            DbProjectable annotation = (DbProjectable)tableClass.getAnnotation(DbProjectable.class);
            String tableName = annotation.tableName();
            this.tableName = filterSymbols(tableName);
            ResultSet set;
            String query = "SHOW TABLES LIKE ?";
            PreparedStatement prepStatement = connection.prepareStatement(query);
            prepStatement.setString(1,tableName);
            set = prepStatement.executeQuery();
            if(set.next()) return;
            query = "CREATE TABLE "+tableName+" (";
            for(String fieldName: fieldNames){
                Field field = tableClass.getDeclaredField(fieldName);
                String type = getSqlType(field.getType());
                query+= camelToUnderScore(field.getName())+" "+type;
                if(CLASS_MAP.containsKey(field.getType())){
                    query+=" NOT NULL ";
                }
                query+=",";
            }
            String key = "PRIMARY KEY( ";
            for(String fieldName: keyFieldsNames){
                Field field = tableClass.getDeclaredField(fieldName);
                this.keyFields.put(fieldName,field.getType());
                String type = getSqlType(field.getType());
                query+= camelToUnderScore(field.getName())+" "+type+", ";
                key+=field.getName()+",";
            }
            key=key.substring(0,key.length()-1)+")";
            if(keyFieldsNum>1){
               compositeKey = true;
            }
            query +=key+" )";
            statement.executeUpdate(query);
            connection.close();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();

        }
        catch(SQLException e){
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    /**
     * Создаёт соединение с базой данных
     * @return объект соединения
     * @throws ClassNotFoundException  отсутствует драйвер
     * @throws SQLException соединение не удалось установить
     */
    private Connection getConnection() throws ClassNotFoundException, SQLException{
        Connection connection;
            Class.forName(driverName);
            String url = "jdbc:"+dbmcPrefix + serverName + "/" + schemeName;
            connection = DriverManager.getConnection(url, login, password);
            return connection;
    }

    /**
     * Устанавливает соответствие между классами Java и типами данных SQL
     * @param fieldType входной класс Java
     * @return имя соответствующего типа SQL
     */
    private String getSqlType(Class<?> fieldType) {
        if(fieldType == String.class){
            return " VARCHAR(255) ";
        }
        if(fieldType.getName().equals("int") || fieldType == Integer.class){
            return " INTEGER ";
        }
        if(fieldType.getName().equals("byte") || fieldType == Byte.class){
            return " TINYINT ";
        }
        if(fieldType.getName().equals("long") || fieldType == Long.class){
            return " BIGINT ";
        }
        if(fieldType.getName().equals("float") || fieldType == Float.class){
            return " FLOAT ";
        }
        if(fieldType.getName().equals("double") || fieldType == Double.class){
            return " DOUBLE PRECISION ";
        }
        if(fieldType.getName().equals("boolean") || fieldType == Boolean.class){
            return " BIT ";
        }
        throw new IllegalArgumentException("Type is not supported");
    }

    /**
     * Проверяет наличие Get и Set аксессоров в классе
     * @param field поле, для которого проходит проверка
     * @return
     */
    public boolean hasAccessors(Field field){
        Class declaringClass = field.getDeclaringClass();
        String fieldName = field.getName();
        fieldName = Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
        try {
            Method getMethod = declaringClass.getMethod("Get"+fieldName);
            if(getMethod.getReturnType() != field.getType()){
                return false;
            }

            Method setMethod = declaringClass.getMethod("Set"+fieldName,field.getType());
            if(!setMethod.getReturnType().getName().equals("void")){
                return false;
            }
        } catch (NoSuchMethodException e) {
            return false;
        }
        return true;
    }
    @Override
    public void insert(T object) {
        try {
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            String query = "INSERT INTO "+tableName+" (";
            String values =" VALUES (";
            for(String fieldName:fields.keySet()){
                query+=camelToUnderScore(fieldName)+",";
                String methodName = "Get"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
                Object value = tableClass.getMethod(methodName).invoke(object);
                Class fieldClass =  fields.get(fieldName);
                if(CLASS_MAP.containsKey(fieldClass)){
                    fieldClass = CLASS_MAP.get(fieldClass);
                }
                String valueStr = fieldClass.cast(value).toString();
                valueStr = filterSymbols(valueStr);
                if(fieldClass == String.class) values+="'"+filterSymbols(valueStr)+"',";
                else values+=filterSymbols(valueStr)+",";
            }
            for(String fieldName:keyFields.keySet()){
                query+=camelToUnderScore(fieldName)+",";
                String methodName = "Get"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
                Object value = tableClass.getMethod(methodName).invoke(object);
                Class fieldClass =  keyFields.get(fieldName);
                if(CLASS_MAP.containsKey(fieldClass)){
                    fieldClass = CLASS_MAP.get(fieldClass);
                }
                String valueStr = fieldClass.cast(value).toString();
                if(fieldClass == String.class) values+="'"+filterSymbols(valueStr)+"',";
                else values+=filterSymbols(valueStr)+",";
            }
            query = query.substring(0,query.length()-1)+") ";
            values = values.substring(0,values.length()-1)+")";
            query+=values;
            statement.execute(query);
            connection.close();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }catch(MySQLIntegrityConstraintViolationException e){
            System.out.println("Object with the same primary key already exists in database");
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(T object) {
        Connection connection = null;
        try {
            connection = getConnection();
            Statement statement = connection.createStatement();
            String query = "UPDATE "+tableName+" SET ";
            for(String fieldName:fields.keySet()){
                query+=camelToUnderScore(fieldName)+"=";
                String methodName = "Get"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
                Object value = tableClass.getMethod(methodName).invoke(object);
                Class fieldClass =  fields.get(fieldName);
                if(CLASS_MAP.containsKey(fieldClass)){
                    fieldClass = CLASS_MAP.get(fieldClass);
                }
                String valueStr = fieldClass.cast(value).toString();
                if(fieldClass == String.class) query+="'"+filterSymbols(valueStr)+"',";
                else query+=filterSymbols(valueStr)+",";
            }
            query = query.substring(0,query.length()-1);
            query+=" WHERE ";
            for(String fieldName:keyFields.keySet()){
                query+=camelToUnderScore(fieldName)+"=";
                String methodName = "Get"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
                Object value = tableClass.getMethod(methodName).invoke(object);
                Class fieldClass =  keyFields.get(fieldName);
                if(CLASS_MAP.containsKey(fieldClass)){
                    fieldClass = CLASS_MAP.get(fieldClass);
                }
                String valueStr = fieldClass.cast(value).toString();
                if(fieldClass == String.class) query+="'"+filterSymbols(valueStr)+"' AND ";
                else query+=filterSymbols(valueStr)+" AND ";
            }
            query = query.substring(0,query.length()-5);
            statement.execute(query);
            connection.close();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void deleteByKey(T key) {
        try {
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            String query = "DELETE FROM "+tableName+" WHERE ";
            for(String fieldName:keyFields.keySet()){
                query+=camelToUnderScore(fieldName)+"=";
                String methodName = "Get"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
                Object value = tableClass.getMethod(methodName).invoke(key);
                Class fieldClass =  keyFields.get(fieldName);
                if(CLASS_MAP.containsKey(fieldClass)){
                    fieldClass = CLASS_MAP.get(fieldClass);
                }
                String valueStr = fieldClass.cast(value).toString();
                if(fieldClass == String.class) query+="'"+filterSymbols(valueStr)+"' AND ";
                else query+=filterSymbols(valueStr)+" AND ";
            }
            query = query.substring(0,query.length()-5);
            statement.execute(query);
            connection.close();
        }catch (SQLException e){
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public T selectByKey(T key) {

        try {
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            String query = "SELECT * FROM "+tableName+" WHERE ";
            for(String fieldName:keyFields.keySet()){
                query+=camelToUnderScore(fieldName)+"=";
                String methodName = "Get"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
                Object value = tableClass.getMethod(methodName).invoke(key);
                Class fieldClass =  keyFields.get(fieldName);
                if(CLASS_MAP.containsKey(fieldClass)){
                    fieldClass = CLASS_MAP.get(fieldClass);
                }
                String valueStr = fieldClass.cast(value).toString();
                if(fieldClass == String.class) query+="'"+filterSymbols(valueStr)+"' AND ";
                else query+=filterSymbols(valueStr)+" AND ";
            }
            query = query.substring(0,query.length()-5);
            ResultSet result = statement.executeQuery(query);

            List<T> results = CreateObjects(result);
            connection.close();
            if(!results.isEmpty()){
                return results.get(0);
            }
        }catch (SQLException e){
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Возвращает список объектов, созданных на основе ответа, пришедшего из БД
     * @param set набор данных, результат запроса
     * @return список сконструированных объектов
     * @throws SQLException результат запроса оказался недоступен
     * @throws NoSuchMethodException метод для задания значение поля не был найден
     * @throws IllegalAccessException не удалось получить доступ к конструктору класса
     */
    private List<T> CreateObjects(ResultSet set) throws SQLException, NoSuchMethodException, IllegalAccessException {
        List<T> result = new ArrayList<T>();
         while(set.next()){
             try {
                 T object = tableClass.newInstance();

             for(String fieldName:fields.keySet()){
                 Object value = set.getObject(camelToUnderScore(fieldName));
                 Class fieldType = fields.get(fieldName);
                 Class wrapper;
                 if(CLASS_MAP.containsKey(fieldType)){
                     wrapper = CLASS_MAP.get(fieldType);
                 }else{
                     wrapper = fieldType;
                 }
                Method setMethod = tableClass.getMethod("Set"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1),fieldType);
                setMethod.invoke(object, wrapper.cast(value));
             }
                 for(String fieldName:keyFields.keySet()){
                     Object value = set.getObject(fieldName);
                     Class fieldType = keyFields.get(fieldName);
                     Class wrapper;
                     if(CLASS_MAP.containsKey(fieldType)){
                         wrapper = CLASS_MAP.get(fieldType);
                     }else{
                         wrapper = fieldType;
                     }
                     Method setMethod = tableClass.getMethod("Set"+Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1),fieldType);
                     if(CLASS_MAP.containsKey(fieldType)){
                         fieldType = CLASS_MAP.get(fieldType);
                     }
                     setMethod.invoke(object, wrapper.cast(value));
                 }
              result.add(object);
             } catch (InstantiationException e) {
                 e.printStackTrace();
             } catch (InvocationTargetException e) {
                 e.printStackTrace();
             }
         }
        return result;
    }

    @Override
    public List<T> selectAll() {
        List<T> results = new ArrayList<T>();
        try {
            Connection connection = getConnection();
            Statement statement = connection.createStatement();
            String query = "SELECT * FROM " + tableName;
            ResultSet result = statement.executeQuery(query);
            results =  CreateObjects(result);
        }catch(SQLException e){
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Фильтует символы апострофа, кавычек и backslash
     * @param string
     * @return
     */
    private String filterSymbols(String string){
             return string.replaceAll("['\"\\\\]","");
    }

    /**
     * Переводит строку из camelCase в under_score
     * @param string такаяСтрока
     * @return такая_строка
     */
    private String camelToUnderScore(String string){
        String regex = "([a-z])([A-Z])";
        String replacement = "$1_$2";
        return string.replaceAll(regex,replacement).toLowerCase();
    }

    /**
     * Переводит строку из under_score в camelCase
     * @param string такая_строка
     * @return такаяСтрока
     */
    private String underScoreToCamel(String string){
        Pattern p = Pattern.compile( "_([a-zA-Z])" );
        Matcher m = p.matcher(string);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1).toUpperCase());
        }
        m.appendTail(sb);
       return sb.toString();
    }
}
