/**
 * Created by Earlviktor on 30.04.2014.
 */
@DbProjectable(tableName = "Sample")
public class SampleClass {



    @KeyField
    private int id;

    private String name;
    private String lastName;

    public SampleClass(String name, String lastName, int number){
        this.id = number;
        this.name = name;
        this.lastName = lastName;
    }

    public SampleClass(){
        id = 0;
        name = lastName = "";
    }

    public void SetName(String name){
          this.name = name;
    }

    public String GetName(){
        return name;
    }

    public void SetLastName(String lastName){
        this.lastName = lastName;
    }

    public String GetLastName(){
        return lastName;
    }

    public void SetId(int id){
        this.id = id;
    }

    public int GetId(){
        return id;
    }
    @Override
    public boolean equals(Object other){
       if(!(other instanceof SampleClass )) return false;
       SampleClass obj = (SampleClass)other;
       return (name.equals(obj.name) && lastName.equals(obj.lastName) && id==obj.id);
    }
}
