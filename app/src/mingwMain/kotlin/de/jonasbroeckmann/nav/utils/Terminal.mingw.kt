@file:Suppress("detekt:MagicNumber")

package de.jonasbroeckmann.nav.utils

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.StandardTerminalInterface
import com.github.ajalt.mordant.terminal.TerminalInterface
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.windows.*
import kotlin.time.TimeMark

actual fun customTerminalInterface(): TerminalInterface? = TerminalInterfaceNativeWindows

// The following is a slightly modified version of the Mordant's internal implementation
// that handles local keyboard layouts better and fixes mouse input.

@OptIn(ExperimentalForeignApi::class)
private object TerminalInterfaceNativeWindows : StandardTerminalInterface() {
    override fun enterRawMode(mouseTracking: MouseTracking): AutoCloseable {
        val originalMode = getStdinConsoleMode()
        // dwMode=0 means ctrl-c processing, echo, and line input modes are disabled. Could add
        // ENABLE_PROCESSED_INPUT or ENABLE_WINDOW_INPUT if we want those events.
        val dwMode = when (mouseTracking) {
            MouseTracking.Off -> 0u
            else -> (ENABLE_MOUSE_INPUT or ENABLE_EXTENDED_FLAGS).toUInt()
        }
        setStdinConsoleMode(dwMode)
        return AutoCloseable { setStdinConsoleMode(originalMode) }
    }

