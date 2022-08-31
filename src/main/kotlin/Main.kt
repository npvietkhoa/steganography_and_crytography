import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.Exception
import kotlin.experimental.xor
import kotlin.system.exitProcess

private const val PROMPT_COMMAND_MESSAGE = "Task (hide, show, exit):"
private const val PROMPT_INPUT_FILE_MESSAGE = "Input image file:"
private const val PROMPT_OUTPUT_FILE_MESSAGE = "Output image file:"
private const val PROMPT_MESSAGE_TO_HIDE_MESSAGE = "Message to hide:"
private const val PROMPT_PASSWORD_MESSAGE = "Password:"
private const val ERROR_INPUT_FILE_NOT_FOUND_MESSAGE = "Can't read input file!"

private const val STUFFING_BYTES = "003" // suffix to mark the end of message

/** Prompts string input using [promptMessage] */
fun promptString(promptMessage: String): String {
    println(promptMessage)

    return readLine()!!
}

/** Prompts for command */
fun promptCommand() = promptString(PROMPT_COMMAND_MESSAGE)

/** Prompts for input file */
fun promptInputFileName() = promptString(PROMPT_INPUT_FILE_MESSAGE)

/** Prompts for output file */
fun promptOutputFileName() = promptString(PROMPT_OUTPUT_FILE_MESSAGE)

/** Prompts user message to hide */
fun promptMessageToHide() = promptString(PROMPT_MESSAGE_TO_HIDE_MESSAGE)

/** Prompts password */
fun promptPassword() = promptString(PROMPT_PASSWORD_MESSAGE)

/** Check if [messageToHide] is too long to be hidden in the [image] */
fun messageFitsImage(messageToHide: String, image: BufferedImage): Boolean {
    return image.height * image.width > (messageToHide.length + 3) * 8
}

/** Encodes [messageToHide] into binary format using [password] and creates it's iterator */
fun createBinaryMessageIterator(messageToHide: String, password: String): Iterator<Char> {
    val messageAsByteArray = encryptDecryptMessage(messageToHide.encodeToByteArray(), password.encodeToByteArray())
    val stuffingBytes = STUFFING_BYTES.encodeToByteArray()
    return (messageAsByteArray.toList() + stuffingBytes.toList()).toByteArray()
        .map {
            String.format(
                "%" + 8 + "s", // format of
                Integer.toBinaryString(it.toInt())
            ).replace(" ".toRegex(), "0")
        }
        .reduce { acc: String, i: String -> acc + i }
        .iterator()
}

/** Encrypt [message] with [password] using xor operator and return its bit array */
fun encryptDecryptMessage(message: ByteArray, password: ByteArray): ByteArray {
    val encryptedMessage = mutableListOf<Byte>()
    var passwordIterator = password.iterator()
    for (b in message) {
        if (!passwordIterator.hasNext()) passwordIterator = password.iterator()
        encryptedMessage.add(b.xor(passwordIterator.nextByte()))
    }

    return encryptedMessage.toByteArray()
}


/**
 * save buffered [image] as default png format file (lossless) with given name [outPutFileName].
 */
fun saveImage(image: BufferedImage, outPutFileName: String) {
    val outputFile = File(outPutFileName)
    ImageIO.write(image, "png", outputFile)
    println("Message saved in $outPutFileName image.")
}

/** Hides [message] in the [image] using [password] */
fun hideMessageInImage(message: String, image: BufferedImage, password: String): BufferedImage {
    val messageIterator = createBinaryMessageIterator(message, password)

    heightLoop@
    for (y in 0 until image.height) {
        widthLoop@
        for (x in 0 until image.width) {
            if (!messageIterator.hasNext()) break@heightLoop
            val bit = messageIterator.next().digitToInt()
            val color = Color(image.getRGB(x, y))
            val blue = color.blue.and(254).or(bit) // The bit data will be saved to the last bit of blue color
            image.setRGB(x, y, Color(color.red, color.green, blue).rgb)
        }
    }

    return image
}



/** Executes hide command */
fun hide() {
    val inputFileName = promptInputFileName()
    val outputFileName = promptOutputFileName()
    val messageToHide = promptMessageToHide()
    val password = promptPassword()
    val inputFile = try {
        File(inputFileName)
    } catch (e: Throwable) {
        throw Exception(ERROR_INPUT_FILE_NOT_FOUND_MESSAGE)
    }
    val image = ImageIO.read(inputFile)!!

    if (!messageFitsImage(messageToHide, image)) {
        throw Exception("The input image is not large enough to hold this message.")
    }

    saveImage(hideMessageInImage(messageToHide, image, password), outputFileName)
}

/** Extracts message from the [image] using password */
fun extractMessage(image: BufferedImage, password: ByteArray): String {
    var accumulated = mutableListOf<String>()
    val bytes = mutableListOf<Byte>()
    outerLoop@ for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val color = Color(image.getRGB(x, y))
            val bit = color.blue.and(1).toString()
            accumulated.add(bit)

            if (accumulated.size % Byte.SIZE_BITS == 0) {
                bytes.add(accumulated.joinToString("").toInt(2).toByte())
                accumulated = mutableListOf()
                if (bytes.takeLast(3).toByteArray().contentEquals(STUFFING_BYTES.encodeToByteArray())) break@outerLoop
            }
        }
    }

    return encryptDecryptMessage(
        bytes.dropLast(STUFFING_BYTES.encodeToByteArray().size).toByteArray(),
        password
    ).toString(Charsets.UTF_8)
}

/** Executes show command */
fun show() {
    val inputFileName = promptInputFileName()
    val inputFile = try {
        File(inputFileName)
    } catch (e: Throwable) {
        throw Exception(ERROR_INPUT_FILE_NOT_FOUND_MESSAGE)
    }
    val password = promptPassword()
    val image = ImageIO.read(inputFile)!!
    println("""
        Message:
        ${extractMessage(image, password.encodeToByteArray())}
    """.trimIndent())
}

fun main() {
    var op: String
    do {
        op = promptCommand()
        try {
            when (op) {
                "hide" -> hide()
                "show" -> show()
                "exit" -> exit()
                else -> println("Wrong task: $op")
            }
        } catch (e: Exception) {
            println(e.message)
        }
    } while(true)
}

fun exit() {
    println("Bye!")
    exitProcess(0)
}