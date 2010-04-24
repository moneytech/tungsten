package tungsten.llvm

import tungsten.Symbol
import tungsten.DataFlow
import tungsten.Graph

class LlvmToTungstenConverter(val module: Module) {
  var cDefinitions = Map[Symbol, tungsten.Definition]()
  val symbolFactory = new tungsten.SymbolFactory
  var parent: String = ""
  var localNameMapping = Map[String, Symbol]()

  def convert: tungsten.Module = {
    module.definitions.foreach { (defn) =>
      val definition = defn._2
      definition match {
        case function: Function => convertFunction(function)
        case _ => throw new UnsupportedOperationException
      }
    }
    new tungsten.Module(cDefinitions)
  }

  def convertFunction(function: Function): tungsten.Function =
  {
    assert(parent.isEmpty)
    assert(localNameMapping.isEmpty)

    val cName = convertName(function.name)
    parent = function.name.tail

    val blockParameterData = BlockParameterAnalysis(function)

    val cReturnType = convertType(function.returnType)
    val cParameters = function.parameters.map(convertParameter _)
    val cBlocks = function.blocks.map { block => 
      convertBlock(block, blockParameterData(block.name))
    }
    val cFunction = tungsten.Function(cName, 
                                      cParameters.map(_.name),
                                      cReturnType,
                                      cBlocks.map(_.name))
    cDefinitions += cFunction.name -> cFunction
    parent = ""
    localNameMapping = Map()
    
    cFunction
  }

  def convertParameter(parameter: Parameter): tungsten.Parameter = {
    val cName = convertName(parameter.name)
    val cType = convertType(parameter.ty)
    val cParam = tungsten.Parameter(cName, cType)
    cDefinitions += cParam.name -> cParam
    cParam
  }

  def convertBlock(block: Block, data: BlockParameterData): tungsten.Block = {
    val cName = convertName(block.name)
    val cParameters = for ((name, ty) <- data.parameters) yield {
      val cName = convertName(name)
      val cTy = convertType(ty)
      val cParam = tungsten.Parameter(cName, cTy)
      cDefinitions += cName -> cParam
      cParam
    }
    val cInsts = block.instructions.filterNot(_.isInstanceOf[PhiInstruction]).map { inst =>
      convertInstruction(inst, data)
    }
    val cBlock = tungsten.Block(cName, cParameters.map(_.name), cInsts.map(_.name))
    cDefinitions += cName -> cBlock
    cBlock
  }

  def convertInstruction(instruction: Instruction, 
                         data: BlockParameterData): tungsten.Instruction = 
  {
    def convertLabel(label: Value): (Symbol, List[tungsten.Value]) = {
      label match {
        case DefinedValue(labelName, LabelType) => {
          val cBlockName = convertName(labelName)
          val cArguments = data.arguments(labelName).map(convertValue _)
          (cBlockName, cArguments)
        }
        case _ => throw new LlvmConversionException("value " + label + " must be a label")
      }
    }

    val name = instruction.name
    val cName = if (name.isEmpty)
      anonymousName
    else
      convertName(name)

    val cInst = instruction match {
      case AllocaInstruction(_, elementType) => {
        val ty = tungsten.PointerType(convertType(elementType))
        tungsten.StackAllocateInstruction(cName, ty)
      }

      case BitcastInstruction(_, value, ty) => {
        (value.ty, ty) match {
          case (IntType(n), IntType(m)) if n == m => {
            tungsten.AssignInstruction(cName, convertValue(value))
          }
          case (PointerType(fromEty), PointerType(toEty)) => {
            // TODO
            throw new UnsupportedOperationException
          }
          case _ => throw new LlvmConversionException("could not bitcast from type " + value.ty + " to type " + ty)
        }
      }

      case BranchInstruction(label) => {
        val (cBlockName, cArguments) = convertLabel(label)
        tungsten.BranchInstruction(cName, cBlockName, cArguments)
      }
            
      case LoadInstruction(_, address, _) => {
        tungsten.LoadInstruction(cName, convertValue(address))
      }

      case _: PhiInstruction => throw new UnsupportedOperationException

      case ReturnInstruction(value) => {
        tungsten.ReturnInstruction(cName, convertValue(value))
      }

      case StoreInstruction(value, address, _) => {
        tungsten.StoreInstruction(cName, convertValue(address), convertValue(value))
      }
    }
    cDefinitions += cName -> cInst
    cInst
  }

  def convertType(ty: Type): tungsten.Type = {
    ty match {
      case VoidType => tungsten.UnitType()
      case IntType(n) => {
        if (n == 1)
          tungsten.BooleanType()
        else if (n <= 8)
          tungsten.IntType(8)
        else if (n <= 16)
          tungsten.IntType(16)
        else if (n <= 32)
          tungsten.IntType(32)
        else if (n <= 64)
          tungsten.IntType(64)
        else
          throw new UnsupportedOperationException
      }
      case PointerType(ety) => tungsten.PointerType(convertType(ety))
      case LabelType => throw new UnsupportedOperationException
    }
  }

  def convertValue(value: Value): tungsten.Value = {
    value match {
      case VoidValue => tungsten.UnitValue()
      case IntValue(n, width) => {
        if (width == 1)
          tungsten.BooleanValue(if (n == 0L) false else true)
        else if (width <= 8)
          tungsten.Int8Value(n.toByte)
        else if (width <= 16)
          tungsten.Int16Value(n.toShort)
        else if (width <= 32)
          tungsten.Int32Value(n.toInt)
        else if (width <= 64)
          tungsten.Int64Value(n)
        else
          throw new UnsupportedOperationException
      }
      case DefinedValue(name, _) => tungsten.DefinedValue(convertName(name))
    }
  }

  def convertName(name: String): Symbol = {
    val (prefix, unprefixed) = (name.head, name.tail)
    if (prefix == '@')
      new Symbol(unprefixed)
    else {
      assert(!parent.isEmpty)
      if (localNameMapping.contains(unprefixed))
        localNameMapping(unprefixed)
      else {
        val fullName = List(parent, unprefixed)
        val symbol = symbolFactory.complexSymbol(fullName)
        localNameMapping += (unprefixed -> symbol)
        symbol
      }
    }
  }

  def anonymousName: Symbol = {
    assert(!parent.isEmpty)
    val fullName = List(parent, "anon$")
    symbolFactory.complexSymbol(fullName)
  }
}

class LlvmConversionException(msg: String) extends Exception(msg)

object LlvmToTungstenConverter {
  def apply(module: Module): tungsten.Module = {
    new LlvmToTungstenConverter(module).convert
  }
}