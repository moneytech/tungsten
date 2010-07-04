package tungsten.llvm

import org.junit.Test
import org.junit.Assert._
import tungsten.{ModuleIO, Symbol}
import tungsten.Utilities._

class LlvmCompatibilityPassTest {
  val pass = new LlvmCompatibilityPass
  val codeTemplate = "is64bit: true\n" +
                     "function unit @main {\n" +
                     "  block %%entry {\n" +
                     "    %s\n" +
                     "    unit %%r = return ()\n" +
                     "  }\n" +
                     "}\n"

  def convert(program: String): tungsten.Module = {
    val module = ModuleIO.readText(program, "<TEST>")
    LlvmCompatibilityPass(module)
  }

  def testCode(expected: String, code: String) {
    val module = ModuleIO.readText(codeTemplate.format(code))
    val block = module.getBlock("main.entry")
    val instructions = module.getInstructions(block.instructions)
    assertEquals(2, instructions.size)
    val instruction = instructions.head
    val converted = pass.convertInstruction(instruction, module)

    val expectedModule = ModuleIO.readText(codeTemplate.format(expected))
    val expectedBlock = expectedModule.getBlock("main.entry")
    val expectedInstructions = expectedModule.getInstructions(expectedBlock.instructions)

    assertEquals(expectedInstructions.take(expectedInstructions.size - 1), converted)
  }    

  def testProgram(expected: String, program: String) {
    val expectedModule = pass.addRuntime(ModuleIO.readText(expected))
    val module = pass(ModuleIO.readText(program))
    assertEquals(expectedModule, module)
  }

  @Test
  def runtimePresent {
    val module = new tungsten.Module
    val processed = pass.addRuntime(module)
    val expectedProgram = "annotation @tungsten.NoReturn\n" +
                          "struct @tungsten.string {\n" +
                          "  field int16* %characters\n" +
                          "  field int64 %length\n" +
                          "}\n" +
                          "function int8* @tungsten.malloc(int32 %size)\n" +
                          "@tungsten.NoReturn function unit @tungsten.exit(int32 %code)\n"
    val expected = ModuleIO.readText(expectedProgram)
    assertEquals(expected.definitions, processed.definitions)
  }

  @Test
  def mainType {
    val program = "function unit @main {\n" +
                  "  block %entry {\n" +
                  "    unit %r = return ()\n" +
                  "  }\n" +
                  "}\n"
    val module = ModuleIO.readText(program)
    val converted = pass.processMain(module)
    val mainType = converted.getFunction("main").returnType
    val retType = converted.get[tungsten.ReturnInstruction]("main.entry.r").get.value.ty
    assertEquals(tungsten.IntType(32), mainType)
    assertEquals(tungsten.IntType(32), retType)
  }

  @Test
  def convertIndexTo32BitConstant {
    val sibling = Symbol("foo")
    assertEquals((tungsten.IntValue(0, 32), None),
                 pass.convertWordTo32Bit(tungsten.IntValue(0, 64), sibling))
  }

  @Test
  def convertIndexTo32BitVar {
    val sibling = Symbol("foo")
    val word = tungsten.DefinedValue("i", tungsten.IntType(64))
    val expected = tungsten.DefinedValue("llvmCompat#1", tungsten.IntType(32))
    val cast = tungsten.IntegerTruncateInstruction(expected.value, expected.ty, word)
    assertEquals((expected, Some(cast)), pass.convertWordTo32Bit(word, sibling))
  }

  @Test
  def addressInst {
    val code = "int64* %a = address int64* %base, int64 %b"
    val expected = "int32 %llvmCompat#1 = itruncate int64 %b\n" +
                   "int64* %a = address int64* %base, int32 %llvmCompat#1"
    testCode(expected, code)
  }

  @Test
  def extractInst {
    val code = "unit %a = extract [2 x unit] {(), ()}, int64 1"
    val expected = "unit %a = extract [2 x unit] {(), ()}, int32 1"
    testCode(expected, code)
  }

  @Test
  def heapInst {
    val code = "int64* %a = heap"
    val expected = "int8* %llvmCompat#1 = scall @tungsten.malloc(int32 8)\n" +
                   "int64* %a = bitcast int8* %llvmCompat#1"
    testCode(expected, code)
  }

  @Test
  def heapArrayInst {
    val code = "int64* %a = heaparray int64 2"
    val expected = "int64 %llvmCompat#1 = binop int64 2 * int64 8\n" +
                   "int8* %llvmCompat#2 = scall @tungsten.malloc(int64 %llvmCompat#1)\n" +
                   "int64* %a = bitcast int8* %llvmCompat#2"
    testCode(expected, code)
  }

