package tungsten.llvm

import tungsten.Symbol
import tungsten.Utilities._

class TungstenToLlvmConverter(module: tungsten.Module) {
  def convert: Module = {
    throw new UnsupportedOperationException
  }

  def convertType(ty: tungsten.Type): Type = {
    ty match {
      case tungsten.UnitType => VoidType
      case tungsten.BooleanType => IntType(1)
      case tungsten.IntType(width) => IntType(width)
      case tungsten.FloatType(32) => FloatType
      case tungsten.FloatType(64) => DoubleType
      case tungsten.PointerType(tungsten.UnitType) => PointerType(IntType(8))
      case tungsten.PointerType(ety) => PointerType(convertType(ety))
      case tungsten.NullType => PointerType(IntType(8))
      case tungsten.ArrayType(None, ety) => ArrayType(0L, convertType(ety))
      case tungsten.ArrayType(Some(size), ety) => ArrayType(size, convertType(ety))
      case tungsten.StructType(structName) => {
        val globalName = globalSymbol(structName)
        val localName = "%" + globalName.tail
        StructType(localName)
      }
      case _ => throw new UnsupportedOperationException
    }
  }

  def globalSymbol(symbol: Symbol): String = {
    "@" + convertSymbol(symbol)
  }

  def localSymbol(symbol: Symbol, parent: Symbol): String = {
    def findChildName(name: List[String], parentName: List[String]): List[String] = {
      (name, parentName) match {
        case (nameHd :: nameTl, parentHd :: parentTl) if nameHd == parentHd => {
          findChildName(nameTl, parentTl)
        }
        case (Nil, _) => symbol.name
        case _ => name
      }
    }

    val childSymbol = new Symbol(findChildName(symbol.name, parent.name), symbol.id)
    "%" + convertSymbol(childSymbol)
  }    

  def convertSymbol(symbol: Symbol): String = {
    def convertNamePart(part: String): String = {
      val buffer = new StringBuffer
      for (c <- part) {
        if (c.toInt < 0x80 && !Character.isISOControl(c) && c != '"' && c != '\\')
          buffer.append(c)
        else if (c.toInt < 0xFF)
          buffer.append("\\%02x".format(c.toInt))
        else
          buffer.append("\\%02x\\%02x".format(c.toInt >>> 8, c.toInt & 0xFF))
      }
      buffer.toString
    }
    
    val idStr = if (symbol.id != 0) "." + symbol.id else ""
    val nameStr = symbol.name.map(convertNamePart _).mkString(".")
    val symbolStr = nameStr + idStr
    val identRegex = "[a-zA-Z$._][a-zA-Z$._0-9]*".r
    identRegex.findFirstIn(symbolStr) match {
      case Some(m) if m == symbolStr => symbolStr
      case _ => "\"" + symbolStr + "\""
    }
  }    
}

object TungstenToLlvmConverter {
  def apply(module: tungsten.Module): Module = {
    new TungstenToLlvmConverter(module).convert
  }
}
