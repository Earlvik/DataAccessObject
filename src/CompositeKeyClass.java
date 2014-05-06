/**
 * Created by Earlviktor on 06.05.2014.
 */
@DbProjectable(tableName = "composite")
public class CompositeKeyClass {
    @KeyField
    private String name;

    @KeyField
    private int age;

    private boolean isMale;

    private double height;

    public CompositeKeyClass(String name, int age, boolean isMale, double height) {
        this.name = name;
        this.age = age;
        this.isMale = isMale;
        this.height = height;
    }

    public CompositeKeyClass(){
        name ="";
        age=0;
        isMale = false;
        height=0.0;
    }

    public String GetName() {
        return name;
    }

    public void SetName(String name) {
        this.name = name;
    }

    public int GetAge() {
        return age;
    }

    public void SetAge(int age) {
        this.age = age;
    }

    public boolean GetIsMale() {
        return isMale;
    }

    public void SetIsMale(boolean isMale) {
        this.isMale = isMale;
    }

    public double GetHeight() {
        return height;
    }

    public void SetHeight(double height) {
        this.height = height;
    }


}