  @Test
  def insertInst {
    val code = "[2 x unit] %a = insert (), [2 x unit] {(), ()}, int64 1"
    val expected = "[2 x unit] %a = insert (), [2 x unit] {(), ()}, int32 1"
    testCode(expected, code)
  }

  @Test
  def intrinsicExitInst {
    val code = "unit %x = intrinsic exit(int32 1)"
    val expected = "unit %x = scall @tungsten.exit(int32 1)"
    testCode(expected, code)
  }

  @Test
  def loadElementInst {
    val code = "unit %b = loadelement [1 x unit]* %a, int64 0, int64 0"
    val expected = "unit* %llvmCompat#1 = address [1 x unit]* %a, int32 0, int32 0\n" +
                   "unit %b = load unit* %llvmCompat#1"
    testCode(expected, code)
  }

  @Test
  def stackArrayInst {
    val code = "int64* %a = stackarray int64 2"
    val expected = "int64* %a = stackarray int32 2"
    testCode(expected, code)
  }

  @Test
  def storeElementInst {
    val code = "unit %x = storeelement (), [1 x unit]* %a, int64 0, int64 0"
    val expected = "unit* %llvmCompat#1 = address [1 x unit]* %a, int32 0, int32 0\n" +
                   "unit %x = store (), unit* %llvmCompat#1"
    testCode(expected, code)
  }

  @Test
  def collectStrings {
    val program = "global string @s = \"s\""
    val module = ModuleIO.readText(program)
    val strings = module.foldValues(Set[String](), pass.collectStrings _)
    assertEquals(Set("s"), strings)
  }

  @Test
  def convertString {
    val string = "s"
    val expected = tungsten.Global("llvmCompat#1",
                                   tungsten.ArrayType(1, tungsten.IntType(16)),
                                   Some(tungsten.ArrayValue(tungsten.IntType(16),
                                                            List(tungsten.IntValue(115, 16)))))
    assertEquals(expected, pass.convertString(string))
  }                                   

  @Test
  def convertStringValue {
    val value = tungsten.StringValue("s")
    val stringName = symbolFromString("llvmCompat#1")
    val stringMap = Map("s" -> stringName)
    val charPtr = WBitCastValue(tungsten.DefinedValue(stringName,
                                                      tungsten.PointerType(tungsten.ArrayType(1, tungsten.IntType(16)))),
                                tungsten.PointerType(tungsten.IntType(16)))
    val expected = tungsten.StructValue("tungsten.string",
                                        List(charPtr, tungsten.IntValue(1, 64)))
    assertEquals(expected, pass.convertStringValue(value, stringMap))
  }

  @Test
  def processStrings {
    val global = tungsten.Global("g", tungsten.StringType, Some(tungsten.StringValue("s")))
    val definitions = Map(global.name -> global)
    val module = new tungsten.Module(definitions=definitions)

    val charType = tungsten.IntType(16)
    val arrayType = tungsten.ArrayType(1, charType)
    val arrayValue = tungsten.ArrayValue(tungsten.IntType(16), List(tungsten.IntValue(115, 16)))
    val stringType = tungsten.StructType("tungsten.string")
    val charsValue = WBitCastValue(tungsten.DefinedValue("llvmCompat#1",
                                                         tungsten.PointerType(arrayType)),
                                   tungsten.PointerType(charType))
    val stringValue = tungsten.StructValue("tungsten.string",
                                           List(charsValue, tungsten.IntValue(1, 64)))
    val storageGlobal = tungsten.Global("llvmCompat#1", arrayType, Some(arrayValue))
    val stringGlobal = tungsten.Global(global.name, stringType, Some(stringValue))
    val expectedDefinitions = Map(storageGlobal.name -> storageGlobal,
                                  stringGlobal.name -> stringGlobal)
    val expectedModule = new tungsten.Module(definitions=expectedDefinitions)
    assertEquals(expectedModule, pass.processStrings(module))
  }

  @Test
  def convertCharValue {
    val value = tungsten.CharValue('A')
    val expected = tungsten.IntValue(65, 16)
    assertEquals(expected, pass.convertCharValue(value))
  }

  @Test
  def convertCharType {
    assertEquals(tungsten.IntType(16), pass.convertCharType(tungsten.CharType))
  }

  @Test
  def processChars {
    val program = "global char @g = 'A'"
    val expected = "global int16 @g = int16 65"
    testProgram(expected, program)
  }
}
