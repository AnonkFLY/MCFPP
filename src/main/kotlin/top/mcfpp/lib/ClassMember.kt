package top.mcfpp.lib

import top.mcfpp.lang.CanSelectMember
import top.mcfpp.lang.ClassPointer

/**
 * 一个类的成员
 */
interface ClassMember {
    enum class AccessModifier {
        PRIVATE, PROTECTED, PUBLIC
    }

    var accessModifier : AccessModifier

    var isStatic: Boolean

    var pointer: CanSelectMember?
    fun Class(): Class?
}