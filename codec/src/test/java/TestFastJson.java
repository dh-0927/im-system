import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dh.im.codec.TestPerson;
import org.junit.Test;

public class TestFastJson {

    @Test
    public void testFastJSON() {
        TestPerson person = new TestPerson();
        person.setName("zhangsan");
        person.setAge(33);
        String json = JSON.toJSONString(person);
        System.out.println("对象->json：" + json);

        JSONObject parse = (JSONObject) JSONObject.parse(json);
        System.out.println("JSON->对象" + parse);
    }
}
