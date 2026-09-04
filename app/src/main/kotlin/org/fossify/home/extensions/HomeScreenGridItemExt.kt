package org.fossify.home.extensions

import org.fossify.home.helpers.ITEM_TYPE_FOLDER
import org.fossify.home.helpers.ITEM_TYPE_ICON
import org.fossify.home.helpers.ITEM_TYPE_SHORTCUT
import org.fossify.home.helpers.ITEM_TYPE_WIDGET
import org.fossify.home.models.HomeScreenGridItem

/** Human-readable item type, for log/ActionTrail messages — never a raw int. */
fun HomeScreenGridItem.readableType(): String = when (type) {
    ITEM_TYPE_ICON -> "icon"
    ITEM_TYPE_FOLDER -> "folder"
    ITEM_TYPE_SHORTCUT -> "shortcut"
    ITEM_TYPE_WIDGET -> "widget"
    else -> "item"
}

/** Human-readable item name for log/ActionTrail messages, falling back to packageName/widgetId when title is blank. */
fun HomeScreenGridItem.readableName(): String {
    return title.ifBlank {
        if (type == ITEM_TYPE_WIDGET) "widgetId=$widgetId" else packageName.ifBlank { "(unknown)" }
    }
}
