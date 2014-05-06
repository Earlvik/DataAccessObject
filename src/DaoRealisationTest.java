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

public class DaoRealisationTest {
    DaoRealisation<SampleClass> testSubject;

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
        String query = "DROP TABLE sample";
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
         testSubject = new DaoRealisation<SampleClass>("localhost","test","com.mysql.jdbc.Driver","mysql://","user","password",SampleClass.class);

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
        testSubject.insert(new SampleClass("John","Dow",1));
    }

    @Test
    public void testRepetitiveInsert(){
        SampleClass object = new SampleClass("Petr","Petrov",2);
        testSubject.insert(object);
        testSubject.insert(object);
        List<SampleClass> result = testSubject.selectAll();
        Assert.assertTrue(result.size() == 1);

    }

    @Test
    public void testUpdate() throws Exception {
        testSubject.insert(new SampleClass("Vikkkktor","Dow",0));
        testSubject.update(new SampleClass("John","Petrov",0));
        SampleClass result = testSubject.selectByKey(new SampleClass("","",0));
        Assert.assertTrue("Looked for John Petrov, found "+result.GetName()+" "+result.GetLastName(),result.GetName().equals("John")
                && result.GetLastName().equals("Petrov") && result.GetId()==0);

    }

    @Test
    public void testDeleteByKey() throws Exception {
        testSubject.insert(new SampleClass("John","Dow",34));
        testSubject.insert( new SampleClass("Petr","Petrov",43));
        testSubject.deleteByKey(new SampleClass("","",43));
        List<SampleClass> result = testSubject.selectAll();
        Assert.assertTrue(result.size() == 1);

    }

    @Test
    public void testSelectByKey() throws Exception {
        testSubject.insert(new SampleClass("John","Dow",34));
        testSubject.insert( new SampleClass("Petr","Petrov",43));
        SampleClass result = testSubject.selectByKey(new SampleClass("","",43));
        Assert.assertTrue("Looked for Petr Petrov, found "+result.GetName()+" "+result.GetLastName(),result.GetName().equals("Petr")
                && result.GetLastName().equals("Petrov") && result.GetId()==43);
    }

    @Test
    public void testSelectAll() throws Exception {
        SampleClass obj1 = new SampleClass("John","Dow",34);
        SampleClass obj2 =  new SampleClass("Petr","Petrov",43);
        testSubject.insert(obj1);
        testSubject.insert(obj2);
        List<SampleClass> result = testSubject.selectAll();
        Assert.assertTrue(result.size() == 2);
        Assert.assertTrue("Looked for John Dow, not found ",result.contains(obj1));
        Assert.assertTrue("Looked for Petr Petrov, not found ",result.contains(obj2));
    }

    @Test
    public void testInjection() throws Exception {
        SampleClass obj1 = new SampleClass("Jonh'DROP TABLE test.sample","Hacker",404);
        SampleClass obj2 = new SampleClass("Regular","Client",21);
        testSubject.insert(obj1);
        testSubject.insert(obj2);
        SampleClass result = testSubject.selectByKey(new SampleClass("","",21));
        Assert.assertTrue("Looked for Regular Client, found "+result.GetName()+" "+result.GetLastName(),result.GetName().equals("Regular")
                && result.GetLastName().equals("Client") && result.GetId()==21);
    }
}