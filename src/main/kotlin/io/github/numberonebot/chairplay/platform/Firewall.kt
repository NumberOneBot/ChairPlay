package io.github.numberonebot.chairplay.platform

import io.github.numberonebot.chairplay.log.AppLog
import io.github.numberonebot.chairplay.ui.FirewallStatus

/** The system firewall as the start and the Get started page see it: what it does to the iPhone, and how a rule is added. */
internal interface Firewall {
    /**
     * Says in [log] what keeps the iPhone out, if anything, and returns the same status [status] does. [port] is the
     * control port; [uiPort], ui.network's port, is one more a rule must open.
     */
    fun check(port: Int, uiPort: Int?, log: AppLog): FirewallStatus

    /** The firewall read again, without the log lines of [check]. */
    fun status(uiPort: Int?): FirewallStatus

    /** The commands that add the rule, one per line, for the page to show. */
    fun hint(port: Int, uiPort: Int?): String

    /** Runs [commands] (as [hint] writes them) with the administrator's consent; true when every one succeeded. */
    fun addRules(commands: String): Boolean
}
