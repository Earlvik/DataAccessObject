import java.lang.annotation.*;

/**
 * Created by Earlviktor on 30.04.2014.
 */
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface DbProjectable {
    String tableName();
}



