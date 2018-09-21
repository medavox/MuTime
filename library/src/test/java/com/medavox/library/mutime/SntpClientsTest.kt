package com.medavox.library.mutime


import java.util.*
import kotlin.test.Test
import kotlin.test.fail

/**
 * @author Adam Howard
 * @since 20/09/2018
 */
class SntpClientsTest {
    private val random = Random()

    @Test
    fun requestTime() {
        TODO()
    }

    @Test
    fun `read$library`() {
        val input = ByteArray(4)
        random.nextBytes(input)
        val javaImplResult = JavaSntpClient.read(input, 0)
        val kotlinImplResult = SntpClient.read(input, 0)
        if(kotlinImplResult != javaImplResult) {
            System.err.println("java and kotlin return values do not match " +
                    "for method read(ByteArray, Int):Long!\n" +
                    "java result: $javaImplResult; kotlin result: $kotlinImplResult")
            fail()
        }
    }

    @Test
    fun `writeTimeStamp$library`() {
        val javaInput = ByteArray(8)
        val kotlinInput = ByteArray(8)
        val inputTime = System.currentTimeMillis()
        random.nextBytes(javaInput)
        random.nextBytes(kotlinInput)
        val javaImplResult = JavaSntpClient.writeTimeStamp(javaInput, 0, inputTime)
        val kotlinImplResult = SntpClient.writeTimeStamp(kotlinInput, 0, inputTime)
        if(kotlinImplResult != javaImplResult) {
            System.err.println("java and kotlin return values do not match " +
                    "for method writeTimestamp(ByteArray, Int, Long):Long!\n" +
                    "java result: $javaImplResult; kotlin result: $kotlinImplResult")
            fail()
        }
    }

    @Test
    fun `readTimeStamp$library`() {
        val input = ByteArray(8)
        random.nextBytes(input)
        val javaImplResult = JavaSntpClient.readTimeStamp(input, 0)
        val kotlinImplResult = SntpClient.readTimeStamp(input, 0)
        if(kotlinImplResult != javaImplResult) {
            System.err.println("java and kotlin return values do not match " +
                    "for method readTimestamp(ByteArray, Int):Long!\n" +
                    "java result: $javaImplResult; kotlin result: $kotlinImplResult")
            fail()
        }
    }

    @Test
    fun `ui$library`() {

        val preInput = ByteArray(1)
        random.nextBytes(preInput)
        val input = preInput[0]
        val javaImplResult = JavaSntpClient.ui(input)
        val kotlinImplResult = SntpClient.ui(input)
        if(kotlinImplResult != javaImplResult) {
            System.err.println("java and kotlin return values do not match " +
                    "for method ui(Byte):Int!\n" +
                    "java result: $javaImplResult; kotlin result: $kotlinImplResult")
            fail()
        }
    }

    @Test
    fun `doubleMillis$library`() {
        val input:Long = random.nextLong()
        val javaImplResult = JavaSntpClient.doubleMillis(input)
        val kotlinImplResult = SntpClient.doubleMillis(input)
        if(kotlinImplResult != javaImplResult) {
            System.err.println("java and kotlin return values do not match " +
                    "for method doubleMillis(Long):Double!\n" +
                    "java result: $javaImplResult; kotlin result: $kotlinImplResult")
            fail()
        }
    }



}