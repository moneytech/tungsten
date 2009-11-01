package tungsten

import Utilities._

sealed abstract class AstDefinition(val location: Location) {
  def compileDeclaration(ctx: AstContext): Unit
  def compile(ctx: AstContext): Definition
}

// Types
sealed abstract class AstType(val location: Location) {
  def compile(ctx: AstContext): Type
  final def compileOrElse(ctx: AstContext, default: Type = UnitType(location)) = {
    try {
      compile(ctx)
    } catch {
      case exn: CompileException => {
        ctx.errors += exn
        default
      }
    }
  }
}

final case class AstUnitType(override val location: Location) extends AstType(location) {
  def compile(ctx: AstContext) = UnitType(location)
}

final case class AstBooleanType(override val location: Location) extends AstType(location) {
  def compile(ctx: AstContext) = BooleanType(location)
}

final case class AstIntType(val width: Int, override val location: Location)
  extends AstType(location)
{
  if (width < 8 || width > 64 || !isPowerOf2(width))
    throw new IllegalArgumentException
  def compile(ctx: AstContext) = IntType(width, location)
}

final case class AstClassType(val name: Symbol,
                              val typeArguments: List[AstType],
                              override val location: Location)
  extends AstType(location)
{
  def compile(ctx: AstContext) = {
    ctx.module.getDefn(name) match {
      case Some(c: Class) => ClassType(c.name, typeArguments.map(_.compile(ctx)), location)
      case Some(i: Interface) => {
        InterfaceType(i.name, typeArguments.map(_.compile(ctx)), location)
      }
      case Some(other) => {
        throw InappropriateSymbolException(name, location, other.location, "type")
      }
      case None => throw UndefinedSymbolException(name, location)
    }
  }
}

// Values

sealed abstract class AstValue(val location: Location) {
  def compile(ctx: AstContext): Value
  final def compileOrElse(ctx: AstContext, default: Value = UnitValue(location)) = {
    try {
      compile(ctx)
    } catch {
      case exn: CompileException => {
        ctx.errors += exn
        default
      }
    }
  }
}

final case class AstUnitValue(override val location: Location) extends AstValue(location) {
  def compile(ctx: AstContext) = UnitValue(location)
}

final case class AstBooleanValue(value: Boolean, override location: Location)
  extends AstValue(location)
{
  def compile(ctx: AstContext) = BooleanValue(value, location)
}

final case class AstInt8Value(value: Byte, override location: Location)
  extends AstValue(location)
{
  def compile(ctx: AstContext) = Int8Value(value, location)
}

final case class AstInt16Value(value: Short, override location: Location)
  extends AstValue(location)
{
  def compile(ctx: AstContext) = Int16Value(value, location)
}

final case class AstInt32Value(value: Int, override location: Location)
  extends AstValue(location)
{
  def compile(ctx: AstContext) = Int32Value(value, location)
}

final case class AstInt64Value(value: Long, override location: Location)
  extends AstValue(location)
{
  def compile(ctx: AstContext) = Int64Value(value, location)
}

final case class AstSymbolValue(value: Symbol, override location: Location)
  extends AstValue(location)
{
  def compile(ctx: AstContext) = {
    ctx.resolve(value) match {
      case Some(name) => DefinedValue(name, location)
      case None => DefinedValue(value, location)
    }
  }
}

// Instructions

sealed abstract class AstInstruction(val name: Symbol, override val location: Location) 
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = {
    val fullName: Symbol = ctx.names.top + name
    val cInst = AssignInstruction(fullName, UnitValue(), location)
    ctx.module.add(cInst)
  }

  def compile(ctx: AstContext): Instruction
}

final case class AstAssignInstruction(override name: Symbol,
                                      target: AstValue,
                                      override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cTarget = target.compile(ctx)
    val cInst = AssignInstruction(fullName, cTarget, location)
    ctx.module.update(cInst)
    cInst
  }
}

final case class AstBinaryOperatorInstruction(override name: Symbol,
                                              operator: BinaryOperator,
                                              left: AstValue,
                                              right: AstValue,
                                              override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cLeft = left.compile(ctx)
    val cRight = right.compile(ctx)
    val cBinop = BinaryOperatorInstruction(fullName, operator, cLeft, cRight, location)
    ctx.module.update(cBinop)
    cBinop
  }
}

final case class AstBranchInstruction(override name: Symbol,
                                      target: Symbol,
                                      arguments: List[AstValue],
                                      override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cTarget = ctx.resolve(target) match {
      case Some(t) => t
      case None => target
    }
    val cArgs = arguments.map(_.compileOrElse(ctx))
    val cInst = BranchInstruction(fullName, cTarget, cArgs, location)
    ctx.module.update(cInst)
    cInst
  }
}

