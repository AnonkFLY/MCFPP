package top.mcfpp.test

import top.mcfpp.test.util.MCFPPStringTest
import kotlin.test.Test

class MNITest {

    @Test
    fun baseMNITest(){
        val test =
            """
                func main(){
                    int qwq = 1;
                    print(qwq::jvm.identifier);
                    print(qwq::jvm.sbObject.toString());
                }
            """.trimIndent()
        MCFPPStringTest.readFromString(test)
    }

}