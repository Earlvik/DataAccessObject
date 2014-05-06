import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

public class DaoCompositeKeyTest {
    DaoRealisation<CompositeKeyClass> testSubject;

    public void DropTestTable() throws Exception{
        String driverName = "com.mysql.jdbc.Driver";
        Connection connection;
        Class.forName(driverName);
        String serverName = "localhost";
        String schemeName = "test";
        String url = "jdbc:mysql://" + serverName + "/" + schemeName;
        String login = "user";
        String password = "password";
        connection = DriverManager.getConnection(url, login, password);
        Statement statement = connection.createStatement();
        String query = "DROP TABLE composite";
        try {
            statement.execute(query);
        }catch(Exception e){
            System.out.println("Table not found. That's OK actually");
        }
        connection.close();
    }
    @Before
    public void setUp() throws Exception {
        DropTestTable();
        testSubject = new DaoRealisation<CompositeKeyClass>("localhost","test","com.mysql.jdbc.Driver","mysql://","user","password",CompositeKeyClass.class);

    }

    @Test
    public void testHasAccessors() throws Exception {
        Class testClass = SampleClass.class;
        for(Field field:testClass.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())){
                continue;
            }
            Assert.assertTrue("Accessors not found for field: "+field.getName(),testSubject.hasAccessors(field));
        }
    }

    @Test
    public void testInsert() throws Exception {
        testSubject.insert(new CompositeKeyClass("John",18,true,1.84));
    }

    @Test
    public void testRepetitiveInsert(){
        CompositeKeyClass object = new CompositeKeyClass("John",18,true,1.84);
        testSubject.insert(object);
        testSubject.insert(object);
        List<CompositeKeyClass> result = testSubject.selectAll();
        Assert.assertTrue(result.size() == 1);
    }

    @Test
    public void testUpdate() throws Exception {
        testSubject.insert(new CompositeKeyClass("John",18,true,1.84));
        testSubject.update(new CompositeKeyClass("John",18,false,1.87));
        CompositeKeyClass result = testSubject.selectByKey(new CompositeKeyClass("John",18,true,0));
        Assert.assertTrue("John's height should be 1.87, but was "+result.GetHeight(),result.GetHeight()==1.87);
    }

    @Test
    public void testDeleteByKey() throws Exception {
        testSubject.insert(new CompositeKeyClass("John",18,true,1.84));
        testSubject.insert(new CompositeKeyClass("Jane",21,true,1.84));
        testSubject.deleteByKey(new CompositeKeyClass("John",18,true,0));
        List<CompositeKeyClass> result = testSubject.selectAll();
        Assert.assertTrue(result.size() == 1);
    }

    @Test
    public void testSelectByKey() throws Exception {
        testSubject.insert(new CompositeKeyClass("John",18,true,1.84));
        testSubject.insert(new CompositeKeyClass("John",27,true,1.89));
        CompositeKeyClass result = testSubject.selectByKey(new CompositeKeyClass("John",18,true,1.84));
        Assert.assertTrue("Looked for John aged 18, found "+result.GetName()+" aged "+result.GetAge(),result.GetName().equals("John")
                && result.GetAge()==18);
    }


}