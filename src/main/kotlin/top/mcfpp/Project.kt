package top.mcfpp

import com.alibaba.fastjson2.*
import com.ibm.icu.impl.data.ResourceReader
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.apache.logging.log4j.*
import top.mcfpp.annotations.InsertCommand
import top.mcfpp.io.LibReader
import top.mcfpp.io.LibWriter
import top.mcfpp.io.MCFPPFile
import top.mcfpp.core.lang.MCFloat
import top.mcfpp.core.lang.UnresolvedVar
import top.mcfpp.core.lang.Var
import top.mcfpp.model.*
import top.mcfpp.model.field.GlobalField
import top.mcfpp.model.function.Function
import top.mcfpp.util.LogProcessor
import java.io.*
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.jar.JarFile
import kotlin.collections.ArrayList
import kotlin.io.path.*


/**
 * 一个工程。工程文件包含了这个mcfpp工程编译需要的所有信息。编译器将会以这个文件为入口开始编译。
 * 同时，这个工程文件的名字也是此文件编译生成的数据包的命名空间。
 */
object Project {

    private var logger: Logger = LogManager.getLogger("mcfpp")

    var config = ProjectConfig()

    var ctx: ParserRuleContext? = null

    /**
     * 当前解析文件的语法树
     */
    var trees:MutableMap<MCFPPFile,ParseTree> = mutableMapOf()

    /**
     * 当前的命名空间
     */
    var currNamespace = config.rootNamespace

    /**
     * 工程中的总错误数量
     */
    var errorCount = 0

    /**
     * 工程中的总警告数量
     */
    var warningCount = 0

    lateinit var mcfppTick : Function

    lateinit var mcfppLoad : Function

    lateinit var mcfppInit : Function

    /**
     * 常量池
     */
    val constants : HashMap<Any, Var<*>> = HashMap()

    /**
     * 宏命令
     */
    val macroFunction : LinkedHashMap<String, String> = LinkedHashMap()

    var compileStage = 0

    const val PRE_INIT = 0
    const val INIT = PRE_INIT + 1
    const val READ_LIB = INIT + 1
    const val INDEX_TYPE = READ_LIB + 1
    const val RESOLVE_FIELD = INDEX_TYPE + 1
    const val RUN_ANNOTATION = RESOLVE_FIELD + 1
    const val COMPILE = RUN_ANNOTATION + 1
    const val OPTIMIZATION = COMPILE + 1
    const val GEN_INDEX = OPTIMIZATION + 1
    const val GEN_DATAPACK = GEN_INDEX + 1

    /**
     * 编译阶段处理器。每个阶段的处理器都会在对应的阶段被调用。
     */
    val stageProcessor = Array(GEN_DATAPACK + 1) { ArrayList<()->Unit>() }

    var classLoader: ClassLoader = Thread.currentThread().contextClassLoader

    val files = ArrayList<MCFPPFile>()

    /**
     * 初始化
     */
    fun init() {
        compileStage++
        //全局缓存初始化
        GlobalField.init()
        ctx = null
        trees.clear()
        currNamespace = config.rootNamespace
        errorCount = 0
        warningCount = 0
        constants.clear()
        macroFunction.clear()
        classLoader = Thread.currentThread().contextClassLoader
        files.clear()
        stageProcessor[compileStage].forEach { it() }
    }

    fun readConfig(path: String): ProjectConfig{
        val config = ProjectConfig()
        //工程信息读取
        try {
            //读取json
            logger.debug("Reading project from file \"$path\"")
            val reader = FileReader(path)
            val qwq = File(path)
            config.root = Path.of(path).toAbsolutePath().parent
            config.name = qwq.name.substring(0, qwq.name.lastIndexOf('.'))
            val json = reader.readText()

            //解析json
            val jsonObject: JSONObject = JSONObject.parse(json) as JSONObject

            //源代码根目录
            config.sourcePath = Path.of(jsonObject.getString("sourcePath")?: ".")

            //版本
            config.version = jsonObject.getString("version")?:"1.21"

            //描述
            config.description = jsonObject.getString("description")?:"A datapack compiled by MCFPP"

            //默认命名空间
            config.rootNamespace = jsonObject.getString("namespace")?: "default"

            //调用库
            val includesJson: JSONArray = jsonObject.getJSONArray("includes")?: JSONArray()
            for (i in 0 until includesJson.size) {
                config.includes.add(includesJson.getString(i))
            }

            //输出目录
            config.targetPath = Path(jsonObject.getString("targetPath")?: "lib/")

            //是否生成数据包
            config.noDatapack = jsonObject.getBooleanValue("noDatapack")


        } catch (e: Exception) {
            LogProcessor.error("Error while reading project from file \"$path\"")
            e.printStackTrace()
        }

        return config
    }