final case class AstConditionalBranchInstruction(override name: Symbol,
                                                 condition: AstValue,
                                                 trueTarget: Symbol,
                                                 falseTarget: Symbol,
                                                 arguments: List[AstValue],
                                                 override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    def resolveTarget(target: Symbol) = {
      ctx.resolve(target) match {
        case Some(resolved) => resolved
        case None => target
      }
    }

    val fullName = ctx.names.top + name
    val cCond = condition.compile(ctx)
    val cTrueTarget = resolveTarget(trueTarget)
    val cFalseTarget = resolveTarget(falseTarget)
    val cArgs = arguments.map(_.compile(ctx))
    val cInst = ConditionalBranchInstruction(fullName, cCond, cTrueTarget, cFalseTarget, 
                                             cArgs, location)
    ctx.module.update(cInst)
    cInst
  }
}

final case class AstGlobalLoadInstruction(override name: Symbol,
                                          globalName: Symbol,
                                          override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cLoad = GlobalLoadInstruction(fullName, globalName, location)
    ctx.module.update(cLoad)
    cLoad
  }
}

final case class AstGlobalStoreInstruction(override name: Symbol,
                                           globalName: Symbol,
                                           value: AstValue,
                                           override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cStore = GlobalStoreInstruction(fullName, 
                                        globalName, 
                                        value.compileOrElse(ctx), 
                                        location)
    ctx.module.update(cStore)
    cStore
  }
}

final case class AstIndirectCallInstruction(override name: Symbol,
                                            target: AstValue,
                                            arguments: List[AstValue],
                                            override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cTarget = target.compileOrElse(ctx)
    val cArgs = arguments.map(_.compile(ctx))
    val cCall = IndirectCallInstruction(fullName, cTarget, cArgs, location)
    ctx.module.update(cCall)
    cCall
  }
}

final case class AstIntrinsicCallInstruction(override name: Symbol,
                                             target: Symbol,
                                             arguments: List[AstValue],
                                             override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    import Intrinsic._
    val fullName = ctx.names.top + name
    val cIntrinsic = target.toString match {
      case "exit" => EXIT
    }
    val cArgs = arguments.map(_.compile(ctx))
    val cCall = IntrinsicCallInstruction(fullName, cIntrinsic, cArgs, location)
    ctx.module.update(cCall)
    cCall
  }
}

final case class AstRelationalOperatorInstruction(override name: Symbol,
                                                  operator: RelationalOperator,
                                                  left: AstValue,
                                                  right: AstValue,
                                                  override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cLeft = left.compile(ctx)
    val cRight = right.compile(ctx)
    val cRelop = RelationalOperatorInstruction(fullName, operator, cLeft, cRight, location)
    ctx.module.update(cRelop)
    cRelop
  }
}

final case class AstReturnInstruction(override name: Symbol,
                                      value: AstValue,
                                      override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cValue = value.compileOrElse(ctx)
    val cReturn = ReturnInstruction(fullName, cValue, location)
    ctx.module.update(cReturn)
    cReturn
  }
}

final case class AstStaticCallInstruction(override name: Symbol,
                                          target: Symbol,
                                          arguments: List[AstValue],
                                          override location: Location)
  extends AstInstruction(name, location)
{
  def compile(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cArgs = arguments.map(_.compile(ctx))
    val cCall = StaticCallInstruction(fullName, target, cArgs, location)
    ctx.module.update(cCall)
    cCall
  }
}

// Function and parameters

final case class AstParameter(name: Symbol, ty: AstType, override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cParam = Parameter(fullName, UnitType(), location)
    ctx.module.add(cParam)
  }

  def compile(ctx: AstContext): Parameter = {
    val cty = ty.compileOrElse(ctx)
    val fullName = ctx.names.top + name
    val cParam = Parameter(fullName, cty, location)
    ctx.module.update(cParam)
    cParam
  }
}     

final case class AstTypeParameter(name: Symbol, 
                                  upperBound: Option[AstType], 
                                  lowerBound: Option[AstType],  
                                  override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    val cTyParam = TypeParameter(fullName, None, None, location)
    ctx.module.add(cTyParam)
  }

  def compile(ctx: AstContext): TypeParameter = {
    val fullName = ctx.names.top + name
    val cUpperBound = upperBound.map(_.compileOrElse(ctx))
    val cLowerBound = lowerBound.map(_.compileOrElse(ctx))
    val cTyParam = TypeParameter(fullName, cUpperBound, cLowerBound, location)
    ctx.module.update(cTyParam)
    cTyParam
  }
}

