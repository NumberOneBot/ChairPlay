package io.github.numberonebot.chairplay.windows

import com.sun.jna.platform.win32.COM.COMLateBindingObject
import com.sun.jna.platform.win32.COM.EnumVariant
import com.sun.jna.platform.win32.COM.IDispatch
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.OaIdl
import com.sun.jna.platform.win32.OleAuto
import com.sun.jna.platform.win32.Variant
import com.sun.jna.ptr.PointerByReference

/**
 * A COM automation object, read by its property and method names: one made from a ProgID or CLSID, or one another
 * object handed out. Only on a thread with COM set up ([onComThread]). [release] gives up a reference this code owns.
 */
internal class Automation : COMLateBindingObject {
    constructor(progId: String) : super(progId, false)
    constructor(clsid: Guid.CLSID) : super(clsid, false)
    constructor(dispatch: IDispatch) : super(dispatch)

    fun int(name: String): Int = getIntProperty(name)

    fun boolean(name: String): Boolean = getBooleanProperty(name)

    /** A string property; null for an empty one (VT_EMPTY or a null BSTR). */
    fun string(name: String): String? = runCatching { getStringProperty(name) }.getOrNull()

    /** An indexed property, as FwPolicy2's FirewallEnabled(profile). */
    fun booleanAt(name: String, index: Int): Boolean {
        val result = Variant.VARIANT.ByReference()
        oleMethod(OleAuto.DISPATCH_PROPERTYGET, result, name, Variant.VARIANT(index))
        return try {
            result.booleanValue()
        } finally {
            OleAuto.INSTANCE.VariantClear(result)
        }
    }

    /** A method that returns a number, as INetwork's GetCategory(). */
    fun callInt(name: String): Int {
        val result = Variant.VARIANT.ByReference()
        oleMethod(OleAuto.DISPATCH_METHOD, result, name)
        return try {
            result.intValue()
        } finally {
            OleAuto.INSTANCE.VariantClear(result)
        }
    }

    /** A method that returns an object, as INetworkListManager's GetNetworks(flags); the caller releases it. */
    fun callObject(name: String, argument: Int): Automation {
        val result = Variant.VARIANT.ByReference()
        oleMethod(OleAuto.DISPATCH_METHOD, result, name, Variant.VARIANT(argument))
        // The reference in the result is handed to the wrapper: the result is not cleared.
        return Automation(result.value as IDispatch)
    }

    /** An object property; the caller releases it. */
    fun child(name: String): Automation = Automation(getAutomationProperty(name))

    /** Calls [block] with each item of this collection (its _NewEnum), and gives the item up after. */
    fun forEach(block: (Automation) -> Unit) {
        val items = enumerator()
        try {
            while (true) {
                val next = items.Next(1).firstOrNull() ?: break
                try {
                    block(Automation(next.value as IDispatch))
                } finally {
                    // The enumerator's reference to the item; the wrapper adds none.
                    OleAuto.INSTANCE.VariantClear(next)
                }
            }
        } finally {
            items.Release()
        }
    }

    private fun enumerator(): EnumVariant {
        val result = Variant.VARIANT.ByReference()
        oleMethod(OleAuto.DISPATCH_METHOD or OleAuto.DISPATCH_PROPERTYGET, result, OaIdl.DISPID_NEWENUM)
        try {
            val pointer = PointerByReference()
            (result.value as Unknown).QueryInterface(Guid.REFIID(EnumVariant.IID), pointer)
            return EnumVariant(pointer.value ?: error("no IEnumVARIANT"))
        } finally {
            OleAuto.INSTANCE.VariantClear(result)
        }
    }
}