    fun checkConfig(){
        //TODO
    }

    /**
     * 读取库文件，并将库写入缓存
     */
    fun readProject(){
        compileStage++
        //读取所有jar
        for (jar in config.jars){
            if(Paths.get(jar).notExists()){
                LogProcessor.error("Cannot find jar at: $jar")
                continue
            }
            val url = Paths.get(jar).toUri().toURL()
            classLoader = URLClassLoader(arrayOf(url), classLoader)
        }
        //默认的
        if(!CompileSettings.ignoreStdLib){
            val inputStream = ResourceReader::class.java.classLoader.getResourceAsStream("lib/.mclib")

            if (inputStream == null) {
                LogProcessor.error("Cannot find lib file at: lib/.mclib")
                return
            }

            // 读取文件内容
            val fileContent = String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
            // 输出文件内容
            LibReader.readFromString(fileContent)
        }
        //写入缓存
        for (include in config.includes) {
            val filePath = if(!include.endsWith(".jar")) include else "$include.jar"
            val file = File(filePath)
            if(file.exists()){
                try {
                    JarFile(filePath).use { jarFile ->
                        val jarEntry = jarFile.getJarEntry("lib/.mclib")
                        if (jarEntry != null) {
                            jarFile.getInputStream(jarEntry).use { inputStream ->
                                // 读取文件内容
                                val fileContent = String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                                // 输出文件内容
                                LibReader.readFromString(fileContent)
                            }
                        } else {
                            LogProcessor.error("Cannot find lib file at: ${file.absolutePath}")
                        }
                    }
                } catch (e: IOException) {
                    LogProcessor.error("Error while reading lib file at ${file.absolutePath}: $e")
                }
            }else{
                LogProcessor.error("Cannot find jar at: ${file.absolutePath}")
            }
        }
        //实例化所有类中的成员字段
        for(namespace in GlobalField.libNamespaces.values){
            namespace.field.forEachClass { c ->
                run {
                    for (v in c.field.allVars){
                        if(v is UnresolvedVar){
                            c.field.putVar(c.identifier, v.resolve(c), true)
                        }
                    }
                }
            }
        }
        //实例化所有类中的成员字段
        for(namespace in GlobalField.stdNamespaces.values){
            namespace.field.forEachClass { c ->
                run {
                    for (v in c.field.allVars){
                        if(v is UnresolvedVar){
                            c.field.putVar(c.identifier, v.resolve(c), true)
                        }
                    }
                }
            }
        }
        //函数参数解析
        GlobalField.importedLibNamespaces.clear()
        //读取所有文件
        MCFPPFile.findFiles(config.sourcePath!!.absolutePathString()).forEach {
            files.add(MCFPPFile(it.toFile()))
        }
        stageProcessor[compileStage].forEach { it() }
    }

