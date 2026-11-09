package io.github.numberonebot.chairplay.platform.windows

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer

/** IUnknown's Release, slot 2 of every COM interface's table. */
internal const val RELEASE = 2

/**
 * Calls the method in [slot] of the COM interface this points to, with the interface first, as COM does; its HRESULT.
 * For interfaces with no automation (IDispatch) to call them by name.
 */
internal fun Pointer.comCall(slot: Int, vararg arguments: Any?): Int =
    Function.getFunction(getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE)).invokeInt(arrayOf(this, *arguments))
