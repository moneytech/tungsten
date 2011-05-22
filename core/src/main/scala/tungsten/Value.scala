package tungsten

import Utilities._

sealed abstract class Value 
  extends Copying[Value]
{
  def ty: Type

  def validateComponents(module: Module, location: Location): List[CompileException] = Nil

  def validate(module: Module, location: Location): List[CompileException] = Nil
}

abstract class ExtendedValue extends Value

sealed abstract class ConstantExpressionValue
  extends Value

final case object UnitValue
  extends Value
{
  def ty = UnitType
}

final case class BooleanValue(value: Boolean)
  extends Value
{
  def ty = BooleanType
}

final case class CharValue(value: Char)
  extends Value
{
  def ty = CharType

  def isPrintable: Boolean = Utilities.charIsPrintable(value)
}

final case class StringValue(value: String)
  extends Value
{
  def ty = StringType
}      

final case class IntValue(value: Long, width: Int)
  extends Value
{
  if (width < 8 || !isPowerOf2(width) || width > 64)
    throw new IllegalArgumentException

  def ty = IntType(width)

  override def validate(module: Module, location: Location): List[CompileException] =
  {
    if (value < ty.minValue || value > ty.maxValue)
      List(IntegerRangeException(value, width, location))
    else
      Nil
  }
}

object IntValue {
  def word(value: Long, module: Module): IntValue = {
    if (module.is64Bit)
      IntValue(value, 64)
    else
      IntValue(value, 32)
  }
}

final case class FloatValue(value: Double, width: Int) 
  extends Value
{
  if (width != 32 && width != 64)
    throw new IllegalArgumentException

  def ty = FloatType(width)
}

final case object NullValue
  extends Value
{
  def ty = NullType
}

final case class ArrayValue(elementType: Type,
                            elements: List[Value])
  extends Value
{
  def ty = ArrayType(elements.size, elementType)

  override def validate(module: Module, location: Location) = {
    elements.flatMap { e: Value => checkType(e.ty, elementType, location) }
  }
}      

final case class StructValue(structName: Symbol,
                             fields: List[Value])
  extends Value
{
  def ty = StructType(structName)

  override def validateComponents(module: Module, location: Location): List[CompileException] = {
    module.validateName[Struct](structName, location)
  }

  override def validate(module: Module, location: Location): List[CompileException] = {
    def validateFieldCount = {
      val struct = module.getStruct(structName)
      if (fields.size == struct.fields.size)
        Nil
      else
        List(FieldCountException(structName, fields.size, struct.fields.size, location))
    }

    def validateFieldTypes = {
      val struct = module.getStruct(structName)
      val fieldTypes = module.getFields(struct.fields).map(_.ty)
      (fields zip fieldTypes) flatMap { ft =>
        val (f, t) = ft
        checkType(f.ty, t, location)
      }
    }

    fields.flatMap(_.validate(module, location)) ++
      stage(validateFieldCount,
            validateFieldTypes)
  }
}

final case class DefinedValue(value: Symbol, ty: Type)
  extends Value
{
  override def validateComponents(module: Module, location: Location) = {
    module.getDefn(value) match {
      case Some(_: Function)  | 
           Some(_: Global)    | 
           Some(_: Parameter) | 
           Some(_: Instruction) => Nil
      case Some(defn) => {
        List(InappropriateSymbolException(value, 
                                          defn.getLocation,
                                          "function, global, parameter, or instruction"))
      }
      case None => List(UndefinedSymbolException(value, location))
    }
  }
}

final case class BitCastValue(value: Value, ty: Type)
  extends ConstantExpressionValue
{
  override def validate(module: Module, location: Location) = {
    val valueSize = value.ty.size(module)
    val tySize = ty.size(module)
    if (valueSize != tySize)
      List(InvalidBitCastException(value, valueSize, ty, tySize, location))
    else
      Nil
  }
}
