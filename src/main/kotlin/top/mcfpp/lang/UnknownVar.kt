package top.mcfpp.lang

import net.querz.nbt.tag.StringTag
import top.mcfpp.lang.type.MCFPPType
import top.mcfpp.model.function.Function
import top.mcfpp.model.Member
import top.mcfpp.model.function.UnknownFunction

class UnknownVar : Var<UnknownVar> {

    constructor(identifier: String):super(identifier)

    override fun onAssign(b: Var<*>) : UnknownVar {
        hasAssigned = true
        return this
    }

    override fun explicitCast(type: MCFPPType): Var<*> = build(identifier, type, Function.currFunction)

    override fun implicitCast(type: MCFPPType): Var<*> = build(identifier, type, Function.currFunction)

    override fun clone(): UnknownVar = this

    override fun getTempVar(): UnknownVar = this

    override fun storeToStack(){}

    override fun getFromStack() {}

    override fun getMemberVar(key: String, accessModifier: Member.AccessModifier): Pair<Var<*>?, Boolean> {
        return UnknownVar(key) to true
    }

    override fun getMemberFunction(
        key: String,
        readOnlyParams: List<MCFPPType>,
        normalParams: List<MCFPPType>,
        accessModifier: Member.AccessModifier
    ): Pair<Function, Boolean> {
        return UnknownFunction("unknown") to true
    }

    override fun toNBTVar(): NBTBasedData<*> {
        return NBTBasedDataConcrete(StringTag("unknown"),"unknown")
    }


}