    override fun readInputEvent(timeout: TimeMark, mouseTracking: MouseTracking): InputEvent? {
        val elapsed = timeout.elapsedNow()
        val dwMilliseconds = when {
            elapsed.isInfinite() -> Int.MAX_VALUE
            elapsed.isPositive() -> 0 // positive elapsed is in the past
            else -> -(elapsed.inWholeMilliseconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        return when (val event = readRawEvent(dwMilliseconds)) {
            null -> null
            is EventRecord.Key -> processKeyEvent(event)
            is EventRecord.Mouse -> processMouseEvent(event, mouseTracking)
        }
    }

    private fun readRawEvent(dwMilliseconds: Int): EventRecord? = memScoped {
        val stdinHandle = GetStdHandle(STD_INPUT_HANDLE)
        val waitResult = WaitForSingleObject(
            hHandle = stdinHandle,
            dwMilliseconds = dwMilliseconds.toUInt()
        )
        if (waitResult != 0u) {
            throw ConsoleException("Timeout reading from console input")
        }
        val inputEvents = allocArray<INPUT_RECORD>(1)
        val eventsRead = alloc<UIntVar>()
        ReadConsoleInput!!(stdinHandle, inputEvents, 1u, eventsRead.ptr)
        if (eventsRead.value == 0u) {
            throw ConsoleException("Error reading from console input")
        }
        val inputEvent = inputEvents[0]
        return when (inputEvent.EventType.toInt()) {
            KEY_EVENT -> {
                val keyEvent = inputEvent.Event.KeyEvent
                EventRecord.Key(
                    bKeyDown = keyEvent.bKeyDown != 0,
                    wVirtualKeyCode = keyEvent.wVirtualKeyCode,
                    uChar = keyEvent.uChar.UnicodeChar.toInt().toChar(),
                    dwControlKeyState = keyEvent.dwControlKeyState,
                )
            }
            MOUSE_EVENT -> {
                val mouseEvent = inputEvent.Event.MouseEvent
                EventRecord.Mouse(
                    dwMousePositionX = mouseEvent.dwMousePosition.X,
                    dwMousePositionY = mouseEvent.dwMousePosition.Y,
                    dwButtonState = mouseEvent.dwButtonState,
                    dwControlKeyState = mouseEvent.dwControlKeyState,
                    dwEventFlags = mouseEvent.dwEventFlags,
                )
            }
            else -> null // Ignore other event types like FOCUS_EVENT that we can't opt out of
        }
    }

    private fun processKeyEvent(event: EventRecord.Key): InputEvent? {
        if (!event.bKeyDown) return null // ignore key up events
        val virtualName = WindowsVirtualKeyCodeToKeyEvent.getName(event.wVirtualKeyCode)
        val transformedKey = transformedKey(event)
            ?.takeUnless { it.isEmpty() || it.first().isISOControl() }
            ?: virtualName
        val key = when {
            transformedKey != null -> transformedKey
            event.uChar in Char.MIN_SURROGATE..Char.MAX_SURROGATE -> {
                // We got a surrogate pair, so we need to read the next char to get the full
                // codepoint. Skip any key up events that might be in the queue.
                var nextEvent: EventRecord?
                do {
                    nextEvent = readRawEvent(0)
                } while (nextEvent != null && (nextEvent !is EventRecord.Key || !nextEvent.bKeyDown))
                if (nextEvent !is EventRecord.Key) {
                    event.uChar.toString()
                } else {
                    charArrayOf(event.uChar, nextEvent.uChar).concatToString()
                }
            }
            event.uChar.code != 0 -> event.uChar.toString()
            else -> "Unidentified"
        }
        return KeyboardEvent(
            key = key,
            ctrl = event.ctrl,
            alt = event.alt,
            shift = event.shift,
        )
    }

    private fun transformedKey(event: EventRecord.Key) = memScoped {
        val keyboardState = allocArray<BYTEVar>(256)
        if (event.ctrl) keyboardState[VK_CONTROL] = 0x80u
        if (event.alt) keyboardState[VK_MENU] = 0x80u
        if (event.shift) keyboardState[VK_SHIFT] = 0x80u
        val bufferSize = 256
        val buffer = allocArray<WCHARVar>(bufferSize)
        val result = ToUnicode(
            wVirtKey = event.wVirtualKeyCode.toUInt(),
            wScanCode = event.uChar.code.toUInt(),
            lpKeyState = keyboardState,
            pwszBuff = buffer,
            cchBuff = bufferSize,
            wFlags = 0u
        )
        when {
            result < 0 -> null
            result == 0 -> null
            else -> {
                buffer[result] = 0u
                buffer.toKString()
            }
        }
    }

    private fun processMouseEvent(
        event: EventRecord.Mouse,
        tracking: MouseTracking,
    ): InputEvent? {
        val eventFlags = event.dwEventFlags
        val buttons = event.dwButtonState.toInt()

        // If the high word of the dwButtonState member contains a positive value, the wheel
        // was rotated forward, away from the user.
        // If the high word of the dwButtonState member contains a positive value, the wheel
        // was rotated to the right.
        return when (tracking) {
            MouseTracking.Off -> null
            MouseTracking.Normal if eventFlags == MOUSE_MOVED.toUInt() -> null
            MouseTracking.Button if eventFlags == MOUSE_MOVED.toUInt() && buttons == 0 -> null
            else -> MouseEvent(
                x = event.dwMousePositionX.toInt(),
                y = event.dwMousePositionY.toInt(),
                left = buttons and FROM_LEFT_1ST_BUTTON_PRESSED != 0,
                right = buttons and RIGHTMOST_BUTTON_PRESSED != 0,
                middle = buttons and FROM_LEFT_2ND_BUTTON_PRESSED != 0,
                mouse4 = buttons and FROM_LEFT_3RD_BUTTON_PRESSED != 0,
                mouse5 = buttons and FROM_LEFT_4TH_BUTTON_PRESSED != 0,
                // If the high word of the dwButtonState member contains a positive value, the wheel
                // was rotated forward, away from the user.
                wheelUp = eventFlags and MOUSE_WHEELED.toUInt() != 0u && buttons shr 16 > 0,
                wheelDown = eventFlags and MOUSE_WHEELED.toUInt() != 0u && buttons shr 16 <= 0,
                // If the high word of the dwButtonState member contains a positive value, the wheel
                // was rotated to the right.
                wheelLeft = eventFlags and MOUSE_HWHEELED.toUInt() != 0u && buttons shr 16 <= 0,
                wheelRight = eventFlags and MOUSE_HWHEELED.toUInt() != 0u && buttons shr 16 > 0,
                ctrl = event.dwControlKeyState and (RIGHT_CTRL_PRESSED or LEFT_CTRL_PRESSED).toUInt() != 0u,
                alt = event.dwControlKeyState and (RIGHT_ALT_PRESSED or LEFT_ALT_PRESSED).toUInt() != 0u,
                shift = event.dwControlKeyState and SHIFT_PRESSED.toUInt() != 0u,
            )
        }
    }

    // https://docs.microsoft.com/en-us/windows/console/getconsolemode
    override fun stdoutInteractive(): Boolean = memScoped {
        GetConsoleMode(GetStdHandle(STD_OUTPUT_HANDLE), alloc<UIntVar>().ptr) != 0
    }

    override fun stdinInteractive(): Boolean = memScoped {
        GetConsoleMode(GetStdHandle(STD_INPUT_HANDLE), alloc<UIntVar>().ptr) != 0
    }

    // https://docs.microsoft.com/en-us/windows/console/getconsolescreenbufferinfo
    override fun getTerminalSize(): Size? = memScoped {
        val csbi = alloc<CONSOLE_SCREEN_BUFFER_INFO>()
        val stdoutHandle = GetStdHandle(STD_OUTPUT_HANDLE)
        if (stdoutHandle == INVALID_HANDLE_VALUE) {
            return@memScoped null
        }

        if (GetConsoleScreenBufferInfo(stdoutHandle, csbi.ptr) == 0) {
            return@memScoped null
        }
        csbi.srWindow.run { Size(width = Right - Left + 1, height = Bottom - Top + 1) }
    }

    private fun getStdinConsoleMode(): UInt {
        return getConsoleMode(GetStdHandle(STD_INPUT_HANDLE)) ?: throw ConsoleException("Error getting console mode")
    }

    private fun setStdinConsoleMode(dwMode: UInt) {
        if (SetConsoleMode(GetStdHandle(STD_INPUT_HANDLE), dwMode) == 0) {
            throw ConsoleException("Error setting console mode")
        }
    }

    // https://docs.microsoft.com/en-us/windows/console/getconsolemode
    private fun getConsoleMode(handle: HANDLE?): UInt? = memScoped {
        if (handle == null || handle == INVALID_HANDLE_VALUE) return null
        val lpMode = alloc<UIntVar>()
        // "If the function succeeds, the return value is nonzero."
        if (GetConsoleMode(handle, lpMode.ptr) == 0) return null
        return lpMode.value
    }

    private sealed interface EventRecord {
        data class Key(
            val bKeyDown: Boolean,
            val wVirtualKeyCode: UShort,
            val uChar: Char,
            val dwControlKeyState: UInt,
        ) : EventRecord {
            val ctrl: Boolean get() = dwControlKeyState and (RIGHT_CTRL_PRESSED or LEFT_CTRL_PRESSED).toUInt() != 0u
            val alt: Boolean get() = dwControlKeyState and (RIGHT_ALT_PRESSED or LEFT_ALT_PRESSED).toUInt() != 0u
            val shift: Boolean get() = dwControlKeyState and SHIFT_PRESSED.toUInt() != 0u
        }

        data class Mouse(
            val dwMousePositionX: Short,
            val dwMousePositionY: Short,
            val dwButtonState: UInt,
            val dwControlKeyState: UInt,
            val dwEventFlags: UInt,
        ) : EventRecord
    }

    private class ConsoleException(message: String) : RuntimeException(message)
}

private object WindowsVirtualKeyCodeToKeyEvent {
    private val map: Map<UShort, String> = mapOf(
        VK_MENU.toUShort() to "Alt",
        VK_CAPITAL.toUShort() to "CapsLock",
        VK_CONTROL.toUShort() to "Control",
        VK_LWIN.toUShort() to "Meta",
        VK_NUMLOCK.toUShort() to "NumLock",
        VK_SCROLL.toUShort() to "ScrollLock",
        VK_SHIFT.toUShort() to "Shift",
        VK_RETURN.toUShort() to "Enter",
        VK_TAB.toUShort() to "Tab",
        VK_SPACE.toUShort() to " ",
        VK_DOWN.toUShort() to "ArrowDown",
        VK_LEFT.toUShort() to "ArrowLeft",
        VK_RIGHT.toUShort() to "ArrowRight",
        VK_UP.toUShort() to "ArrowUp",
        VK_END.toUShort() to "End",
        VK_HOME.toUShort() to "Home",
        VK_NEXT.toUShort() to "PageDown",
        VK_PRIOR.toUShort() to "PageUp",
        VK_BACK.toUShort() to "Backspace",
        VK_CLEAR.toUShort() to "Clear",
        VK_CRSEL.toUShort() to "CrSel",
        VK_DELETE.toUShort() to "Delete",
        VK_EREOF.toUShort() to "EraseEof",
        VK_EXSEL.toUShort() to "ExSel",
        VK_INSERT.toUShort() to "Insert",
        VK_ACCEPT.toUShort() to "Accept",
        VK_OEM_ATTN.toUShort() to "Attn",
        VK_APPS.toUShort() to "ContextMenu",
        VK_ESCAPE.toUShort() to "Escape",
        VK_EXECUTE.toUShort() to "Execute",
        VK_OEM_FINISH.toUShort() to "Finish",
        VK_HELP.toUShort() to "Help",
        VK_PAUSE.toUShort() to "Pause",
        VK_PLAY.toUShort() to "Play",
        VK_SELECT.toUShort() to "Select",
        VK_SNAPSHOT.toUShort() to "PrintScreen",
        VK_SLEEP.toUShort() to "Standby",
        VK_OEM_ATTN.toUShort() to "Alphanumeric",
        VK_CONVERT.toUShort() to "Convert",
        VK_FINAL.toUShort() to "FinalMode",
        VK_MODECHANGE.toUShort() to "ModeChange",
        VK_NONCONVERT.toUShort() to "NonConvert",
        VK_PROCESSKEY.toUShort() to "Process",
        VK_HANGUL.toUShort() to "HangulMode",
        VK_HANJA.toUShort() to "HanjaMode",
        VK_JUNJA.toUShort() to "JunjaMode",
        VK_OEM_AUTO.toUShort() to "Hankaku",
        VK_OEM_COPY.toUShort() to "Hiragana",
        VK_KANA.toUShort() to "KanaMode",
        VK_OEM_FINISH.toUShort() to "Katakana",
        VK_OEM_BACKTAB.toUShort() to "Romaji",
        VK_OEM_ENLW.toUShort() to "Zenkaku",
        VK_F1.toUShort() to "F1",
        VK_F2.toUShort() to "F2",
        VK_F3.toUShort() to "F3",
        VK_F4.toUShort() to "F4",
        VK_F5.toUShort() to "F5",
        VK_F6.toUShort() to "F6",
        VK_F7.toUShort() to "F7",
        VK_F8.toUShort() to "F8",
        VK_F9.toUShort() to "F9",
        VK_F10.toUShort() to "F10",
        VK_F11.toUShort() to "F11",
        VK_F12.toUShort() to "F12",
        VK_F13.toUShort() to "F13",
        VK_F14.toUShort() to "F14",
        VK_F15.toUShort() to "F15",
        VK_F16.toUShort() to "F16",
        VK_F17.toUShort() to "F17",
        VK_F18.toUShort() to "F18",
        VK_F19.toUShort() to "F19",
        VK_F20.toUShort() to "F20",
        VK_F21.toUShort() to "F21",
        VK_F22.toUShort() to "F22",
        VK_F23.toUShort() to "F23",
        VK_F24.toUShort() to "F24",
        VK_MEDIA_PLAY_PAUSE.toUShort() to "MediaPlayPause",
        VK_MEDIA_STOP.toUShort() to "MediaStop",
        VK_MEDIA_NEXT_TRACK.toUShort() to "MediaTrackNext",
        VK_MEDIA_PREV_TRACK.toUShort() to "MediaTrackPrevious",
        VK_VOLUME_DOWN.toUShort() to "AudioVolumeDown",
        VK_VOLUME_MUTE.toUShort() to "AudioVolumeMute",
        VK_VOLUME_UP.toUShort() to "AudioVolumeUp",
        VK_ZOOM.toUShort() to "ZoomToggle",
        VK_LAUNCH_MAIL.toUShort() to "LaunchMail",
        VK_LAUNCH_MEDIA_SELECT.toUShort() to "LaunchMediaPlayer",
        VK_LAUNCH_APP1.toUShort() to "LaunchApplication1",
        VK_LAUNCH_APP2.toUShort() to "LaunchApplication2",
        VK_BROWSER_BACK.toUShort() to "BrowserBack",
        VK_BROWSER_FAVORITES.toUShort() to "BrowserFavorites",
        VK_BROWSER_FORWARD.toUShort() to "BrowserForward",
        VK_BROWSER_HOME.toUShort() to "BrowserHome",
        VK_BROWSER_REFRESH.toUShort() to "BrowserRefresh",
        VK_BROWSER_SEARCH.toUShort() to "BrowserSearch",
        VK_BROWSER_STOP.toUShort() to "BrowserStop",
        VK_DECIMAL.toUShort() to "Decimal",
        VK_MULTIPLY.toUShort() to "Multiply",
        VK_ADD.toUShort() to "Add",
        VK_DIVIDE.toUShort() to "Divide",
        VK_SUBTRACT.toUShort() to "Subtract",
        VK_SEPARATOR.toUShort() to "Separator",
        0x30.toUShort() to "0",
        0x31.toUShort() to "1",
        0x32.toUShort() to "2",
        0x33.toUShort() to "3",
        0x34.toUShort() to "4",
        0x35.toUShort() to "5",
        0x36.toUShort() to "6",
        0x37.toUShort() to "7",
        0x38.toUShort() to "8",
        0x39.toUShort() to "9",
        VK_NUMPAD0.toUShort() to "0",
        VK_NUMPAD1.toUShort() to "1",
        VK_NUMPAD2.toUShort() to "2",
        VK_NUMPAD3.toUShort() to "3",
        VK_NUMPAD4.toUShort() to "4",
        VK_NUMPAD5.toUShort() to "5",
        VK_NUMPAD6.toUShort() to "6",
        VK_NUMPAD7.toUShort() to "7",
        VK_NUMPAD8.toUShort() to "8",
        VK_NUMPAD9.toUShort() to "9",
        0x41.toUShort() to "a",
        0x42.toUShort() to "b",
        0x43.toUShort() to "c",
        0x44.toUShort() to "d",
        0x45.toUShort() to "e",
        0x46.toUShort() to "f",
        0x47.toUShort() to "g",
        0x48.toUShort() to "h",
        0x49.toUShort() to "i",
        0x4A.toUShort() to "j",
        0x4B.toUShort() to "k",
        0x4C.toUShort() to "l",
        0x4D.toUShort() to "m",
        0x4E.toUShort() to "n",
        0x4F.toUShort() to "o",
        0x50.toUShort() to "p",
        0x51.toUShort() to "q",
        0x52.toUShort() to "r",
        0x53.toUShort() to "s",
        0x54.toUShort() to "t",
        0x55.toUShort() to "u",
        0x56.toUShort() to "v",
        0x57.toUShort() to "w",
        0x58.toUShort() to "x",
        0x59.toUShort() to "y",
        0x5A.toUShort() to "z",
    )

    fun getName(keyCode: UShort): String? = map[keyCode]
}
