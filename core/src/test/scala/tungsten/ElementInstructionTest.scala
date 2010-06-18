package tungsten

import org.junit.Test
import org.junit.Assert._
import Utilities._

class ElementInstructionTest {
  object dummy
    extends ExtendedInstruction
    with ElementInstruction
  {
    def ty = UnitType
    def operands = Nil
    def name = Symbol("dummy")
    def annotations = Nil
  }

  val module = ModuleIO.readText("is64bit: true\n" +
                                 "struct @A { field [2 x int64] %a }\n")
  val baseType = StructType("A")
  val pointerType = PointerType(baseType)

  def containsError[T <: CompileException](errors: List[CompileException])(implicit m: Manifest[T]) {
    assertTrue(errors.exists(m.erasure.isInstance _))
  }

  @Test
  def getElementType {
    val indices = List(IntValue(0, 64), IntValue(1, 64))
    val elementType = dummy.getElementType(module, baseType, indices)
    assertEquals(IntType(64), elementType)
  }

  @Test
  def getPointerType {
    val indices = List(IntValue(2, 64), IntValue(0, 64), IntValue(1, 64))
    val elementType = dummy.getPointerType(module, pointerType, indices)
    assertEquals(PointerType(IntType(64)), elementType)
  }

  @Test
  def validateIndicesCorrect {
    val indices = List(IntValue(0, 64), IntValue(1, 64))
    assertEquals(Nil, dummy.validateIndices(module, baseType, indices))
  }

  @Test
  def validateIndicesBadIndex {
    val indices = List(IntValue(0, 32))
    containsError[TypeMismatchException](dummy.validateIndices(module, baseType, indices))
  }

  @Test
  def validateIndicesOutOfRange {
    val indices = List(IntValue(1, 64))
    containsError[InvalidIndexException](dummy.validateIndices(module, baseType, indices))
  }

  @Test
  def validateIndicesNonConstant {
    val indices = List(DefinedValue("a", IntType(64)))
    containsError[InvalidIndexException](dummy.validateIndices(module, baseType, indices))
  }

  @Test
  def validateIndicesNonAggregate {
    val indices = List(IntValue(0, 64))
    val baseType = IntType(64)
    containsError[InvalidIndexException](dummy.validateIndices(module, baseType, indices))
  }

  @Test
  def validatePointerIndicesNonPointer {
    val indices = List(IntValue(0, 64))
    containsError[TypeMismatchException](dummy.validatePointerIndices(module, baseType, indices))
  }

  @Test
  def validatePointerIndicesNil {
    containsError[MissingElementIndexException](dummy.validatePointerIndices(module, pointerType, Nil))
  }
}