    /**
     * 编制类型索引
     */
    fun indexType(){
        compileStage++
        logger.debug("Generate Type Index...")
        //解析文件
        for (file in files) {
            try {
                file.indexType()
            } catch (e: IOException) {
                logger.error("Error while generate type index in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }
        //解析所有泛型类的泛型参数类型
        stageProcessor[compileStage].forEach { it() }
    }

    /**
     * 编制函数索引，解析类/模板成员
     */
    fun resolveField() {
        compileStage++
        logger.debug("Generate Function Index...")
        //解析文件
        for (file in files) {
            try {
                file.resolveField()
            } catch (e: IOException) {
                logger.error("Error while generate function index in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }
        stageProcessor[compileStage].forEach { it() }
    }

    fun runAnnotation(){
        compileStage++
        logger.debug("Run Annotation...")
        //解析文件
        for (file in files) {
            try {
                file.runAnnotation()
            } catch (e: IOException) {
                logger.error("Error while run annotation in file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
            GlobalField.importedLibNamespaces.clear()
        }
        stageProcessor[compileStage].forEach { it() }
    }

    /**
     * 编译工程
     */
    fun compile() {
        compileStage++
        //工程文件编译
        //解析文件
        for (file in files) {
            LogProcessor.debug("Compiling mcfpp code in \"$file\"")
            try {
                file.compile()
            } catch (e: IOException) {
                logger.error("Error while compiling file \"$file\"")
                errorCount++
                e.printStackTrace()
            }
        }
        stageProcessor[compileStage].forEach { it() }
    }

    /**
     * 整理并优化工程
     */
    @InsertCommand
    fun optimization() {
        compileStage++
        logger.debug("Optimizing...")
        logger.debug("Adding scoreboards declare in mcfpp:load function")

        //向load函数中添加记分板初始化命令
        Function.currFunction = GlobalField.stdNamespaces["mcfpp"]!!.field.getFunction("load", ArrayList(), ArrayList())
        for (scoreboard in GlobalField.scoreboards.values){
            Function.addCommand("scoreboard objectives add ${scoreboard.name} ${scoreboard.criterion}")
        }
        //向load函数中添加库初始化命令
        Function.addCommand("execute unless score math mcfpp_init matches 1 run function math:_init")
        //向load中添加类初始化命令
        for (n in GlobalField.localNamespaces.values){
            n.field.forEachObject { c->
                run {
                    if(c is ObjectClass){
                        c.classPreInit.invoke(ArrayList(), null)
                    }
                }
            }
        }
        //向load中添加类的load函数
        for (n in GlobalField.localNamespaces.values){
            n.field.forEachClass { c -> mcfppLoad.runInFunction {
                val qwq = c.field.getFunction("load",ArrayList(), ArrayList())
                Function.addCommand("execute as @e[tag=${c.tag}] at @s run function ${qwq.namespaceID}")
            }  }
            n.field.forEachObject { o -> mcfppLoad.runInFunction {
                if(o !is ObjectClass) return@runInFunction
                val qwq = o.field.getFunction("load",ArrayList(), ArrayList())
                Function.addCommand("execute as ${o.uuid} at @s run function ${qwq.namespaceID}")
            } }
        }

        //向tick中添加类的tick函数
        for (n in GlobalField.localNamespaces.values){
            n.field.forEachClass { c -> mcfppTick.runInFunction {
                val qwq = c.field.getFunction("tick",ArrayList(), ArrayList())
                Function.addCommand("execute as @e[tag=${c.tag}] at @s run function ${qwq.namespaceID}")
            }  }
            n.field.forEachObject { o -> mcfppTick.runInFunction {
                if(o !is ObjectClass) return@runInFunction
                val qwq = o.field.getFunction("tick",ArrayList(), ArrayList())
                Function.addCommand("execute as ${o.uuid} at @s run function ${qwq.namespaceID}")
            } }
        }

        //浮点数临时marker实体
        Function.addCommand("summon marker 0 0 0 {Tags:[\"mcfpp:float_marker\"],UUID:${MCFloat.tempFloatEntityUUIDNBT}}")


        //浮点数的
        //寻找入口函数
        var hasEntrance = false
        for(field in GlobalField.localNamespaces.values){
            field.field.forEachFunction { f->
                run {
                    if (f.parent.size == 0 && f !is Native) {
                        //找到了入口函数
                        hasEntrance = true
                        f.commands.add(0, "data modify storage mcfpp:system ${config.rootNamespace}.stack_frame prepend value {}")
                        f.commands.add("data remove storage mcfpp:system ${config.rootNamespace}.stack_frame[0]")
                        logger.debug("Find entrance function: {} {}", f.tags, f.identifier)
                    }
                }
            }
        }
        if (!hasEntrance && !CompileSettings.isLib) {
            logger.warn("No valid entrance function in Project ${config.rootNamespace}")
            warningCount++
        }
        logger.info("Complete compiling project " + config.root!!.name + " with [$errorCount] error and [$warningCount] warning")
        stageProcessor[compileStage].forEach { it() }
    }

    /**
     * 生成库索引
     * 在和工程信息json文件的同一个目录下生成一个.mclib文件
     */
    fun genIndex() {
        compileStage++
        LibWriter.write(config.targetPath!!.absolutePathString())
        stageProcessor[compileStage].forEach { it() }
    }
}