final case class AstBlock(name: Symbol,
                          parameters: List[AstParameter],
                          instructions: List[AstInstruction],
                          override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = {
    val fullName = ctx.names.top + name
    ctx.names.push(fullName)
    parameters.foreach(_.compileDeclaration(ctx))
    instructions.foreach(_.compileDeclaration(ctx))
    val cBlock = Block(fullName, Nil, Nil, location)
    ctx.names.pop
    ctx.module.add(cBlock)
  }

  def compile(ctx: AstContext): Block = {
    val fullName = ctx.names.top + name
    ctx.names.push(fullName)
    val cParams = parameters.map(_.compile(ctx).name)
    val cInsts = instructions.map(_.compile(ctx).name)
    val cBlock = Block(fullName, cParams, cInsts, location)
    ctx.module.update(cBlock)
    ctx.names.pop
    cBlock
  }
}

final case class AstFunction(name: Symbol,
                             returnType: AstType,
                             typeParameters: List[AstTypeParameter],
                             parameters: List[AstParameter],
                             blocks: List[AstBlock],
                             override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = {
    ctx.names.push(name)
    typeParameters.foreach(_.compileDeclaration(ctx))
    parameters.foreach(_.compileDeclaration(ctx))
    blocks.foreach(_.compileDeclaration(ctx))
    val cFunction = Function(name, Nil, Nil, UnitType(), Nil, location)
    ctx.names.pop
    ctx.module.add(cFunction)
  }

  def compile(ctx: AstContext) = {
    ctx.names.push(name)
    val cRetTy = returnType.compileOrElse(ctx)
    val cTyParams = typeParameters.map(_.compile(ctx).name)
    val cParams = parameters.map(_.compile(ctx).name)
    var cBlocks = blocks.map(_.compile(ctx).name)
    cBlocks match {
      case entryName :: _ => {
        val entry = ctx.module.get[Block](entryName).get
        if (entry.parameters.isEmpty) {
          val newEntry = Block(entry.name, cParams, entry.instructions, entry.location) 
          ctx.module.update(newEntry)
        }
      }
      case _ => ()
    }
    val cFunction = Function(name, cTyParams, cParams, cRetTy, cBlocks, location)
    ctx.module.update(cFunction)
    ctx.names.pop
    cFunction
  }
}

// Global

final case class AstGlobal(name: Symbol, 
                           ty: AstType, 
                           value: Option[AstValue], 
                           override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = {
    val cGlobal = Global(name, UnitType(), None, location)
    ctx.module.add(cGlobal)
  }

  def compile(ctx: AstContext) = {
    val global = Global(name, ty.compile(ctx), value.map(_.compile(ctx)), location)
    ctx.module.update(global)
    global
  }
}

// Data structures

final case class AstField(name: Symbol,
                          ty: AstType,
                          override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = throw new UnsupportedOperationException

  def compile(ctx: AstContext) = throw new UnsupportedOperationException
}

final case class AstStruct(name: Symbol,
                           typeParameters: List[AstTypeParameter],
                           fields: List[AstField],
                           override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = throw new UnsupportedOperationException

  def compile(ctx: AstContext) = throw new UnsupportedOperationException
}

final case class AstClass(name: Symbol,
                          typeParameters: List[AstTypeParameter],
                          superclass: Option[AstType],
                          interfaces: List[AstType],
                          fields: List[AstField],
                          methods: List[AstFunction],
                          override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = throw new UnsupportedOperationException

  def compile(ctx: AstContext) = throw new UnsupportedOperationException
}

final case class AstInterface(name: Symbol,
                              typeParameters: List[AstTypeParameter],
                              superclass: Option[AstType],
                              interfaces: List[AstType],
                              methods: List[AstFunction],
                              override location: Location)
  extends AstDefinition(location)
{
  def compileDeclaration(ctx: AstContext) = throw new UnsupportedOperationException

  def compile(ctx: AstContext) = throw new UnsupportedOperationException
}

// Module

final case class AstModule(definitions: List[AstDefinition]) {
  def compile: Either[Module, List[CompileException]] = {
    val ctx = new AstContext
    definitions.foreach(_.compileDeclaration(ctx))
    definitions.foreach(_.compile(ctx))
    ctx.errors.toList match {
      case Nil => Left(ctx.module)
      case errors => Right(errors)
    }
  }
}
